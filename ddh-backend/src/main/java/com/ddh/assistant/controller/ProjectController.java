package com.ddh.assistant.controller;

import com.ddh.assistant.common.Result;
import com.ddh.assistant.model.entity.Project;
import com.ddh.assistant.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目管理控制器
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Map<String, Object> info = new HashMap<>();
        info.put("status", "UP");
        info.put("application", "ddh-assistant");
        info.put("version", "1.0.0-SNAPSHOT");
        return Result.ok(info);
    }

    /**
     * 创建项目
     */
    @PostMapping
    public Result<Project> create(@RequestBody Project project) {
        return Result.ok(projectService.create(project));
    }

    /**
     * 获取项目列表
     */
    @GetMapping
    public Result<List<Project>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(projectService.list(keyword));
    }

    /**
     * 获取项目详情
     */
    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        return Result.ok(projectService.getById(id));
    }

    /**
     * 删除项目
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }
}
