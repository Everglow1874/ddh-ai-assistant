package com.ddh.assistant.service.metadata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ddh.assistant.mapper.ColumnMetadataMapper;
import com.ddh.assistant.mapper.TableMetadataMapper;
import com.ddh.assistant.model.entity.ColumnMetadata;
import com.ddh.assistant.model.entity.TableMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 元数据管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    private final TableMetadataMapper tableMetadataMapper;
    private final ColumnMetadataMapper columnMetadataMapper;

    // ========== 表元数据 CRUD ==========

    public Page<TableMetadata> listTables(Long projectId, String keyword, int current, int size) {
        LambdaQueryWrapper<TableMetadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TableMetadata::getProjectId, projectId);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(TableMetadata::getTableName, keyword).or().like(TableMetadata::getTableComment, keyword));
        }
        wrapper.orderByDesc(TableMetadata::getCreatedAt);
        return tableMetadataMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public List<TableMetadata> listAllTables(Long projectId) {
        LambdaQueryWrapper<TableMetadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TableMetadata::getProjectId, projectId);
        wrapper.orderByAsc(TableMetadata::getTableName);
        return tableMetadataMapper.selectList(wrapper);
    }

    public TableMetadata getTableById(Long tableId) {
        TableMetadata table = tableMetadataMapper.selectById(tableId);
        if (table == null) {
            throw new IllegalArgumentException("表不存在: " + tableId);
        }
        return table;
    }

    public TableMetadata getTableById(Long projectId, Long tableId) {
        TableMetadata table = tableMetadataMapper.selectById(tableId);
        if (table == null || !table.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("表不存在: " + tableId);
        }
        return table;
    }

    // ========== 字段元数据 CRUD ==========

    public List<ColumnMetadata> listColumns(Long tableId) {
        LambdaQueryWrapper<ColumnMetadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ColumnMetadata::getTableId, tableId);
        wrapper.orderByAsc(ColumnMetadata::getSortOrder);
        return columnMetadataMapper.selectList(wrapper);
    }

    public ColumnMetadata getColumnById(Long columnId) {
        return columnMetadataMapper.selectById(columnId);
    }

    // ========== 批量导入 ==========

    @Transactional(rollbackFor = Exception.class)
    public ImportResult importMetadata(Long projectId, List<TableMetadata> tables, List<ColumnMetadata> columns) {
        int importedTables = 0;
        int importedColumns = 0;

        for (TableMetadata table : tables) {
            table.setProjectId(projectId);
            table.setSourceType("EXCEL");
            table.setCreatedAt(LocalDateTime.now());
            table.setUpdatedAt(LocalDateTime.now());
            tableMetadataMapper.insert(table);
            importedTables++;
        }

        for (ColumnMetadata column : columns) {
            columnMetadataMapper.insert(column);
            importedColumns++;
        }

        ImportResult result = new ImportResult();
        result.setTotalTables(tables.size());
        result.setImportedTables(importedTables);
        result.setTotalColumns(columns.size());
        result.setImportedColumns(importedColumns);
        return result;
    }

    // ========== 删除 ==========

    @Transactional(rollbackFor = Exception.class)
    public void deleteTable(Long projectId, Long tableId) {
        TableMetadata table = getTableById(projectId, tableId);
        // 先删除字段
        LambdaQueryWrapper<ColumnMetadata> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ColumnMetadata::getTableId, tableId);
        columnMetadataMapper.delete(wrapper);
        // 再删除表
        tableMetadataMapper.deleteById(tableId);
    }

    // ========== 更新 ==========

    @Transactional(rollbackFor = Exception.class)
    public void updateTable(TableMetadata table) {
        tableMetadataMapper.updateById(table);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateColumn(ColumnMetadata column) {
        columnMetadataMapper.updateById(column);
    }

    /**
     * 导入结果
     */
    public static class ImportResult {
        private int totalTables;
        private int importedTables;
        private int totalColumns;
        private int importedColumns;
        private List<String> errors;

        public int getTotalTables() {
            return totalTables;
        }

        public void setTotalTables(int totalTables) {
            this.totalTables = totalTables;
        }

        public int getImportedTables() {
            return importedTables;
        }

        public void setImportedTables(int importedTables) {
            this.importedTables = importedTables;
        }

        public int getTotalColumns() {
            return totalColumns;
        }

        public void setTotalColumns(int totalColumns) {
            this.totalColumns = totalColumns;
        }

        public int getImportedColumns() {
            return importedColumns;
        }

        public void setImportedColumns(int importedColumns) {
            this.importedColumns = importedColumns;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }
    }
}