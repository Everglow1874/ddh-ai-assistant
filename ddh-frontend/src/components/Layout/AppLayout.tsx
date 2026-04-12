import { Outlet, useNavigate, useParams, useLocation } from 'react-router-dom'
import { Layout, Menu } from 'antd'
import {
  DatabaseOutlined,
  CodeOutlined,
  UnorderedListOutlined,
  ArrowLeftOutlined,
} from '@ant-design/icons'

const { Sider, Content, Header } = Layout

function AppLayout() {
  const navigate = useNavigate()
  const { projectId } = useParams()
  const location = useLocation()

  const currentPath = location.pathname.split('/').pop() || 'metadata'

  const menuItems = [
    {
      key: 'metadata',
      icon: <DatabaseOutlined />,
      label: '元数据管理',
    },
    {
      key: 'workbench',
      icon: <CodeOutlined />,
      label: '开发工作台',
    },
    {
      key: 'jobs',
      icon: <UnorderedListOutlined />,
      label: '作业管理',
    },
  ]

  return (
    <Layout style={{ height: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          background: '#001529',
          padding: '0 24px',
        }}
      >
        <ArrowLeftOutlined
          style={{ color: '#fff', fontSize: 16, cursor: 'pointer', marginRight: 16 }}
          onClick={() => navigate('/projects')}
        />
        <span style={{ color: '#fff', fontSize: 18, fontWeight: 'bold' }}>
          DDH-Assistant
        </span>
      </Header>
      <Layout>
        <Sider width={200} theme="light">
          <Menu
            mode="inline"
            selectedKeys={[currentPath]}
            items={menuItems}
            style={{ height: '100%', borderRight: 0 }}
            onClick={({ key }) => navigate(`/projects/${projectId}/${key}`)}
          />
        </Sider>
        <Content style={{ padding: 24, overflow: 'auto', background: '#f5f5f5' }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}

export default AppLayout
