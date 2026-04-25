package com.ddh.assistant.service.metadata;

import com.ddh.assistant.model.entity.ColumnMetadata;
import com.ddh.assistant.model.entity.TableMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DDL 语句解析服务
 * 支持从 CREATE TABLE 语句中提取表和字段元数据
 */
@Slf4j
@Service
public class DdlParserService {

    @lombok.Data
    public static class DdlParseResult {
        private List<TableMetadata> tables = new ArrayList<>();
        private List<ColumnMetadata> columns = new ArrayList<>();
        private List<String> errors = new ArrayList<>();
    }

    /**
     * 解析 DDL 文本，支持多个 CREATE TABLE 语句
     */
    public DdlParseResult parseDdl(String ddlText) {
        DdlParseResult result = new DdlParseResult();
        if (!StringUtils.hasText(ddlText)) {
            result.getErrors().add("DDL 文本为空");
            return result;
        }

        // 提取所有 CREATE TABLE 块
        Pattern tablePattern = Pattern.compile(
            "CREATE\\s+(?:TEMP(?:ORARY)?\\s+)?TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?" +
            "(?:`?([\\w.]+)`?\\s*\\.\\s*)?`?([\\w]+)`?\\s*\\(([\\s\\S]*?)\\)\\s*(?:ENGINE|DEFAULT|COMMENT|;|$)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );

        Matcher tableMatcher = tablePattern.matcher(ddlText);
        int tableIndex = 0;

        while (tableMatcher.find()) {
            tableIndex++;
            try {
                String schemaName = tableMatcher.group(1);
                String tableName = tableMatcher.group(2);
                String columnsDdl = tableMatcher.group(3);

                if (!StringUtils.hasText(tableName)) continue;

                // 提取表注释 (COMMENT='xxx' 在 CREATE TABLE 末尾)
                String tableComment = extractTableComment(ddlText, tableMatcher.end());

                TableMetadata table = new TableMetadata();
                table.setTableName(tableName.replaceAll("`", "").trim());
                table.setSchemaName(schemaName != null ? schemaName.replaceAll("`", "").trim() : null);
                table.setTableComment(tableComment);
                table.setSourceType("DDL");

                result.getTables().add(table);

                // 解析字段
                List<ColumnMetadata> cols = parseColumns(columnsDdl, tableIndex);
                result.getColumns().addAll(cols);

                log.info("[DDL Parser] 解析表: {}, 字段数: {}", tableName, cols.size());
            } catch (Exception e) {
                result.getErrors().add("解析第 " + tableIndex + " 个表失败: " + e.getMessage());
                log.warn("[DDL Parser] 解析失败: {}", e.getMessage());
            }
        }

        if (tableIndex == 0) {
            result.getErrors().add("未找到有效的 CREATE TABLE 语句");
        }

        return result;
    }

    private List<ColumnMetadata> parseColumns(String columnsDdl, int tableIndex) {
        List<ColumnMetadata> cols = new ArrayList<>();
        Set<String> primaryKeys = new HashSet<>();

        // 先找 PRIMARY KEY 定义
        Pattern pkPattern = Pattern.compile(
            "PRIMARY\\s+KEY\\s*\\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE
        );
        Matcher pkMatcher = pkPattern.matcher(columnsDdl);
        if (pkMatcher.find()) {
            String pkList = pkMatcher.group(1);
            for (String pk : pkList.split(",")) {
                primaryKeys.add(pk.trim().replaceAll("`", "").toLowerCase());
            }
        }

        // 按行解析字段定义（跳过约束行）
        String[] lines = columnsDdl.split(",(?=\\s*(?:`|[a-zA-Z_])(?:[^,`]*(?:`[^`]*`[^,`]*)*))");
        int sortOrder = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 跳过约束行
            if (line.toUpperCase().matches("^(PRIMARY|UNIQUE|KEY|INDEX|CONSTRAINT|CHECK|FOREIGN).*")) {
                continue;
            }

            try {
                ColumnMetadata col = parseColumnLine(line, sortOrder++, primaryKeys);
                if (col != null) {
                    col.setTableId((long) tableIndex); // 临时用 tableIndex 标记，导入时会替换
                    cols.add(col);
                }
            } catch (Exception e) {
                log.debug("[DDL Parser] 跳过行: '{}', 原因: {}", line, e.getMessage());
            }
        }

        return cols;
    }

    private ColumnMetadata parseColumnLine(String line, int sortOrder, Set<String> primaryKeys) {
        // 字段行格式: `column_name` TYPE [NOT NULL] [DEFAULT xxx] [COMMENT 'xxx']
        Pattern colPattern = Pattern.compile(
            "^`?([\\w]+)`?\\s+([\\w(),' ]+?)(?:\\s+NOT\\s+NULL)?(?:\\s+NULL)?(?:\\s+DEFAULT\\s+([^\\s,]+|'[^']*'))?(?:\\s+AUTO_INCREMENT)?(?:\\s+COMMENT\\s+'([^']*)')?\\s*$",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = colPattern.matcher(line.trim());
        if (!m.find()) return null;

        String colName = m.group(1).trim();
        String colType = m.group(2).trim().toUpperCase();
        String defaultVal = m.group(3);
        String comment = m.group(4);

        // 清理类型字符串
        colType = colType.replaceAll("\\s+", " ")
                         .replaceAll("(?i)\\s*(UNSIGNED|ZEROFILL)\\s*", "")
                         .trim();

        boolean notNull = line.toUpperCase().contains("NOT NULL");
        boolean isPk = primaryKeys.contains(colName.toLowerCase());

        // 如果字段本身有 PRIMARY KEY 标记
        if (line.toUpperCase().contains("PRIMARY KEY")) {
            isPk = true;
        }

        ColumnMetadata col = new ColumnMetadata();
        col.setColumnName(colName);
        col.setColumnType(colType);
        col.setColumnComment(comment);
        col.setIsPrimaryKey(isPk ? 1 : 0);
        col.setIsNullable(notNull ? 0 : 1);
        col.setDefaultValue(defaultVal != null ? defaultVal.replaceAll("^'|'$", "") : null);
        col.setSortOrder(sortOrder);
        return col;
    }

    private String extractTableComment(String ddlText, int searchStart) {
        // 在 CREATE TABLE 结束后的 200 字符内查找 COMMENT
        int end = Math.min(searchStart + 200, ddlText.length());
        String snippet = ddlText.substring(searchStart, end);
        Pattern p = Pattern.compile("COMMENT\\s*=?\\s*'([^']*)'", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(snippet);
        if (m.find()) return m.group(1);
        return null;
    }
}
