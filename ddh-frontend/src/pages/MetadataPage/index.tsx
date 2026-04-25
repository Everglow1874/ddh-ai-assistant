import { useEffect, useState, useCallback } from 'react'
import { useParams } from 'react-router-dom'
import { Table, Button, Space, Input, message, Upload, Modal, Popconfirm, Tabs, Tag, Tooltip, Typography } from 'antd'
import { UploadOutlined, CodeOutlined, KeyOutlined, CopyOutlined, DatabaseOutlined } from '@ant-design/icons'
import { getTables, getTableDetail, importExcel, importDdl, deleteTable, type TableMetadata, type ColumnMetadata, type TableDetail } from '../../api/metadata'

const { Text } = Typography

/** 根据字段列表生成建表 DDL */
function generateDdl(table: TableMetadata, cols: ColumnMetadata[]): string {
  const lines: string[] = []
  lines.push(`-- ${table.tableComment || table.tableName}`)
  lines.push(`CREATE TABLE IF NOT EXISTS ${table.schemaName ? table.schemaName + '.' : ''}${table.tableName} (`)
  const pkCols = cols.filter(c => c.isPrimaryKey === 1).map(c => c.columnName)
  const colDefs = cols.map((col, idx) => {
    const nullable = col.isNullable === 0 ? ' NOT NULL' : ''
    const def = col.defaultValue ? ` DEFAULT ${col.defaultValue}` : ''
    const comment = col.columnComment ? ` -- ${col.columnComment}` : ''
    const comma = idx < cols.length - 1 || pkCols.length > 0 ? ',' : ''
    return `    ${col.columnName} ${col.columnType}${nullable}${def}${comma}${comment}`
  })
  lines.push(...colDefs)
  if (pkCols.length > 0) {
    lines.push(`    PRIMARY KEY (${pkCols.join(', ')})`)
  }
  lines.push(');')
  return lines.join('\n')
}

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
  const [detailTab, setDetailTab] = useState('columns')

  const [importModalOpen, setImportModalOpen] = useState(false)
  const [importLoading, setImportLoading] = useState(false)
  const [importTab, setImportTab] = useState<'excel' | 'ddl'>('excel')
  const [ddlText, setDdlText] = useState('')

  const fetchTables = useCallback(async (page = 1, kw = keyword) => {
    setLoading(true)
    try {
      const result = await getTables(pid, kw || undefined, page, 20)
      setTables(result.records)
      setTotal(result.total)
      setCurrent(result.current)
    } catch { /* handled */ } finally {
      setLoading(false)
    }
  }, [pid, keyword])

  useEffect(() => { fetchTables() }, [pid, fetchTables])

  const handleTableClick = async (table: TableMetadata) => {
    try {
      const detail: TableDetail = await getTableDetail(pid, table.id)
      setSelectedTable(detail.table)
      setColumns(detail.columns)
      setDetailTab('columns')
    } catch {
      message.error('获取表详情失败')
    }
  }

  const handleImport = async (file: File) => {
    setImportLoading(true)
    try {
      const result = await importExcel(pid, file)
      let msg = `导入成功：${result.importedTables} 个表，${result.importedColumns} 个字段`
      if (result.errors?.length) msg += `，${result.errors.length} 个警告`
      message.success(msg)
      setImportModalOpen(false)
      fetchTables()
    } catch {
      message.error('导入失败')
    } finally {
      setImportLoading(false)
    }
    return false
  }

  const handleDelete = async (tableId: number) => {
    try {
      await deleteTable(pid, tableId)
      message.success('删除成功')
      if (selectedTable?.id === tableId) { setSelectedTable(null); setColumns([]) }
      fetchTables()
    } catch {
      message.error('删除失败')
    }
  }

  const handleImportDdl = async () => {
    if (!ddlText.trim()) { message.warning('请输入 DDL 语句'); return }
    setImportLoading(true)
    try {
      const result = await importDdl(pid, ddlText.trim())
      let msg = `导入成功：${result.importedTables} 个表，${result.importedColumns} 个字段`
      if (result.errors?.length) msg += `，${result.errors.length} 个警告`
      message.success(msg)
      setImportModalOpen(false)
      setDdlText('')
      fetchTables()
    } catch {
      message.error('DDL 导入失败，请检查语法')
    } finally {
      setImportLoading(false)
    }
  }
  const handleCopyDdl = () => {
    if (!selectedTable) return
    const ddl = generateDdl(selectedTable, columns)
    navigator.clipboard.writeText(ddl).then(() => message.success('DDL 已复制到剪贴板'))
  }

  const tableColumns = [
    {
      title: '表名', dataIndex: 'tableName', key: 'tableName',
      render: (name: string, record: TableMetadata) => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>{name}</div>
          {record.tableComment && <div style={{ fontSize: 11, color: '#999' }}>{record.tableComment}</div>}
        </div>
      )
    },
    { title: 'Schema', dataIndex: 'schemaName', key: 'schemaName', width: 100 },
    {
      title: '操作', key: 'action', width: 100,
      render: (_: unknown, record: TableMetadata) => (
        <Space>
          <Button type="link" size="small" onClick={() => handleTableClick(record)}>查看</Button>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      )
    },
  ]

  const columnColumns = [
    {
      title: '字段名', dataIndex: 'columnName', key: 'columnName',
      render: (name: string, record: ColumnMetadata) => (
        <Space size={4}>
          {record.isPrimaryKey === 1 && (
            <Tooltip title="主键"><KeyOutlined style={{ color: '#faad14', fontSize: 11 }} /></Tooltip>
          )}
          <Text style={{ fontWeight: record.isPrimaryKey === 1 ? 600 : 400, fontSize: 13 }}>{name}</Text>
        </Space>
      )
    },
    {
      title: '类型', dataIndex: 'columnType', key: 'columnType', width: 160,
      render: (t: string) => <Text code style={{ fontSize: 12 }}>{t}</Text>
    },
    { title: '注释', dataIndex: 'columnComment', key: 'columnComment' },
    {
      title: '可空', dataIndex: 'isNullable', key: 'isNullable', width: 60,
      render: (v: number) => v === 1 ? <Tag color="default" style={{ fontSize: 10 }}>YES</Tag> : <Tag color="red" style={{ fontSize: 10 }}>NO</Tag>
    },
    { title: '默认值', dataIndex: 'defaultValue', key: 'defaultValue', width: 100 },
  ]

  const ddl = selectedTable ? generateDdl(selectedTable, columns) : ''

  return (
    <div style={{ display: 'flex', gap: 16, height: '100%' }}>
      {/* 左侧：表列表 */}
      <div style={{ width: 420, flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
        <div style={{ marginBottom: 12 }}>
          <Space>
            <Input.Search
              placeholder="搜索表名或注释"
              allowClear
              style={{ width: 260 }}
              onSearch={(val) => { setKeyword(val); fetchTables(1, val) }}
            />
            <Button icon={<UploadOutlined />} onClick={() => setImportModalOpen(true)}>导入 Excel</Button>
          </Space>
        </div>
        <Table
          dataSource={tables}
          columns={tableColumns}
          rowKey="id"
          loading={loading}
          size="small"
          pagination={{
            current, total, pageSize: 20,
            onChange: fetchTables,
            showTotal: (t) => `共 ${t} 张表`,
            size: 'small',
          }}
          onRow={(record) => ({
            onClick: () => handleTableClick(record),
            style: {
              cursor: 'pointer',
              background: selectedTable?.id === record.id ? '#e6f7ff' : undefined,
            }
          })}
        />
      </div>

      {/* 右侧：字段详情 + DDL */}
      <div style={{ flex: 1, background: '#fff', borderRadius: 6, padding: '0 16px', overflow: 'auto' }}>
        {selectedTable ? (
          <>
            <div style={{ padding: '14px 0 10px', borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <Text strong style={{ fontSize: 16 }}>{selectedTable.tableName}</Text>
                {selectedTable.tableComment && (
                  <Text type="secondary" style={{ marginLeft: 8 }}>{selectedTable.tableComment}</Text>
                )}
                {selectedTable.schemaName && (
                  <Text type="secondary" style={{ marginLeft: 8, fontSize: 12 }}>Schema: {selectedTable.schemaName}</Text>
                )}
                <div style={{ marginTop: 4 }}>
                  <Tag color="blue">{columns.length} 个字段</Tag>
                  {columns.filter(c => c.isPrimaryKey === 1).length > 0 && (
                    <Tag color="gold">{columns.filter(c => c.isPrimaryKey === 1).length} 个主键</Tag>
                  )}
                </div>
              </div>
              <Button
                size="small"
                icon={<CopyOutlined />}
                onClick={handleCopyDdl}
              >
                复制 DDL
              </Button>
            </div>

            <Tabs
              activeKey={detailTab}
              onChange={setDetailTab}
              size="small"
              style={{ marginTop: 4 }}
              items={[
                {
                  key: 'columns',
                  label: `字段列表 (${columns.length})`,
                  children: (
                    <Table
                      dataSource={columns}
                      columns={columnColumns}
                      rowKey="id"
                      size="small"
                      pagination={false}
                    />
                  ),
                },
                {
                  key: 'ddl',
                  label: <><CodeOutlined /> 建表 DDL</>,
                  children: (
                    <div>
                      <div style={{ marginBottom: 8, display: 'flex', justifyContent: 'flex-end' }}>
                        <Button size="small" icon={<CopyOutlined />} onClick={handleCopyDdl}>
                          复制 DDL
                        </Button>
                      </div>
                      <pre style={{
                        background: '#f6f8fa',
                        padding: 16,
                        borderRadius: 6,
                        fontSize: 13,
                        lineHeight: 1.6,
                        overflow: 'auto',
                        border: '1px solid #e8e8e8',
                        whiteSpace: 'pre',
                        fontFamily: '"Fira Code", "Consolas", monospace',
                      }}>
                        {ddl}
                      </pre>
                    </div>
                  ),
                },
              ]}
            />
          </>
        ) : (
          <div style={{ color: '#999', textAlign: 'center', marginTop: 120 }}>
            <DatabaseOutlined style={{ fontSize: 48, color: '#e0e0e0', display: 'block', marginBottom: 12 }} />
            点击左侧表查看字段详情
          </div>
        )}
      </div>

      {/* 导入 Modal */}
      <Modal
        title="导入元数据"
        open={importModalOpen}
        onCancel={() => { setImportModalOpen(false); setDdlText('') }}
        footer={importTab === 'ddl' ? (
          <Space>
            <Button onClick={() => { setImportModalOpen(false); setDdlText('') }}>取消</Button>
            <Button type="primary" loading={importLoading} onClick={handleImportDdl}>
              导入 DDL
            </Button>
          </Space>
        ) : null}
        width={600}
      >
        <Tabs
          activeKey={importTab}
          onChange={(k) => setImportTab(k as 'excel' | 'ddl')}
          items={[
            {
              key: 'excel',
              label: '📊 Excel 导入',
              children: (
                <div style={{ paddingTop: 8 }}>
                  <p style={{ color: '#666', marginBottom: 12 }}>Excel 文件格式要求：</p>
                  <ul style={{ color: '#666', fontSize: 13, paddingLeft: 20, marginBottom: 16 }}>
                    <li>Sheet1（表信息）：表名、表注释、Schema</li>
                    <li>Sheet2（字段信息）：表名、字段名、类型、注释、是否主键、是否可空</li>
                  </ul>
                  <Upload.Dragger
                    accept=".xlsx,.xls"
                    beforeUpload={handleImport}
                    showUploadList={false}
                    disabled={importLoading}
                  >
                    <p className="ant-upload-drag-icon">
                      <UploadOutlined style={{ fontSize: 40, color: '#1677ff' }} />
                    </p>
                    <p className="ant-upload-text">点击或拖拽 Excel 文件到此处</p>
                    <p className="ant-upload-hint">支持 .xlsx / .xls 格式</p>
                  </Upload.Dragger>
                  {importLoading && (
                    <div style={{ marginTop: 12, textAlign: 'center', color: '#1677ff' }}>
                      正在导入，请稍候…
                    </div>
                  )}
                </div>
              ),
            },
            {
              key: 'ddl',
              label: '📝 DDL 导入',
              children: (
                <div style={{ paddingTop: 8 }}>
                  <p style={{ color: '#666', marginBottom: 8, fontSize: 13 }}>
                    粘贴 CREATE TABLE 语句（支持多个表，GaussDB / MySQL / PostgreSQL 语法）：
                  </p>
                  <Input.TextArea
                    value={ddlText}
                    onChange={e => setDdlText(e.target.value)}
                    placeholder={`-- 示例：
CREATE TABLE orders (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  user_id BIGINT NOT NULL COMMENT '用户ID',
  amount NUMERIC(10,2) NOT NULL COMMENT '订单金额',
  status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态',
  created_at DATETIME NOT NULL COMMENT '创建时间',
  PRIMARY KEY (id)
) COMMENT='订单表';`}
                    rows={12}
                    style={{ fontFamily: 'monospace', fontSize: 12 }}
                  />
                  <Text type="secondary" style={{ fontSize: 12, marginTop: 6, display: 'block' }}>
                    支持 COMMENT、NOT NULL、DEFAULT、PRIMARY KEY 等常见语法
                  </Text>
                </div>
              ),
            },
          ]}
        />
      </Modal>
    </div>
  )
}

export default MetadataPage
