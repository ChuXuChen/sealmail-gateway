package com.auggie.student_server.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.type.Alias;

import java.util.Date;

/**
 * 系统配置实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Alias("SysConfig")
public class SysConfig {

    /**
     * 配置ID
     */
    private Integer configId;

    /**
     * 配置键名
     */
    private String configKey;

    /**
     * 配置值
     */
    private String configValue;

    /**
     * 配置分组
     */
    private String configGroup;

    /**
     * 配置类型
     */
    private String configType;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 是否加密存储
     */
    private Integer isEncrypted;

    /**
     * 是否系统配置
     */
    private Integer isSystem;

    /**
     * 显示顺序
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
