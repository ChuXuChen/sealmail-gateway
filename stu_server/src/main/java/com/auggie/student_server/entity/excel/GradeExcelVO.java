package com.auggie.student_server.entity.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩导出Excel视图对象
 */
@Data
public class GradeExcelVO {

    @ExcelProperty("课程号")
    private Integer cid;

    @ExcelProperty("课程名")
    private String cname;

    @ExcelProperty("学号")
    private Integer sid;

    @ExcelProperty("学生姓名")
    private String sname;

    @ExcelProperty("成绩")
    private BigDecimal grade;

    @ExcelProperty("学期")
    private String term;

}
