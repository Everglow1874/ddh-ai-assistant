import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button, Modal, Form, Input, message, Empty, Space, Popconfirm, Typography, Tag } from 'antd'
import {
  PlusOutlined, DeleteOutlined, ArrowRightOutlined,
  DatabaseOutlined, UnorderedListOutlined, ClockCircleOutlined,
} from '@ant-design/icons'
import { getProjects, createProject, deleteProject, type Project } from '../../api/project'

const { Text, Title } = Typography

function ProjectListPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [creating, setCreating] = useState(false)
  const [form] = Form.useForm()
  const navigate = useNavigate()

  const fetchProjects = async () => {
    setLoading(true)
    try {
      const data = await getProjects()
      setProjects(data)
    } catch { /* handled */ } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchProjects() }, [])

  const handleCreate = async () => {
    try {
      const values = await form.validateFields()
      setCreating(true)
      await createProject(values)
      message.success('项目创建成功')
      setModalOpen(false)
      form.resetFields()
      fetchProjects()
    } catch { /* validation error */ } finally {
      setCreating(false)
    }
  }

  const handleDelete = async (id: number) => {
    await deleteProject(id)
    message.success('项目已删除')
    fetchProjects()
  }

  const formatDate = (s?: string) => {
    if (!s) return ''
    return new Date(s).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
  }

  return (
    <div style={{ minHeight: '100vh', background: '#f5f6fa', padding: '0' }}>
      {/* 顶部 Banner */}
      <div style={{
        background: 'linear-gradient(135deg, #001529 0%, #003a70 100%)',
        padding: '40px 60px 36px',
        marginBottom: 0,
      }}>
        <div style={{ maxWidth: 1100, margin: '0 auto' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
            <div>
              <Title style={{ color: '#fff', margin: 0, fontSize: 28, fontWeight: 700 }}>
                DDH-Assistant
              </Title>
              <Text style={{ color: 'rgba(255,255,255,0.65)', fontSize: 15, marginTop: 6, display: 'block' }}>
                数仓 ETL 作业开发 AI 助手 · 让 SQL 开发更高效
              </Text>
            </div>
            <Button
              type="primary"
              size="large"
              icon={<PlusOutlined />}
              onClick={() => setModalOpen(true)}
              style={{ borderRadius: 8, height: 44, paddingInline: 24 }}
            >
              新建项目
            </Button>
          </div>
        </div>
      </div>

      {/* 主内容 */}
      <div style={{ maxWidth: 1100, margin: '0 auto', padding: '32px 60px 60px' }}>
        {loading ? (
          <div style={{ textAlign: 'center', paddingTop: 80, color: '#999' }}>加载中…</div>
        ) : projects.length === 0 ? (
          <div style={{
            background: '#fff',
            borderRadius: 12,
            padding: '80px 40px',
            textAlign: 'center',
            border: '1px dashed #d9d9d9',
          }}>
            <Empty
              description={
                <div>
                  <Text style={{ fontSize: 16, color: '#666' }}>还没有项目</Text><br />
                  <Text type="secondary" style={{ fontSize: 13 }}>创建第一个项目，开始 AI 辅助 ETL 开发</Text>
                </div>
              }
            >
              <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
                立即创建
              </Button>
            </Empty>
          </div>
        ) : (
          <>
            <div style={{ marginBottom: 20, display: 'flex', alignItems: 'center', gap: 8 }}>
              <Text style={{ fontSize: 14, color: '#666' }}>共 {projects.length} 个项目</Text>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: 16 }}>
              {projects.map((project) => (
                <div
                  key={project.id}
                  style={{
                    background: '#fff',
                    borderRadius: 12,
                    border: '1px solid #e8e8e8',
                    padding: '20px 22px',
                    cursor: 'pointer',
                    transition: 'all 0.2s',
                    position: 'relative',
                    overflow: 'hidden',
                  }}
                  onMouseEnter={e => {
                    (e.currentTarget as HTMLDivElement).style.boxShadow = '0 4px 20px rgba(0,0,0,0.1)'
                    ;(e.currentTarget as HTMLDivElement).style.borderColor = '#1677ff'
                    ;(e.currentTarget as HTMLDivElement).style.transform = 'translateY(-2px)'
                  }}
                  onMouseLeave={e => {
                    (e.currentTarget as HTMLDivElement).style.boxShadow = 'none'
                    ;(e.currentTarget as HTMLDivElement).style.borderColor = '#e8e8e8'
                    ;(e.currentTarget as HTMLDivElement).style.transform = 'translateY(0)'
                  }}
                  onClick={() => navigate(`/projects/${project.id}/metadata`)}
                >
                  {/* 顶色条 */}
                  <div style={{
                    position: 'absolute', top: 0, left: 0, right: 0, height: 3,
                    background: `hsl(${(project.id * 47) % 360}, 60%, 55%)`,
                    borderRadius: '12px 12px 0 0',
                  }} />

                  <div style={{ marginBottom: 10 }}>
                    <Text strong style={{ fontSize: 16, display: 'block', marginBottom: 4 }}>
                      {project.projectName}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 13, lineHeight: 1.5, display: 'block', minHeight: 36 }}>
                      {project.description || '暂无描述'}
                    </Text>
                  </div>

                  <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginTop: 12, paddingTop: 12, borderTop: '1px solid #f0f0f0' }}>
                    <Space size={4}>
                      <ClockCircleOutlined style={{ fontSize: 11, color: '#bbb' }} />
                      <Text type="secondary" style={{ fontSize: 12 }}>{formatDate(project.createdAt)}</Text>
                    </Space>
                    <div style={{ flex: 1 }} />
                    <Space>
                      <Button
                        type="primary"
                        size="small"
                        icon={<ArrowRightOutlined />}
                        onClick={(e) => { e.stopPropagation(); navigate(`/projects/${project.id}/metadata`) }}
                        style={{ borderRadius: 6 }}
                      >
                        进入
                      </Button>
                      <Popconfirm
                        title="确定要删除该项目吗？"
                        description="删除后无法恢复，包含的元数据和作业也会被删除。"
                        onConfirm={(e) => { e?.stopPropagation(); handleDelete(project.id) }}
                        onCancel={(e) => e?.stopPropagation()}
                        okText="删除"
                        okButtonProps={{ danger: true }}
                      >
                        <Button
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          onClick={(e) => e.stopPropagation()}
                          style={{ borderRadius: 6 }}
                        />
                      </Popconfirm>
                    </Space>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      {/* 新建项目弹窗 */}
      <Modal
        title="新建项目"
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => { setModalOpen(false); form.resetFields() }}
        okText="创建"
        okButtonProps={{ loading: creating }}
        cancelText="取消"
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="projectName"
            label="项目名称"
            rules={[{ required: true, message: '请输入项目名称' }]}
          >
            <Input placeholder="例如：销售数仓 ETL 项目" maxLength={100} showCount />
          </Form.Item>
          <Form.Item name="description" label="项目描述">
            <Input.TextArea
              placeholder="简要描述项目用途（可选）"
              rows={3}
              maxLength={500}
              showCount
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default ProjectListPage
