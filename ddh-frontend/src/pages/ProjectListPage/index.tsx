import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Card, Button, Modal, Form, Input, message, Empty, Space, Popconfirm } from 'antd'
import { PlusOutlined, DeleteOutlined, ArrowRightOutlined } from '@ant-design/icons'
import { getProjects, createProject, deleteProject, type Project } from '../../api/project'

function ProjectListPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [form] = Form.useForm()
  const navigate = useNavigate()

  const fetchProjects = async () => {
    setLoading(true)
    try {
      const data = await getProjects()
      setProjects(data)
    } catch (e) {
      // error handled in interceptor
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchProjects()
  }, [])

  const handleCreate = async () => {
    try {
      const values = await form.validateFields()
      await createProject(values)
      message.success('项目创建成功')
      setModalOpen(false)
      form.resetFields()
      fetchProjects()
    } catch (e) {
      // validation error
    }
  }

  const handleDelete = async (id: number) => {
    await deleteProject(id)
    message.success('项目已删除')
    fetchProjects()
  }

  return (
    <div style={{ maxWidth: 1200, margin: '0 auto', padding: '40px 24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <h1 style={{ margin: 0 }}>DDH-Assistant 数仓开发助手</h1>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
          新建项目
        </Button>
      </div>

      {projects.length === 0 && !loading ? (
        <Empty description="暂无项目，点击右上角新建" />
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: 16 }}>
          {projects.map((project) => (
            <Card key={project.id} hoverable>
              <h3 style={{ marginTop: 0 }}>{project.projectName}</h3>
              <p style={{ color: '#666', minHeight: 40 }}>{project.description || '暂无描述'}</p>
              <p style={{ color: '#999', fontSize: 12 }}>创建时间: {project.createdAt}</p>
              <Space>
                <Button
                  type="primary"
                  icon={<ArrowRightOutlined />}
                  onClick={() => navigate(`/projects/${project.id}/metadata`)}
                >
                  进入
                </Button>
                <Popconfirm title="确定要删除该项目吗？" onConfirm={() => handleDelete(project.id)}>
                  <Button danger icon={<DeleteOutlined />}>删除</Button>
                </Popconfirm>
              </Space>
            </Card>
          ))}
        </div>
      )}

      <Modal
        title="新建项目"
        open={modalOpen}
        onOk={handleCreate}
        onCancel={() => { setModalOpen(false); form.resetFields() }}
        okText="创建"
        cancelText="取消"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="projectName" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
            <Input placeholder="请输入项目名称" />
          </Form.Item>
          <Form.Item name="description" label="项目描述">
            <Input.TextArea placeholder="请输入项目描述（可选）" rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}

export default ProjectListPage
