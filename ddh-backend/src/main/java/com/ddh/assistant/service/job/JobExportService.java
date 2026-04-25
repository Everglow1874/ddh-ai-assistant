package com.ddh.assistant.service.job;

import com.ddh.assistant.model.entity.EtlJob;
import com.ddh.assistant.model.entity.EtlJobStep;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ETL 作业导出服务
 * 导出结构：
 *   {jobName}/
 *     README.txt
 *     manifest.xml
 *     steps/
 *       01_extract_ddl.sql
 *       01_extract_dml.sql
 *       02_transform_ddl.sql
 *       02_transform_dml.sql
 *       03_load_ddl.sql
 *       03_load_dml.sql
 *     full/
 *       all_ddl.sql    (所有 DDL 合并)
 *       all_dml.sql    (所有 DML 合并)
 *       all_in_one.sql (全量脚本，按执行顺序)
 */
@Service
@RequiredArgsConstructor
public class JobExportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public InputStreamResource exportToZip(EtlJob job, List<EtlJobStep> steps) {
        try {
            Path tempFile = Files.createTempFile("ddh_export_", ".zip");

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
                String dir = sanitizeName(job.getJobName()) + "/";

                // 1. README.txt
                addEntry(zos, dir + "README.txt", generateReadme(job, steps));

                // 2. manifest.xml
                addEntry(zos, dir + "manifest.xml", generateManifest(job, steps));

                // 3. 每步独立 SQL 文件
                StringBuilder allDdl = new StringBuilder(fileHeader(job));
                StringBuilder allDml = new StringBuilder(fileHeader(job));
                StringBuilder allInOne = new StringBuilder(fileHeader(job));

                for (EtlJobStep step : steps) {
                    String prefix = String.format("%02d_%s",
                            step.getStepOrder(),
                            sanitizeName(step.getStepName().toLowerCase().replace(" ", "_")));

                    // DDL 文件
                    if (hasContent(step.getDdlSql())) {
                        String stepDdl = stepFileHeader(step) + step.getDdlSql().trim() + "\n";
                        addEntry(zos, dir + "steps/" + prefix + "_ddl.sql", stepDdl);
                        allDdl.append("\n-- ").append(step.getStepName()).append("\n");
                        allDdl.append(step.getDdlSql().trim()).append("\n");
                        allInOne.append("\n-- [").append(step.getStepOrder()).append("] ").append(step.getStepName()).append(" - DDL\n");
                        allInOne.append(step.getDdlSql().trim()).append("\n");
                    }

                    // DML 文件
                    if (hasContent(step.getDmlSql())) {
                        String stepDml = stepFileHeader(step) + step.getDmlSql().trim() + "\n";
                        addEntry(zos, dir + "steps/" + prefix + "_dml.sql", stepDml);
                        allDml.append("\n-- ").append(step.getStepName()).append("\n");
                        allDml.append(step.getDmlSql().trim()).append("\n");
                        allInOne.append("\n-- [").append(step.getStepOrder()).append("] ").append(step.getStepName()).append(" - DML\n");
                        allInOne.append(step.getDmlSql().trim()).append("\n");
                    }
                }

                // 4. 合并文件
                addEntry(zos, dir + "full/all_ddl.sql", allDdl.toString());
                addEntry(zos, dir + "full/all_dml.sql", allDml.toString());
                addEntry(zos, dir + "full/all_in_one.sql", allInOne.toString());
            }

            InputStream inputStream = new FileInputStream(tempFile.toFile());
            long size = Files.size(tempFile);
            return new InputStreamResource(inputStream) {
                @Override public long contentLength() { return size; }
            };
        } catch (IOException e) {
            throw new RuntimeException("导出作业失败: " + e.getMessage(), e);
        }
    }

    private void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String fileHeader(EtlJob job) {
        return "-- ============================================================\n" +
               "-- Job    : " + job.getJobName() + "\n" +
               "-- Status : " + job.getStatus() + "\n" +
               "-- Version: " + job.getVersion() + "\n" +
               "-- Export : " + LocalDateTime.now().format(FMT) + "\n" +
               "-- ============================================================\n";
    }

    private String stepFileHeader(EtlJobStep step) {
        return "-- ============================================================\n" +
               "-- Step   : " + step.getStepOrder() + " - " + step.getStepName() + "\n" +
               "-- Type   : " + step.getStepType() + "\n" +
               (hasContent(step.getTargetTable()) ? "-- Target : " + step.getTargetTable() + "\n" : "") +
               (hasContent(step.getDescription()) ? "-- Desc   : " + step.getDescription() + "\n" : "") +
               "-- ============================================================\n\n";
    }

    private String generateReadme(EtlJob job, List<EtlJobStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("ETL Job Export\n");
        sb.append("==============================================================\n\n");
        sb.append("Job Name   : ").append(job.getJobName()).append("\n");
        sb.append("Status     : ").append(job.getStatus()).append("\n");
        sb.append("Version    : ").append(job.getVersion()).append("\n");
        sb.append("Description: ").append(job.getDescription() != null ? job.getDescription() : "").append("\n");
        sb.append("Exported At: ").append(LocalDateTime.now().format(FMT)).append("\n\n");

        sb.append("Steps (").append(steps.size()).append("):\n");
        sb.append("--------------------------------------------------------------\n");
        for (EtlJobStep step : steps) {
            sb.append(String.format("  %02d. [%s] %s", step.getStepOrder(), step.getStepType(), step.getStepName()));
            if (hasContent(step.getTargetTable())) sb.append(" → ").append(step.getTargetTable());
            sb.append("\n");
        }

        sb.append("\nFile Structure:\n");
        sb.append("--------------------------------------------------------------\n");
        sb.append("  README.txt           This file\n");
        sb.append("  manifest.xml         Job metadata in XML format\n");
        sb.append("  steps/               Per-step SQL files\n");
        for (EtlJobStep step : steps) {
            String prefix = String.format("    %02d_%s", step.getStepOrder(),
                    sanitizeName(step.getStepName().toLowerCase().replace(" ", "_")));
            if (hasContent(step.getDdlSql())) sb.append(prefix).append("_ddl.sql\n");
            if (hasContent(step.getDmlSql())) sb.append(prefix).append("_dml.sql\n");
        }
        sb.append("  full/                Merged SQL files\n");
        sb.append("    all_ddl.sql        All DDL statements merged\n");
        sb.append("    all_dml.sql        All DML statements merged\n");
        sb.append("    all_in_one.sql     Complete script in execution order\n\n");

        sb.append("Execution Notes:\n");
        sb.append("--------------------------------------------------------------\n");
        sb.append("  - Execute steps in order (01 → 02 → 03 ...)\n");
        sb.append("  - For a full run, use full/all_in_one.sql\n");
        sb.append("  - Target database: GaussDB\n");
        sb.append("  - Jobs are designed to be idempotent (DELETE + INSERT)\n");
        return sb.toString();
    }

    private String generateManifest(EtlJob job, List<EtlJobStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<job>\n");
        sb.append("  <name>").append(escapeXml(job.getJobName())).append("</name>\n");
        sb.append("  <description>").append(escapeXml(job.getDescription())).append("</description>\n");
        sb.append("  <status>").append(job.getStatus()).append("</status>\n");
        sb.append("  <version>").append(job.getVersion()).append("</version>\n");
        sb.append("  <createdAt>").append(job.getCreatedAt()).append("</createdAt>\n");
        sb.append("  <exportedAt>").append(LocalDateTime.now().format(FMT)).append("</exportedAt>\n");
        sb.append("  <steps count=\"").append(steps.size()).append("\">\n");
        for (EtlJobStep step : steps) {
            sb.append("    <step order=\"").append(step.getStepOrder()).append("\">\n");
            sb.append("      <name>").append(escapeXml(step.getStepName())).append("</name>\n");
            sb.append("      <type>").append(step.getStepType()).append("</type>\n");
            sb.append("      <description>").append(escapeXml(step.getDescription())).append("</description>\n");
            sb.append("      <targetTable>").append(escapeXml(step.getTargetTable())).append("</targetTable>\n");
            sb.append("      <hasDdl>").append(hasContent(step.getDdlSql())).append("</hasDdl>\n");
            sb.append("      <hasDml>").append(hasContent(step.getDmlSql())).append("</hasDml>\n");
            sb.append("    </step>\n");
        }
        sb.append("  </steps>\n");
        sb.append("</job>\n");
        return sb.toString();
    }

    private boolean hasContent(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String sanitizeName(String name) {
        if (name == null) return "job";
        return name.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_\\-]", "_").replaceAll("_+", "_");
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                   .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
