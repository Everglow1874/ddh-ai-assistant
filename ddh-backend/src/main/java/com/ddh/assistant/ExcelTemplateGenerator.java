package com.ddh.assistant;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;

import java.util.ArrayList;
import java.util.List;

public class ExcelTemplateGenerator {

    public static void main(String[] args) {
        String templatePath = "D:/learning/project/ai/ddh-assistant/ddh-backend/src/main/resources/metadata-template.xlsx";
        
        // Sheet1: 表信息
        List<TableInfo> tableInfoList = new ArrayList<>();
        tableInfoList.add(new TableInfo("sales_order", "订单表", "sales"));
        tableInfoList.add(new TableInfo("user_info", "用户信息表", "sales"));
        tableInfoList.add(new TableInfo("product_info", "商品信息表", "sales"));
        tableInfoList.add(new TableInfo("department", "部门表", "sales"));
        tableInfoList.add(new TableInfo("employee", "员工表", "hr"));
        
        // Sheet2: 字段信息
        List<ColumnInfo> columnInfoList = new ArrayList<>();
        // sales_order 字段
        columnInfoList.add(new ColumnInfo("sales_order", "order_id", "BIGINT", "订单ID", 1, 0));
        columnInfoList.add(new ColumnInfo("sales_order", "user_id", "BIGINT", "用户ID", 0, 0));
        columnInfoList.add(new ColumnInfo("sales_order", "order_date", "DATE", "下单日期", 0, 1));
        columnInfoList.add(new ColumnInfo("sales_order", "amount", "DECIMAL(10,2)", "订单金额", 0, 1));
        columnInfoList.add(new ColumnInfo("sales_order", "status", "VARCHAR(20)", "订单状态", 0, 1));
        columnInfoList.add(new ColumnInfo("sales_order", "dept_id", "INT", "部门ID", 0, 1));
        // user_info 字段
        columnInfoList.add(new ColumnInfo("user_info", "user_id", "BIGINT", "用户ID", 1, 0));
        columnInfoList.add(new ColumnInfo("user_info", "user_name", "VARCHAR(50)", "用户名", 0, 1));
        columnInfoList.add(new ColumnInfo("user_info", "phone", "VARCHAR(20)", "手机号", 0, 1));
        columnInfoList.add(new ColumnInfo("user_info", "email", "VARCHAR(100)", "邮箱", 0, 1));
        // product_info 字段
        columnInfoList.add(new ColumnInfo("product_info", "product_id", "BIGINT", "商品ID", 1, 0));
        columnInfoList.add(new ColumnInfo("product_info", "product_name", "VARCHAR(100)", "商品名称", 0, 1));
        columnInfoList.add(new ColumnInfo("product_info", "category", "VARCHAR(50)", "商品分类", 0, 1));
        columnInfoList.add(new ColumnInfo("product_info", "price", "DECIMAL(10,2)", "价格", 0, 1));
        // department 字段
        columnInfoList.add(new ColumnInfo("department", "dept_id", "INT", "部门ID", 1, 0));
        columnInfoList.add(new ColumnInfo("department", "dept_name", "VARCHAR(50)", "部门名称", 0, 1));
        columnInfoList.add(new ColumnInfo("department", "parent_dept_id", "INT", "上级部门ID", 0, 1));
        // employee 字段
        columnInfoList.add(new ColumnInfo("employee", "emp_id", "BIGINT", "员工ID", 1, 0));
        columnInfoList.add(new ColumnInfo("employee", "emp_name", "VARCHAR(50)", "员工姓名", 0, 1));
        columnInfoList.add(new ColumnInfo("employee", "dept_id", "INT", "部门ID", 0, 1));
        columnInfoList.add(new ColumnInfo("employee", "position", "VARCHAR(50)", "职位", 0, 1));
        columnInfoList.add(new ColumnInfo("employee", "salary", "DECIMAL(10,2)", "薪资", 0, 1));
        
        try (ExcelWriter excelWriter = EasyExcel.write(templatePath).build()) {
            WriteSheet sheet1 = EasyExcel.writerSheet(0, "表信息").head(TableInfo.class).build();
            WriteSheet sheet2 = EasyExcel.writerSheet(1, "字段信息").head(ColumnInfo.class).build();
            
            excelWriter.write(tableInfoList, sheet1);
            excelWriter.write(columnInfoList, sheet2);
            
            System.out.println("Excel 模板生成成功: " + templatePath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // 表信息 Sheet 实体
    public static class TableInfo {
        private String tableName;
        private String tableComment;
        private String schemaName;
        
        public TableInfo() {}
        public TableInfo(String tableName, String tableComment, String schemaName) {
            this.tableName = tableName;
            this.tableComment = tableComment;
            this.schemaName = schemaName;
        }
        
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getTableComment() { return tableComment; }
        public void setTableComment(String tableComment) { this.tableComment = tableComment; }
        public String getSchemaName() { return schemaName; }
        public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    }
    
    // 字段信息 Sheet 实体
    public static class ColumnInfo {
        private String tableName;
        private String columnName;
        private String columnType;
        private String columnComment;
        private Integer isPrimaryKey;
        private Integer isNullable;
        
        public ColumnInfo() {}
        public ColumnInfo(String tableName, String columnName, String columnType, 
                         String columnComment, Integer isPrimaryKey, Integer isNullable) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.columnType = columnType;
            this.columnComment = columnComment;
            this.isPrimaryKey = isPrimaryKey;
            this.isNullable = isNullable;
        }
        
        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getColumnType() { return columnType; }
        public void setColumnType(String columnType) { this.columnType = columnType; }
        public String getColumnComment() { return columnComment; }
        public void setColumnComment(String columnComment) { this.columnComment = columnComment; }
        public Integer getIsPrimaryKey() { return isPrimaryKey; }
        public void setIsPrimaryKey(Integer isPrimaryKey) { this.isPrimaryKey = isPrimaryKey; }
        public Integer getIsNullable() { return isNullable; }
        public void setIsNullable(Integer isNullable) { this.isNullable = isNullable; }
    }
}