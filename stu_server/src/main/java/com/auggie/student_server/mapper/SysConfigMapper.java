package com.auggie.student_server.mapper;

import com.auggie.student_server.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 系统配置Mapper
 */
@Mapper
@Repository
public interface SysConfigMapper {

    /**
     * 查询所有配置
     * @return 配置列表
     */
    List<SysConfig> findAll();

    /**
     * 根据配置键查询配置
     * @param configKey 配置键
     * @return 配置信息
     */
    SysConfig findByKey(@Param("configKey") String configKey);

    /**
     * 根据分组查询配置
     * @param configGroup 配置分组
     * @return 配置列表
     */
    List<SysConfig> findByGroup(@Param("configGroup") String configGroup);

    /**
     * 更新配置值
     * @param configKey 配置键
     * @param configValue 配置值
     * @return 是否成功
     */
    boolean updateValueByKey(@Param("configKey") String configKey, @Param("configValue") String configValue);

    /**
     * 插入配置
     * @param sysConfig 配置信息
     * @return 是否成功
     */
    boolean insert(@Param("sysConfig") SysConfig sysConfig);

    /**
     * 删除配置
     * @param configKey 配置键
     * @return 是否成功
     */
    boolean deleteByKey(@Param("configKey") String configKey);
}
