package com.ddh.assistant.controller;

import com.ddh.assistant.common.Result;
import com.ddh.assistant.model.entity.TableMetadata;
import com.ddh.assistant.model.entity.ColumnMetadata;
import com.ddh.assistant.service.metadata.MetadataService;
import com.ddh.assistant.service.metadata.MetadataService.ImportResult;
import com.ddh.assistant.service.metadata.DdlParserService;
import com.ddh.assistant.service.metadata.ExcelParserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 元数据管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final MetadataService metadataService;
    private final ExcelParserService excelParserService;
    private final DdlParserService ddlParserService;

    /**
     * 获取表列表（分页）
     */
    @GetMapping("/tables")
    public Result<Map<String, Object>> listTables(
            @PathVariable Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size) {
        
        Page<TableMetadata> page = metadataService.listTables(projectId, keyword, current, size);
        
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("current", page.getCurrent());
        result.put("size", page.getSize());
        return Result.ok(result);
    }

    /**
     * 获取表列表（不分页）
     */
    @GetMapping("/tables/all")
    public Result<List<TableMetadata>> listAllTables(@PathVariable Long projectId) {
        return Result.ok(metadataService.listAllTables(projectId));
    }

    /**
     * 获取表详情（含字段）
     */
    @GetMapping("/tables/{tableId}")
    public Result<Map<String, Object>> getTableDetail(
            @PathVariable Long projectId,
            @PathVariable Long tableId) {
        
        TableMetadata table = metadataService.getTableById(projectId, tableId);
        List<ColumnMetadata> columns = metadataService.listColumns(tableId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("table", table);
        result.put("columns", columns);
        return Result.ok(result);
    }

    /**
     * 导入 Excel
     */
    @PostMapping("/import/excel")
    public Result<ImportResult> importExcel(
            @PathVariable Long projectId,
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return Result.fail(400, "文件为空");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            return Result.fail(400, "请上传 xlsx 或 xls 格式的 Excel 文件");
        }
        
        try {
            ExcelParserService.ParseResult parseResult = excelParserService.parseExcel(file);
            ImportResult importResult = metadataService.importMetadata(projectId, 
                    parseResult.getTables(), parseResult.getColumns());
            
            if (!parseResult.getErrors().isEmpty()) {
                importResult.setErrors(parseResult.getErrors());
            }
            return Result.ok(importResult);
        } catch (Exception e) {
            log.error("Excel 导入失败", e);
            return Result.fail(500, "Excel 解析失败: " + e.getMessage());
        }
    }

    /**
     * 导入 DDL 语句（CREATE TABLE）
     */
    @PostMapping("/import/ddl")
    public Result<ImportResult> importDdl(
            @PathVariable Long projectId,
            @RequestBody java.util.Map<String, String> body) {

        String ddlText = body.get("ddl");
        if (!org.springframework.util.StringUtils.hasText(ddlText)) {
            return Result.fail(400, "DDL 内容不能为空");
        }

        try {
            DdlParserService.DdlParseResult parseResult = ddlParserService.parseDdl(ddlText);
            if (parseResult.getTables().isEmpty()) {
                return Result.fail(400, "未解析到有效的 CREATE TABLE 语句：" +
                        (parseResult.getErrors().isEmpty() ? "请检查 DDL 格式" : parseResult.getErrors().get(0)));
            }

            ImportResult importResult = metadataService.importMetadata(
                    projectId, parseResult.getTables(), parseResult.getColumns());
            if (!parseResult.getErrors().isEmpty()) {
                importResult.setErrors(parseResult.getErrors());
            }
            return Result.ok(importResult);
        } catch (Exception e) {
            log.error("DDL 导入失败", e);
            return Result.fail(500, "DDL 解析失败: " + e.getMessage());
        }
    }

    /**
     * 删除表
     */
    @DeleteMapping("/tables/{tableId}")
    public Result<Void> deleteTable(
            @PathVariable Long projectId,
            @PathVariable Long tableId) {
        metadataService.deleteTable(projectId, tableId);
        return Result.ok();
    }

    /**
     * 更新表信息
     */
    @PutMapping("/tables/{tableId}")
    public Result<Void> updateTable(
            @PathVariable Long projectId,
            @PathVariable Long tableId,
            @RequestBody TableMetadata table) {
        
        table.setId(tableId);
        metadataService.updateTable(table);
        return Result.ok();
    }

    /**
     * 更新字段信息
     */
    @PutMapping("/columns/{columnId}")
    public Result<Void> updateColumn(
            @PathVariable Long projectId,
            @PathVariable Long columnId,
            @RequestBody ColumnMetadata column) {
        
        column.setId(columnId);
        metadataService.updateColumn(column);
        return Result.ok();
    }
}