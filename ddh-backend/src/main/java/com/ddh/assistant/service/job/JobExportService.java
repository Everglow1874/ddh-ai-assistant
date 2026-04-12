package com.ddh.assistant.service.job;

import com.ddh.assistant.model.entity.EtlJob;
import com.ddh.assistant.model.entity.EtlJobStep;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class JobExportService {

    private static final String MANIFEST_XML = "manifest.xml";
    private static final String DDL_SQL = "ddl.sql";
    private static final String DML_SQL = "dml.sql";
    private static final String README_TXT = "README.txt";

    public InputStreamResource exportToZip(EtlJob job, java.util.List<EtlJobStep> steps) {
        try {
            Path tempFile = Files.createTempFile(job.getJobName(), ".zip");
            
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
                StringBuilder ddlBuilder = new StringBuilder();
                StringBuilder dmlBuilder = new StringBuilder();
                
                ddlBuilder.append("-- ========================================\n");
                ddlBuilder.append("-- Job: ").append(job.getJobName()).append("\n");
                ddlBuilder.append("-- Version: ").append(job.getVersion()).append("\n");
                ddlBuilder.append("-- Created: ").append(job.getCreatedAt()).append("\n");
                ddlBuilder.append("-- ========================================\n\n");
                
                dmlBuilder.append("-- ========================================\n");
                dmlBuilder.append("-- Job: ").append(job.getJobName()).append("\n");
                dmlBuilder.append("-- Version: ").append(job.getVersion()).append("\n");
                dmlBuilder.append("-- Created: ").append(job.getCreatedAt()).append("\n");
                dmlBuilder.append("-- ========================================\n\n");
                
                int stepNum = 1;
                for (EtlJobStep step : steps) {
                    ddlBuilder.append("-- Step ").append(stepNum).append(": ").append(step.getStepName()).append("\n");
                    dmlBuilder.append("-- Step ").append(stepNum).append(": ").append(step.getStepName()).append("\n");
                    
                    if (step.getDdlSql() != null && !step.getDdlSql().isEmpty()) {
                        ddlBuilder.append(step.getDdlSql()).append("\n\n");
                    }
                    if (step.getDmlSql() != null && !step.getDmlSql().isEmpty()) {
                        dmlBuilder.append(step.getDmlSql()).append("\n\n");
                    }
                    stepNum++;
                }
                
                addZipEntry(zos, MANIFEST_XML, generateManifest(job, steps));
                addZipEntry(zos, DDL_SQL, ddlBuilder.toString());
                addZipEntry(zos, DML_SQL, dmlBuilder.toString());
                addZipEntry(zos, README_TXT, generateReadme(job, steps));
            }
            
            InputStream inputStream = new FileInputStream(tempFile.toFile());
            return new InputStreamResource(inputStream) {
                @Override
                public long contentLength() {
                    try {
                        return Files.size(tempFile);
                    } catch (IOException e) {
                        return -1;
                    }
                }
            };
        } catch (IOException e) {
            throw new RuntimeException("导出作业失败: " + e.getMessage(), e);
        }
    }

    private void addZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String generateManifest(EtlJob job, java.util.List<EtlJobStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<job>\n");
        sb.append("  <name>").append(escapeXml(job.getJobName())).append("</name>\n");
        sb.append("  <description>").append(escapeXml(job.getDescription())).append("</description>\n");
        sb.append("  <version>").append(job.getVersion()).append("</version>\n");
        sb.append("  <status>").append(job.getStatus()).append("</status>\n");
        sb.append("  <createdAt>").append(job.getCreatedAt()).append("</createdAt>\n");
        sb.append("  <steps>\n");
        
        for (EtlJobStep step : steps) {
            sb.append("    <step>\n");
            sb.append("      <order>").append(step.getStepOrder()).append("</order>\n");
            sb.append("      <name>").append(escapeXml(step.getStepName())).append("</name>\n");
            sb.append("      <type>").append(step.getStepType()).append("</type>\n");
            sb.append("      <description>").append(escapeXml(step.getDescription())).append("</description>\n");
            sb.append("      <targetTable>").append(escapeXml(step.getTargetTable())).append("</targetTable>\n");
            sb.append("    </step>\n");
        }
        
        sb.append("  </steps>\n");
        sb.append("</job>\n");
        return sb.toString();
    }

    private String generateReadme(EtlJob job, java.util.List<EtlJobStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("ETL Job: ").append(job.getJobName()).append("\n");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 50; i++) line.append("=");
        sb.append(line).append("\n\n");
        sb.append("Description: ").append(job.getDescription()).append("\n\n");
        sb.append("Status: ").append(job.getStatus()).append("\n");
        sb.append("Version: ").append(job.getVersion()).append("\n");
        sb.append("Created: ").append(job.getCreatedAt()).append("\n\n");
        sb.append("Steps (").append(steps.size()).append("):\n");
        
        for (EtlJobStep step : steps) {
            sb.append("  ").append(step.getStepOrder()).append(". ");
            sb.append(step.getStepName());
            sb.append(" (").append(step.getStepType()).append(")\n");
        }
        
        sb.append("\nFiles:\n");
        sb.append("  - manifest.xml: Job metadata in XML format\n");
        sb.append("  - ddl.sql: Data definition statements (CREATE TABLE, etc.)\n");
        sb.append("  - dml.sql: Data manipulation statements (INSERT, UPDATE, etc.)\n");
        
        return sb.toString();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}