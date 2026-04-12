import request from './request';

export interface Project {
  id: number;
  projectName: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 获取项目列表 */
export function getProjects(keyword?: string): Promise<Project[]> {
  return request.get('/projects', { params: { keyword } });
}

/** 创建项目 */
export function createProject(data: { projectName: string; description?: string }): Promise<Project> {
  return request.post('/projects', data);
}

/** 删除项目 */
export function deleteProject(id: number): Promise<void> {
  return request.delete(`/projects/${id}`);
}

/** 健康检查 */
export function healthCheck(): Promise<any> {
  return request.get('/projects/health');
}
