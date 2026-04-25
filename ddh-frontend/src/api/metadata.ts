import request from './request';

export interface TableMetadata {
  id: number;
  projectId: number;
  tableName: string;
  tableComment?: string;
  schemaName?: string;
  sourceType?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ColumnMetadata {
  id: number;
  tableId: number;
  columnName: string;
  columnType: string;
  columnComment?: string;
  isPrimaryKey: number;
  isNullable: number;
  defaultValue?: string;
  sortOrder?: number;
}

export interface TableDetail {
  table: TableMetadata;
  columns: ColumnMetadata[];
}

export interface ImportResult {
  totalTables: number;
  importedTables: number;
  totalColumns: number;
  importedColumns: number;
  errors?: string[];
}

/** 获取表列表（分页） */
export function getTables(projectId: number, keyword?: string, current = 1, size = 20): Promise<{ records: TableMetadata[]; total: number; current: number; size: number }> {
  return request.get(`/projects/${projectId}/metadata/tables`, { params: { keyword, current, size } });
}

/** 获取表列表（不分页） */
export function getAllTables(projectId: number): Promise<TableMetadata[]> {
  return request.get(`/projects/${projectId}/metadata/tables/all`);
}

/** 获取表详情（含字段） */
export function getTableDetail(projectId: number, tableId: number): Promise<TableDetail> {
  return request.get(`/projects/${projectId}/metadata/tables/${tableId}`);
}

/** 导入 Excel */
export function importExcel(projectId: number, file: File): Promise<ImportResult> {
  const formData = new FormData();
  formData.append('file', file);
  return request.post(`/projects/${projectId}/metadata/import/excel`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

/** 删除表 */
export function deleteTable(projectId: number, tableId: number): Promise<void> {
  return request.delete(`/projects/${projectId}/metadata/tables/${tableId}`);
}

/** 更新表信息 */
export function updateTable(projectId: number, tableId: number, data: Partial<TableMetadata>): Promise<void> {
  return request.put(`/projects/${projectId}/metadata/tables/${tableId}`, data);
}

/** 更新字段信息 */
export function updateColumn(projectId: number, columnId: number, data: Partial<ColumnMetadata>): Promise<void> {
  return request.put(`/projects/${projectId}/metadata/columns/${columnId}`, data);
}
/** 导入 DDL 语句 */
export function importDdl(projectId: number, ddl: string): Promise<ImportResult> {
  return request.post(`/projects/${projectId}/metadata/import/ddl`, { ddl });
}
