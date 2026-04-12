package com.ddh.assistant.service.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.assistant.mapper.EtlJobMapper;
import com.ddh.assistant.mapper.EtlJobStepMapper;
import com.ddh.assistant.model.entity.EtlJob;
import com.ddh.assistant.model.entity.EtlJobStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobService {

    private final EtlJobMapper jobMapper;
    private final EtlJobStepMapper stepMapper;

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

    @Transactional
    public EtlJob createFromSession(Long projectId, Long sessionId, String jobName, String description) {
        EtlJob job = new EtlJob();
        job.setProjectId(projectId);
        job.setSessionId(sessionId);
        job.setJobName(jobName);
        job.setDescription(description);
        job.setStatus("DRAFT");
        job.setVersion(1);
        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobMapper.insert(job);
        return job;
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
        if (existing == null) {
            throw new IllegalArgumentException("步骤不存在: " + stepId);
        }
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
        if (job != null) {
            job.setStatus(status);
            jobMapper.updateById(job);
        }
    }
}