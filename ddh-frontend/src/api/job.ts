import request from './request';

export interface EtlJob {
  id: number;
  projectId: number;
  sessionId?: number;
  jobName: string;
  description?: string;
  status: string;
  version: number;
  stepCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface EtlJobStep {
  id: number;
  jobId: number;
  stepOrder: number;
  stepName: string;
  stepType?: string;
  description?: string;
  ddlSql?: string;
  dmlSql?: string;
  sourceTables?: string;
  targetTable?: string;
  createdAt?: string;
  updatedAt?: string;
}

export function getJobs(projectId: number, keyword?: string): Promise<EtlJob[]> {
  return request.get(`/projects/${projectId}/jobs`, { params: { keyword } });
}

export function getJob(id: number): Promise<EtlJob> {
  return request.get(`/jobs/${id}`);
}

export function getJobSteps(jobId: number): Promise<EtlJobStep[]> {
  return request.get(`/jobs/${jobId}/steps`);
}

export function createJob(data: { projectId: number; jobName: string; description?: string }): Promise<EtlJob> {
  return request.post(`/projects/${data.projectId}/jobs`, { jobName: data.jobName, description: data.description });
}

export function createJobFromSession(projectId: number, sessionId: number, jobName: string, description?: string): Promise<EtlJob> {
  return request.post(`/projects/${projectId}/jobs/from-session?sessionId=${sessionId}&jobName=${encodeURIComponent(jobName)}${description ? '&description=' + encodeURIComponent(description) : ''}`);
}

export function addJobStep(jobId: number, step: Partial<EtlJobStep>): Promise<EtlJobStep> {
  return request.post(`/jobs/${jobId}/steps`, step);
}

export function updateJobStep(jobId: number, stepId: number, step: Partial<EtlJobStep>): Promise<EtlJobStep> {
  return request.put(`/jobs/${jobId}/steps/${stepId}`, step);
}

export function deleteJob(id: number): Promise<void> {
  return request.delete(`/jobs/${id}`);
}

export function exportJob(id: number): Promise<Blob> {
  return request.get(`/jobs/${id}/export`, { responseType: 'blob' });
}

export function updateJobStatus(id: number, status: string): Promise<EtlJob> {
  return request.put(`/jobs/${id}/status?status=${status}`);
}