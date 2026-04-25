package com.ddh.assistant.service.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.assistant.mapper.ChatSessionMapper;
import com.ddh.assistant.mapper.EtlJobMapper;
import com.ddh.assistant.mapper.EtlJobStepMapper;
import com.ddh.assistant.model.entity.ChatSession;
import com.ddh.assistant.model.entity.EtlJob;
import com.ddh.assistant.model.entity.EtlJobStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final EtlJobMapper jobMapper;
    private final EtlJobStepMapper stepMapper;
    private final ChatSessionMapper sessionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<EtlJob> listByProject(Long projectId) {
        LambdaQueryWrapper<EtlJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EtlJob::getProjectId, projectId);
        wrapper.orderByDesc(EtlJob::getUpdatedAt);
        return jobMapper.selectList(wrapper);
    }

    public List<EtlJob> list(Long projectId, String keyword) {
        LambdaQueryWrapper<EtlJob> wrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            wrapper.eq(EtlJob::getProjectId, projectId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like(EtlJob::getJobName, keyword);
        }
        wrapper.orderByDesc(EtlJob::getUpdatedAt);
        return jobMapper.selectList(wrapper);
    }

    public EtlJob getById(Long id) {
        EtlJob job = jobMapper.selectById(id);
        if (job == null) {
            throw new IllegalArgumentException("作业不存在: " + id);
        }
        return job;
    }

    public List<EtlJobStep> getStepsByJob(Long jobId) {
        LambdaQueryWrapper<EtlJobStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EtlJobStep::getJobId, jobId);
        wrapper.orderByAsc(EtlJobStep::getStepOrder);
        return stepMapper.selectList(wrapper);
    }

    @Transactional
    public EtlJob create(EtlJob job) {
        LocalDateTime now = LocalDateTime.now();
        if (job.getCreatedAt() == null) job.setCreatedAt(now);
        if (job.getUpdatedAt() == null) job.setUpdatedAt(now);
        jobMapper.insert(job);
        return job;
    }

    /**
     * 从对话会话创建 ETL 作业，并解析 AI 生成的 SQL 拆分为步骤
     */
    @Transactional
    public EtlJob createFromSession(Long projectId, Long sessionId, String jobName, String description) {
        ChatSession session = sessionMapper.selectById(sessionId);
        
        EtlJob job = new EtlJob();
        job.setProjectId(projectId);
        job.setSessionId(sessionId);
        job.setJobName(jobName);
        job.setDescription(description);
        // 如果会话已完成（DONE），直接标记为 COMPLETED
        String sessionState = session != null ? session.getCurrentState() : "INIT";
        job.setStatus("DONE".equals(sessionState) ? "COMPLETED" : "DRAFT");
        job.setVersion(1);
        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobMapper.insert(job);
        log.info("[JobService] 作业创建成功, jobId={}, sessionId={}", job.getId(), sessionId);

        try {
            if (session != null && StringUtils.hasText(session.getContextJson())) {
                JsonNode context = objectMapper.readTree(session.getContextJson());
                parseAndCreateSteps(job.getId(), context);
            }
        } catch (Exception e) {
            log.warn("[JobService] 解析会话 context 失败，作业步骤为空: {}", e.getMessage());
        }

        return job;
    }

    private void parseAndCreateSteps(Long jobId, JsonNode context) {
        String generatedSql = context.has("generatedSql")
                ? context.get("generatedSql").asText() : null;
        String stepDesignJson = context.has("stepDesign")
                ? context.get("stepDesign").asText() : null;

        if (!StringUtils.hasText(generatedSql)) {
            log.info("[JobService] context 中无 generatedSql，跳过步骤解析");
            return;
        }

        log.info("[JobService] 开始解析 SQL 步骤，SQL 长度={}", generatedSql.length());

        List<EtlJobStep> steps = tryParseStepsFromStepDesign(jobId, stepDesignJson, generatedSql);

        if (steps.isEmpty()) {
            steps = parseStepsFromSql(jobId, generatedSql);
        }

        for (EtlJobStep step : steps) {
            LocalDateTime now = LocalDateTime.now();
            step.setCreatedAt(now);
            step.setUpdatedAt(now);
            stepMapper.insert(step);
            log.info("[JobService] 步骤入库: order={}, name={}", step.getStepOrder(), step.getStepName());
        }
    }

    private List<EtlJobStep> tryParseStepsFromStepDesign(Long jobId, String stepDesignJson, String generatedSql) {
        List<EtlJobStep> steps = new ArrayList<>();
        if (!StringUtils.hasText(stepDesignJson)) return steps;

        try {
            JsonNode design = objectMapper.readTree(stepDesignJson);
            if (!design.has("phases")) return steps;

            List<String> ddlBlocks = extractDdlBlocks(generatedSql);
            List<String> dmlBlocks = extractDmlBlocks(generatedSql);

            JsonNode phases = design.get("phases");
            int order = 1;
            int ddlIdx = 0, dmlIdx = 0;

            for (JsonNode phase : phases) {
                String phaseType = phase.has("phase") ? phase.get("phase").asText() : "UNKNOWN";
                String tempTable = phase.has("temp_table") ? phase.get("temp_table").asText() : "";
                String targetTable = phase.has("target_table") ? phase.get("target_table").asText() : "";
                String sourceTables = phase.has("source_tables") ? phase.get("source_tables").toString() : "";

                EtlJobStep step = new EtlJobStep();
                step.setJobId(jobId);
                step.setStepOrder(order++);
                step.setStepType(phaseType);
                step.setSourceTables(sourceTables);

                switch (phaseType) {
                    case "EXTRACT":
                        step.setStepName("Extract - 数据抽取");
                        step.setTargetTable(tempTable);
                        step.setDescription("从源表抽取数据到临时抽取表 " + tempTable);
                        if (ddlIdx < ddlBlocks.size()) step.setDdlSql(ddlBlocks.get(ddlIdx++));
                        if (dmlIdx < dmlBlocks.size()) step.setDmlSql(dmlBlocks.get(dmlIdx++));
                        break;
                    case "TRANSFORM":
                        step.setStepName("Transform - 数据转换");
                        step.setTargetTable(tempTable);
                        step.setDescription("清洗转换数据，写入转换临时表 " + tempTable);
                        if (ddlIdx < ddlBlocks.size()) step.setDdlSql(ddlBlocks.get(ddlIdx++));
                        if (dmlIdx < dmlBlocks.size()) step.setDmlSql(dmlBlocks.get(dmlIdx++));
                        break;
                    case "LOAD":
                        step.setStepName("Load - 数据加载");
                        step.setTargetTable(targetTable);
                        step.setDescription("将转换后数据加载到目标表 " + targetTable);
                        if (ddlIdx < ddlBlocks.size()) step.setDdlSql(ddlBlocks.get(ddlIdx++));
                        if (dmlIdx < dmlBlocks.size()) step.setDmlSql(dmlBlocks.get(dmlIdx++));
                        break;
                    default:
                        step.setStepName(phaseType + " 步骤");
                        step.setDescription("ETL 步骤");
                }
                steps.add(step);
            }
            log.info("[JobService] 从 stepDesign 解析到 {} 个步骤", steps.size());
        } catch (Exception e) {
            log.warn("[JobService] stepDesign 解析失败: {}", e.getMessage());
            steps.clear();
        }
        return steps;
    }

    private List<EtlJobStep> parseStepsFromSql(Long jobId, String sql) {
        List<EtlJobStep> steps = new ArrayList<>();
        String[] sections = sql.split("(?i)(?=--\\s*(?:extract|transform|load|step|阶段|步骤|phase))");

        if (sections.length <= 1) {
            EtlJobStep step = new EtlJobStep();
            step.setJobId(jobId);
            step.setStepOrder(1);
            step.setStepName("ETL 作业脚本");
            step.setStepType("FULL");
            step.setDescription("完整 ETL 作业 SQL");
            step.setDmlSql(sql);
            steps.add(step);
            return steps;
        }

        String[] phaseNames = {"EXTRACT", "TRANSFORM", "LOAD"};
        int order = 1;
        for (String section : sections) {
            if (!StringUtils.hasText(section.trim())) continue;
            String upper = section.toUpperCase();
            String stepType = "UNKNOWN";
            String stepName = "步骤 " + order;

            for (String phase : phaseNames) {
                if (upper.contains(phase)) { stepType = phase; break; }
            }

            switch (stepType) {
                case "EXTRACT": stepName = "Extract - 数据抽取"; break;
                case "TRANSFORM": stepName = "Transform - 数据转换"; break;
                case "LOAD": stepName = "Load - 数据加载"; break;
            }

            String ddl = extractDdlFromSection(section);
            String dml = extractDmlFromSection(section);

            EtlJobStep step = new EtlJobStep();
            step.setJobId(jobId);
            step.setStepOrder(order++);
            step.setStepName(stepName);
            step.setStepType(stepType);
            step.setDescription(java.util.Arrays.stream(section.split("\\r?\\n"))
                    .filter(l -> l.trim().startsWith("--"))
                    .findFirst().map(l -> l.trim().replaceFirst("^--\\s*", "")).orElse(""));
            step.setDdlSql(StringUtils.hasText(ddl) ? ddl : null);
            step.setDmlSql(StringUtils.hasText(dml) ? dml : section.trim());
            steps.add(step);
        }
        return steps;
    }

    private List<String> extractDdlBlocks(String sql) {
        List<String> blocks = new ArrayList<>();
        Pattern p = Pattern.compile("(?i)(CREATE\\s+(?:TEMP\\s+)?TABLE[\\s\\S]+?;)", Pattern.MULTILINE);
        Matcher m = p.matcher(sql);
        while (m.find()) blocks.add(m.group(1).trim());
        return blocks;
    }

    private List<String> extractDmlBlocks(String sql) {
        List<String> blocks = new ArrayList<>();
        Pattern p = Pattern.compile("(?i)((?:INSERT|DELETE|UPDATE|DROP)\\s+[\\s\\S]+?;)", Pattern.MULTILINE);
        Matcher m = p.matcher(sql);
        while (m.find()) blocks.add(m.group(1).trim());
        return blocks;
    }

    private String extractDdlFromSection(String section) {
        Pattern p = Pattern.compile("(?i)(CREATE\\s+(?:TEMP\\s+)?TABLE[\\s\\S]+?;)", Pattern.MULTILINE);
        Matcher m = p.matcher(section);
        StringBuilder sb = new StringBuilder();
        while (m.find()) { if (sb.length() > 0) sb.append("\n\n"); sb.append(m.group(1).trim()); }
        return sb.toString();
    }

    private String extractDmlFromSection(String section) {
        Pattern p = Pattern.compile("(?i)((?:INSERT|DELETE|UPDATE|DROP)\\s+[\\s\\S]+?;)", Pattern.MULTILINE);
        Matcher m = p.matcher(section);
        StringBuilder sb = new StringBuilder();
        while (m.find()) { if (sb.length() > 0) sb.append("\n\n"); sb.append(m.group(1).trim()); }
        return sb.toString();
    }

    @Transactional
    public EtlJobStep addStep(EtlJobStep step) {
        LambdaQueryWrapper<EtlJobStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EtlJobStep::getJobId, step.getJobId());
        wrapper.orderByDesc(EtlJobStep::getStepOrder);
        wrapper.last("LIMIT 1");
        EtlJobStep lastStep = stepMapper.selectOne(wrapper);
        step.setStepOrder(lastStep == null ? 1 : lastStep.getStepOrder() + 1);
        stepMapper.insert(step);
        return step;
    }

    @Transactional
    public EtlJobStep updateStep(Long stepId, EtlJobStep step) {
        EtlJobStep existing = stepMapper.selectById(stepId);
        if (existing == null) throw new IllegalArgumentException("步骤不存在: " + stepId);
        step.setId(stepId);
        stepMapper.updateById(step);
        return stepMapper.selectById(stepId);
    }

    @Transactional
    public void delete(Long id) {
        LambdaQueryWrapper<EtlJobStep> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EtlJobStep::getJobId, id);
        stepMapper.delete(wrapper);
        jobMapper.deleteById(id);
    }

    @Transactional
    public void updateStatus(Long id, String status) {
        EtlJob job = jobMapper.selectById(id);
        if (job != null) { job.setStatus(status); jobMapper.updateById(job); }
    }
}
