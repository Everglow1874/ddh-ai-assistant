import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { Table, Button, Space, Input, message, Upload, Modal, Popconfirm } from 'antd'
import { UploadOutlined, SearchOutlined } from '@ant-design/icons'
import { getTables, getTableDetail, importExcel, deleteTable, type TableMetadata, type ColumnMetadata, type TableDetail } from '../../api/metadata'

function MetadataPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const pid = Number(projectId)

  const [tables, setTables] = useState<TableMetadata[]>([])
  const [loading, setLoading] = useState(false)
  const [total, setTotal] = useState(0)
  const [current, setCurrent] = useState(1)
  const [keyword, setKeyword] = useState('')

  const [selectedTable, setSelectedTable] = useState<TableMetadata | null>(null)
  const [columns, setColumns] = useState<ColumnMetadata[]>([])

  const [importModalOpen, setImportModalOpen] = useState(false)
  const [importLoading, setImportLoading] = useState(false)

  const fetchTables = async (page = 1) => {
    setLoading(true)
    try {
      const result = await getTables(pid, keyword || undefined, page, 20)
      setTables(result.records)
      setTotal(result.total)
      setCurrent(result.current)
    } catch (e) {
      // error handled in interceptor
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchTables()
  }, [pid])

  const handleTableClick = async (table: TableMetadata) => {
    try {
      const detail: TableDetail = await getTableDetail(pid, table.id)
      setSelectedTable(detail.table)
      setColumns(detail.columns)
    } catch (e) {
      message.error('获取表详情失败')
    }
  }

  const handleImport = async (file: File) => {
    setImportLoading(true)
    try {
      const result = await importExcel(pid, file)
      let msg = `导入成功：${result.importedTables} 个表，${result.importedColumns} 个字段`
      if (result.errors && result.errors.length > 0) {
        msg += `，${result.errors.length} 个警告`
      }
      message.success(msg)
      setImportModalOpen(false)
      fetchTables()
    } catch (e) {
      message.error('导入失败')
    } finally {
      setImportLoading(false)
    }
    return false //阻止自动上传
  }

  const handleDelete = async (tableId: number) => {
    try {
      await deleteTable(pid, tableId)
      message.success('删除成功')
      if (selectedTable?.id === tableId) {
        setSelectedTable(null)
        setColumns([])
      }
      fetchTables()
    } catch (e) {
      message.error('删除失败')
    }
  }

  const handlePageChange = (page: number) => {
    fetchTables(page)
  }

  const columns1 = [
    { title: '表名', dataIndex: 'tableName', key: 'tableName' },
    { title: '注释', dataIndex: 'tableComment', key: 'tableComment' },
    { title: 'Schema', dataIndex: 'schemaName', key: 'schemaName' },
    { title: '操作', key: 'action', width: 120,
      render: (_: any, record: TableMetadata) => (
        <Space>
          <Button type="link" size="small" onClick={() => handleTableClick(record)}>查看</Button>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      )
    },
  ]

  const columns2 = [
    { title: '序号', dataIndex: 'sortOrder', key: 'sortOrder', width: 60 },
    { title: '字段名', dataIndex: 'columnName', key: 'columnName' },
    { title: '类型', dataIndex: 'columnType', key: 'columnType', width: 150 },
    { title: '注释', dataIndex: 'columnComment', key: 'columnComment' },
    { title: '主键', dataIndex: 'isPrimaryKey', key: 'isPrimaryKey', width: 60,
      render: (val: number) => val === 1 ? '是' : ''
    },
    { title: '可空', dataIndex: 'isNullable', key: 'isNullable', width: 60,
      render: (val: number) => val === 1 ? '是' : '否'
    },
  ]

  return (
    <div style={{ display: 'flex', gap: 16, height: '100%' }}>
      {/* 左侧：表列表 */}
      <div style={{ width: 500, flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
        <div style={{ marginBottom: 16 }}>
          <Space>
            <Input.Search
              placeholder="搜索表名或注释"
              allowClear
              enterButton={<SearchOutlined />}
              style={{ width: 280 }}
              onSearch={(val) => { setKeyword(val); fetchTables(1) }}
            />
            <Button icon={<UploadOutlined />} onClick={() => setImportModalOpen(true)}>导入Excel</Button>
          </Space>
        </div>
        <Table
          dataSource={tables}
          columns={columns1}
          rowKey="id"
          loading={loading}
          size="small"
          pagination={{ current, total, pageSize: 20, onChange: handlePageChange, showTotal: (t) => `共 ${t} 条` }}
          onRow={(record) => ({
            onClick: () => handleTableClick(record),
            style: { cursor: 'pointer', background: selectedTable?.id === record.id ? '#e6f7ff' : undefined }
          })}
        />
      </div>

      {/* 右侧：字段详情 */}
      <div style={{ flex: 1, background: '#fff', borderRadius: 6, padding: 16 }}>
        {selectedTable ? (
          <>
            <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <h3 style={{ margin: 0 }}>{selectedTable.tableName}</h3>
                <span style={{ color: '#666' }}>{selectedTable.tableComment}</span>
                {selectedTable.schemaName && <span style={{ color: '#999', marginLeft: 8 }}>Schema: {selectedTable.schemaName}</span>}
              </div>
            </div>
            <Table
              dataSource={columns}
              columns={columns2}
              rowKey="id"
              size="small"
              pagination={false}
            />
          </>
        ) : (
          <div style={{ color: '#999', textAlign: 'center', marginTop: 100 }}>
            点击左侧表查看字段详情
          </div>
        )}
      </div>

      {/* 导入 Modal */}
      <Modal
        title="导入元数据"
        open={importModalOpen}
        onCancel={() => setImportModalOpen(false)}
        footer={null}
      >
        <div style={{ padding: 20 }}>
          <p>请上传 Excel 文件，支持以下格式：</p>
          <ul>
            <li>Sheet1: 表信息（表名、表注释、Schema）</li>
            <li>Sheet2: 字段信息（表名、字段名、类型、注释、主键、可空）</li>
          </ul>
          <Upload.Dragger
            accept=".xlsx,.xls"
            beforeUpload={handleImport}
            showUploadList={false}
            disabled={importLoading}
          >
            <p className="ant-upload-drag-icon">
              <UploadOutlined style={{ fontSize: 48, color: '#999' }} />
            </p>
            <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p className="ant-upload-hint">支持 xlsx/xls 格式</p>
          </Upload.Dragger>
          {importLoading && <div style={{ marginTop: 16, textAlign: 'center' }}>导入中...</div>}
        </div>
      </Modal>
    </div>
  )
}

export default MetadataPage