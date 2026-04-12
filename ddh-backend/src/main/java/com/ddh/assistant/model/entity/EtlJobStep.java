package com.ddh.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 作业步骤
 */
@Data
@TableName("etl_job_step")
public class EtlJobStep implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private Integer stepOrder;

    private String stepName;

    private String stepType;

    private String description;

    private String ddlSql;

    private String dmlSql;

    private String sourceTables;

    private String targetTable;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
