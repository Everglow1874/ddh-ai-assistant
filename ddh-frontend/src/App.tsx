import { Routes, Route, Navigate } from 'react-router-dom'
import AppLayout from './components/Layout/AppLayout'
import ProjectListPage from './pages/ProjectListPage'
import MetadataPage from './pages/MetadataPage'
import WorkbenchPage from './pages/WorkbenchPage'
import JobListPage from './pages/JobListPage'
import JobDetailPage from './pages/JobDetailPage'

function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/projects" replace />} />
      <Route path="/projects" element={<ProjectListPage />} />
      <Route path="/projects/:projectId" element={<AppLayout />}>
        <Route path="metadata" element={<MetadataPage />} />
        <Route path="workbench" element={<WorkbenchPage />} />
        <Route path="jobs" element={<JobListPage />} />
        <Route path="jobs/:jobId" element={<JobDetailPage />} />
      </Route>
    </Routes>
  )
}

export default App
