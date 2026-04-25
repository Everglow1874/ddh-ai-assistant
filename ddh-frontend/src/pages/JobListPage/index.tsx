import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { Table, Button, Space, Input, Modal, message, Tag, Card, Typography, Empty } from 'antd'
import { DeleteOutlined, ExportOutlined, PlusOutlined } from '@ant-design/icons'
import { getJobs, deleteJob, type EtlJob } from '../../api/job'

const { Title, Text } = Typography
const { Search } = Input

function JobListPage() {
  const { projectId } = useParams<{ projectId: string }>()
  const pid = projectId ? Number(projectId) : 0

  const [jobs, setJobs] = useState<EtlJob[]>([])
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')

  const loadJobs = async () => {
    if (!pid) return
    setLoading(true)
    try {
      const data = await getJobs(pid, keyword || undefined)
      setJobs(data)
    } catch (e) {
      // 错误已由请求拦截器处理
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (!pid) return
    loadJobs()
  }, [pid, keyword])

  const handleDelete = (id: number) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除这个作业吗？此操作不可恢复。',
      okText: '删除',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await deleteJob(id)
          message.success('删除成功')
          loadJobs()
        } catch (e) {
          // 错误已由请求拦截器处理
        }
      },
    })
  }

  const handleExport = async (job: EtlJob) => {
    try {
      const response = await fetch(`/api/jobs/${job.id}/export`, {
        credentials: 'include',
      })
      const blob = await response.blob()
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `${job.jobName}.zip`
      a.click()
      window.URL.revokeObjectURL(url)
      message.success('导出成功')
    } catch (e) {
      message.error('导出失败')
    }
  }

  const columns = [
    {
      title: '作业名称',
      dataIndex: 'jobName',
      key: 'jobName',
      render: (name: string, record: EtlJob) => (
        <Link to={`/projects/${pid}/jobs/${record.id}`}>{name}</Link>
      ),
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const color = status === 'COMPLETED' ? 'green' : status === 'DRAFT' ? 'orange' : 'default'
        return <Tag color={color}>{status}</Tag>
      },
    },
    {
      title: '步骤数',
      dataIndex: 'stepCount',
      key: 'stepCount',
      width: 80,
      render: (count: number) => count != null ? `${count} 步` : '-',
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: (v: string) => v ? new Date(v).toLocaleString() : '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: EtlJob) => (
        <Space>
          <Button
            type="text"
            size="small"
            icon={<ExportOutlined />}
            onClick={() => handleExport(record)}
          >
            导出
          </Button>
          <Button
            type="text"
            size="small"
            danger
            icon={<DeleteOutlined />}
            onClick={() => handleDelete(record.id)}
          >
            删除
          </Button>
        </Space>
      ),
    },
  ]

  const emptyContent = (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <span>
          暂无作业，去
          <Link to={`/projects/${pid}/workbench`}> AI 工作台 </Link>
          通过对话生成并保存作业
        </span>
      }
    >
      <Link to={`/projects/${pid}/workbench`}>
        <Button type="primary" icon={<PlusOutlined />}>
          去创建作业
        </Button>
      </Link>
    </Empty>
  )

  return (
    <div style={{ padding: 24 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div>
          <Title level={3} style={{ margin: 0 }}>作业列表</Title>
          <Text type="secondary">通过 AI 工作台生成和保存的 ETL 作业</Text>
        </div>
        <Link to={`/projects/${pid}/workbench`}>
          <Button type="primary" icon={<PlusOutlined />}>新建作业</Button>
        </Link>
      </div>

      <Card>
        <Search
          placeholder="搜索作业名称"
          allowClear
          onSearch={setKeyword}
          style={{ marginBottom: 16 }}
        />
        <Table
          columns={columns}
          dataSource={jobs}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10 }}
          locale={{ emptyText: emptyContent }}
        />
      </Card>
    </div>
  )
}

export default JobListPage