package com.ddh.assistant.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

/**
 * 字段元数据
 */
@Data
@TableName("column_metadata")
public class ColumnMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tableId;

    private String columnName;

    private String columnType;

    private String columnComment;

    private Integer isPrimaryKey;

    private Integer isNullable;

    private String defaultValue;

    private Integer sortOrder;
}
