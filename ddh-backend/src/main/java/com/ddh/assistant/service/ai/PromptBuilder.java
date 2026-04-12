package com.ddh.assistant.service.ai;

import com.ddh.assistant.model.entity.TableMetadata;
import com.ddh.assistant.model.entity.ColumnMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Prompt 模板构建器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptBuilder {

    private static final String PROMPT_PATH = "prompts/";

    /**
     * 加载 Prompt 模板
     */
    public String loadTemplate(String templateName) {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_PATH + templateName + ".txt");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.error("加载 Prompt 模板失败: {}", templateName, e);
            return "";
        }
    }

    /**
     * 构建需求分析的 Prompt
     */
    public String buildRequirementPrompt(String userRequirement, List<TableMetadata> tables) {
        String template = loadTemplate("requirement-analysis");
        String tableSummary = buildTableSummary(tables);
        
        return template
                .replace("{{table_summary}}", tableSummary)
                .replace("{{user_requirement}}", userRequirement);
    }

    /**
     * 构建源表推荐的 Prompt
     */
    public String buildTableRecommendPrompt(String requirementJson, List<TableMetadata> tables, 
                                              List<ColumnMetadata> columns) {
        String template = loadTemplate("table-recommend");
        String tableMetadata = buildTableMetadata(tables, columns);
        
        return template
                .replace("{{requirement_json}}", requirementJson)
                .replace("{{table_metadata}}", tableMetadata);
    }

    /**
     * 构建步骤拆分的 Prompt
     */
    public String buildStepDesignPrompt(String requirementJson, String selectedTablesJson) {
        String template = loadTemplate("step-design");
        
        return template
                .replace("{{requirement_json}}", requirementJson)
                .replace("{{selected_tables_json}}", selectedTablesJson);
    }

    /**
     * 构建 SQL 生成的 Prompt
     */
    public String buildSqlGeneratePrompt(String stepJson, String sourceTablesMetadata) {
        String template = loadTemplate("sql-generate");
        
        return template
                .replace("{{step_json}}", stepJson)
                .replace("{{source_tables_metadata}}", sourceTablesMetadata);
    }

    /**
     * 构建 SQL 审查的 Prompt
     */
    public String buildSqlReviewPrompt(String jobName, String jobDescription, String allStepsSql) {
        String template = loadTemplate("sql-review");
        
        return template
                .replace("{{job_name}}", jobName)
                .replace("{{job_description}}", jobDescription)
                .replace("{{all_steps_sql}}", allStepsSql);
    }

    /**
     * 构建 SQL 生成用的表元数据（public，供 ChatService 调用）
     */
    public String buildTableMetadataForSql(List<TableMetadata> tables, List<ColumnMetadata> columns) {
        return buildTableMetadata(tables, columns);
    }

    private String buildTableSummary(List<TableMetadata> tables) {
        if (tables == null || tables.isEmpty()) {
            return "暂无导入的表元数据";
        }
        
        StringBuilder sb = new StringBuilder();
        for (TableMetadata table : tables) {
            sb.append("- ").append(table.getTableName());
            if (table.getTableComment() != null) {
                sb.append(" (").append(table.getTableComment()).append(")");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String buildTableMetadata(List<TableMetadata> tables, List<ColumnMetadata> columns) {
        if (tables == null || tables.isEmpty()) {
            return "暂无元数据";
        }
        
        StringBuilder sb = new StringBuilder();
        for (TableMetadata table : tables) {
            sb.append("### ").append(table.getTableName());
            if (table.getTableComment() != null) {
                sb.append(" (").append(table.getTableComment()).append(")");
            }
            sb.append("\n");
            
            sb.append("| 字段名 | 类型 | 说明 |\n");
            sb.append("|--------|------|------|\n");
            
            // 找到该表的字段
            for (ColumnMetadata col : columns) {
                if (col.getTableId().equals(table.getId())) {
                    sb.append("| ").append(col.getColumnName());
                    sb.append(" | ").append(col.getColumnType());
                    sb.append(" | ").append(col.getColumnComment() != null ? col.getColumnComment() : "");
                    sb.append(" |\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}