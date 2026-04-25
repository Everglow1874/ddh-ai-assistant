package com.ddh.assistant.controller;

import com.ddh.assistant.common.Result;
import com.ddh.assistant.model.entity.EtlJob;
import com.ddh.assistant.model.entity.EtlJobStep;
import com.ddh.assistant.service.job.JobExportService;
import com.ddh.assistant.service.job.JobService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final JobExportService jobExportService;

    /** 作业列表（含步骤数） */
    @GetMapping("/projects/{projectId}/jobs")
    public Result<List<JobVO>> listJobs(@PathVariable Long projectId,
                                        @RequestParam(required = false) String keyword) {
        List<EtlJob> jobs = jobService.list(projectId, keyword);
        List<JobVO> vos = jobs.stream().map(job -> {
            List<EtlJobStep> steps = jobService.getStepsByJob(job.getId());
            return new JobVO(job, steps.size());
        }).collect(Collectors.toList());
        return Result.ok(vos);
    }

    @GetMapping("/jobs/{id}")
    public Result<EtlJob> getJob(@PathVariable Long id) {
        return Result.ok(jobService.getById(id));
    }

    @GetMapping("/jobs/{id}/steps")
    public Result<List<EtlJobStep>> getJobSteps(@PathVariable Long id) {
        return Result.ok(jobService.getStepsByJob(id));
    }

    @PostMapping("/projects/{projectId}/jobs")
    public Result<EtlJob> createJob(@PathVariable Long projectId,
                                    @RequestBody EtlJob job) {
        job.setProjectId(projectId);
        return Result.ok(jobService.create(job));
    }

    @PostMapping("/projects/{projectId}/jobs/from-session")
    public Result<EtlJob> createFromSession(@PathVariable Long projectId,
                                            @RequestParam Long sessionId,
                                            @RequestParam String jobName,
                                            @RequestParam(required = false) String description) {
        return Result.ok(jobService.createFromSession(projectId, sessionId, jobName, description));
    }

    @PostMapping("/jobs/{jobId}/steps")
    public Result<EtlJobStep> addStep(@PathVariable Long jobId, @RequestBody EtlJobStep step) {
        step.setJobId(jobId);
        return Result.ok(jobService.addStep(step));
    }

    @PutMapping("/jobs/{jobId}/steps/{stepId}")
    public Result<EtlJobStep> updateStep(@PathVariable Long jobId,
                                         @PathVariable Long stepId,
                                         @RequestBody EtlJobStep step) {
        return Result.ok(jobService.updateStep(stepId, step));
    }

    @DeleteMapping("/jobs/{id}")
    public Result<Void> deleteJob(@PathVariable Long id) {
        jobService.delete(id);
        return Result.ok();
    }

    @GetMapping("/jobs/{id}/export")
    public ResponseEntity<InputStreamResource> exportJob(@PathVariable Long id) {
        EtlJob job = jobService.getById(id);
        List<EtlJobStep> steps = jobService.getStepsByJob(id);
        InputStreamResource resource = jobExportService.exportToZip(job, steps);
        String filename = job.getJobName().replaceAll("[^a-zA-Z0-9]", "_") + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PutMapping("/jobs/{id}/status")
    public Result<EtlJob> updateStatus(@PathVariable Long id, @RequestParam String status) {
        jobService.updateStatus(id, status);
        return Result.ok(jobService.getById(id));
    }

    /** 作业视图对象，附带步骤数 */
    @Data
    public static class JobVO {
        private Long id;
        private Long projectId;
        private Long sessionId;
        private String jobName;
        private String description;
        private String status;
        private Integer version;
        private Integer stepCount;
        private String createdAt;
        private String updatedAt;

        public JobVO(EtlJob job, int stepCount) {
            this.id = job.getId();
            this.projectId = job.getProjectId();
            this.sessionId = job.getSessionId();
            this.jobName = job.getJobName();
            this.description = job.getDescription();
            this.status = job.getStatus();
            this.version = job.getVersion();
            this.stepCount = stepCount;
            this.createdAt = job.getCreatedAt() != null ? job.getCreatedAt().toString() : null;
            this.updatedAt = job.getUpdatedAt() != null ? job.getUpdatedAt().toString() : null;
        }
    }
}
