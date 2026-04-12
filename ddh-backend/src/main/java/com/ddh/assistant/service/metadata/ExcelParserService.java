package com.ddh.assistant.service.metadata;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.ddh.assistant.model.entity.TableMetadata;
import com.ddh.assistant.model.entity.ColumnMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Excel 解析导入服务
 */
@Slf4j
@Service
public class ExcelParserService {

    /**
     * 解析 Excel 文件，提取表和字段元数据
     * Sheet1: 表信息（tableName, tableComment, schemaName）
     * Sheet2: 字段信息（tableName, columnName, columnType, columnComment, isPrimaryKey, isNullable）
     */
    public ParseResult parseExcel(MultipartFile file) throws IOException {
        List<TableInfoDTO> tableInfos = new ArrayList<>();
        List<ColumnInfoDTO> columnInfos = new ArrayList<>();

        // Sheet1: 表信息
        EasyExcel.read(new BufferedInputStream(file.getInputStream()))
                .head(TableInfoDTO.class)
                .sheet(0)
                .registerReadListener(new ReadListener<TableInfoDTO>() {
                    @Override
                    public void invoke(TableInfoDTO data, AnalysisContext context) {
                        if (hasText(data.getTableName())) {
                            tableInfos.add(data);
                        }
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                    }
                })
                .doRead();

        // Sheet2: 字段信息 - 需要重新获取流
        EasyExcel.read(new BufferedInputStream(file.getInputStream()))
                .head(ColumnInfoDTO.class)
                .sheet(1)
                .registerReadListener(new ReadListener<ColumnInfoDTO>() {
                    @Override
                    public void invoke(ColumnInfoDTO data, AnalysisContext context) {
                        if (hasText(data.getTableName()) && hasText(data.getColumnName())) {
                            columnInfos.add(data);
                        }
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                    }
                })
                .doRead();

        return convertToMetadata(tableInfos, columnInfos);
    }

    private ParseResult convertToMetadata(List<TableInfoDTO> tableInfos, List<ColumnInfoDTO> columnInfos) {
        List<TableMetadata> tables = new ArrayList<>();
        List<ColumnMetadata> columns = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // 构建表名到索引的映射
        Map<String, Integer> tableNameToIndex = new HashMap<>();
        
        // 创建表元数据
        for (int i = 0; i < tableInfos.size(); i++) {
            TableInfoDTO dto = tableInfos.get(i);
            TableMetadata table = new TableMetadata();
            table.setTableName(dto.getTableName());
            table.setTableComment(dto.getTableComment());
            table.setSchemaName(dto.getSchemaName());
            tables.add(table);
            tableNameToIndex.put(dto.getTableName(), i + 1); // 使用索引+1 作为临时ID
        }

        // 创建字段元数据
        for (int i = 0; i < columnInfos.size(); i++) {
            ColumnInfoDTO dto = columnInfos.get(i);
            
            Integer tableId = tableNameToIndex.get(dto.getTableName());
            if (tableId == null) {
                errors.add("字段 " + dto.getColumnName() + " 的表 " + dto.getTableName() + " 不存在");
                continue;
            }

            if (!hasText(dto.getColumnType())) {
                errors.add("字段 " + dto.getTableName() + "." + dto.getColumnName() + " 类型为空");
                continue;
            }

            ColumnMetadata column = new ColumnMetadata();
            column.setTableId(tableId.longValue());
            column.setColumnName(dto.getColumnName());
            column.setColumnType(dto.getColumnType());
            column.setColumnComment(dto.getColumnComment());
            column.setIsPrimaryKey(dto.getIsPrimaryKey() != null && dto.getIsPrimaryKey() == 1 ? 1 : 0);
            column.setIsNullable(dto.getIsNullable() == null || dto.getIsNullable() == 1 ? 1 : 0);
            column.setSortOrder(i + 1);
            columns.add(column);
        }

        ParseResult result = new ParseResult();
        result.setTables(tables);
        result.setColumns(columns);
        result.setErrors(errors);
        return result;
    }

    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }

    // ========== DTO 类 ==========

    public static class TableInfoDTO {
        private String tableName;
        private String tableComment;
        private String schemaName;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getTableComment() { return tableComment; }
        public void setTableComment(String tableComment) { this.tableComment = tableComment; }
        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    }

    public static class ColumnInfoDTO {
        private String tableName;
        private String columnName;
        private String columnType;
        private String columnComment;
        private Integer isPrimaryKey;
        private Integer isNullable;

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getColumnType() { return columnType; }
        public void setColumnType(String columnType) { this.columnType = columnType; }
        public String getColumnComment() { return columnComment; }
        public void setColumnComment(String columnComment) { this.columnComment = columnComment; }
        public Integer getIsPrimaryKey() { return isPrimaryKey; }
        public void setIsPrimaryKey(Integer isPrimaryKey) { this.isPrimaryKey = isPrimaryKey; }
        public Integer getIsNullable() { return isNullable; }
        public void setIsNullable(Integer isNullable) { this.isNullable = isNullable; }
    }

    public static class ParseResult {
        private List<TableMetadata> tables;
        private List<ColumnMetadata> columns;
        private List<String> errors;

        public List<TableMetadata> getTables() { return tables; }
        public void setTables(List<TableMetadata> tables) { this.tables = tables; }
        public List<ColumnMetadata> getColumns() { return columns; }
        public void setColumns(List<ColumnMetadata> columns) { this.columns = columns; }
        public List<String> getErrors() { return errors; }
        public void setErrors(List<String> errors) { this.errors = errors; }
    }
}