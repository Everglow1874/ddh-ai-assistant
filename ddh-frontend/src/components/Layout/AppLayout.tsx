import { useEffect, useState } from 'react'
import { Outlet, useNavigate, useParams, useLocation } from 'react-router-dom'
import { Layout, Menu, Typography } from 'antd'
import {
  DatabaseOutlined,
  CodeOutlined,
  UnorderedListOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons'
import { getProject, type Project } from '../../api/project'

const { Sider, Content, Header } = Layout
const { Text } = Typography

function AppLayout() {
  const navigate = useNavigate()
  const { projectId } = useParams()
  const location = useLocation()
  const [project, setProject] = useState<Project | null>(null)

  useEffect(() => {
    if (projectId) {
      getProject(Number(projectId)).then(setProject).catch(() => {})
    }
  }, [projectId])

  // 判断当前选中菜单：jobs 下面的子路由（如 jobs/:jobId）也高亮 jobs
  const pathParts = location.pathname.split('/')
  const lastPart = pathParts[pathParts.length - 1]
  const secondLast = pathParts[pathParts.length - 2]
  let selectedKey = lastPart
  if (secondLast === 'jobs' && lastPart !== 'jobs') {
    selectedKey = 'jobs'
  }

  const menuItems = [
    { key: 'metadata', icon: <DatabaseOutlined />, label: '元数据管理' },
    { key: 'workbench', icon: <CodeOutlined />, label: '开发工作台' },
    { key: 'jobs', icon: <UnorderedListOutlined />, label: '作业管理' },
  ]

  return (
    <Layout style={{ height: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', background: '#001529', padding: '0 24px', gap: 12 }}>
        <ArrowLeftOutlined
          style={{ color: '#fff', fontSize: 16, cursor: 'pointer', flexShrink: 0 }}
          onClick={() => navigate('/projects')}
        />
        <span style={{ color: '#fff', fontSize: 17, fontWeight: 700, letterSpacing: '0.5px', flexShrink: 0 }}>
          DDH-Assistant
        </span>
        {project && (
          <>
            <span style={{ color: 'rgba(255,255,255,0.3)', fontSize: 14, flexShrink: 0 }}>／</span>
            <Text style={{ color: 'rgba(255,255,255,0.85)', fontSize: 14, maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {project.projectName}
            </Text>
          </>
        )}
      </Header>
      <Layout>
        <Sider width={200} theme="light">
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={menuItems}
            style={{ height: '100%', borderRight: 0 }}
            onClick={({ key }) => navigate(`/projects/${projectId}/${key}`)}
          />
        </Sider>
        <Content style={{ padding: 20, overflow: 'auto', background: '#f5f5f5' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}

export default AppLayout
