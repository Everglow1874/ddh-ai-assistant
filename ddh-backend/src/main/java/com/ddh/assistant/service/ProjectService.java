package com.ddh.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ddh.assistant.mapper.ProjectMapper;
import com.ddh.assistant.model.entity.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 项目管理服务
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;

    public Project create(Project project) {
        projectMapper.insert(project);
        return project;
    }

    public List<Project> list(String keyword) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Project::getProjectName, keyword);
        }
        wrapper.orderByDesc(Project::getCreatedAt);
        return projectMapper.selectList(wrapper);
    }

    public Project getById(Long id) {
        Project project = projectMapper.selectById(id);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在: " + id);
        }
        return project;
    }

    public void delete(Long id) {
        projectMapper.deleteById(id);
    }
}
