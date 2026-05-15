package com.auggie.student_server.service;

import com.auggie.student_server.entity.SysConfig;
import com.auggie.student_server.mapper.SysConfigMapper;
import com.auggie.student_server.utils.AESUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置服务
 */
@Service
public class SysConfigService {

    @Autowired
    private SysConfigMapper sysConfigMapper;

    // 配置缓存
    private final Map<String, SysConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 初始化缓存
     */
    @PostConstruct
    public void initCache() {
        // 延迟初始化，避免Mapper还没初始化完成导致空指针
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 等待1秒确保Spring初始化完成
                refreshCache();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    /**
     * 刷新缓存
     */
    public void refreshCache() {
        if (sysConfigMapper == null) {
            return; // Mapper还没初始化，跳过
        }
        configCache.clear();
        List<SysConfig> allConfigs = sysConfigMapper.findAll();
        if (allConfigs != null) {
            for (SysConfig config : allConfigs) {
                if (config != null && config.getConfigKey() != null) {
                    configCache.put(config.getConfigKey(), config);
                }
            }
        }
        // 如果配置为空，初始化默认配置
        if (configCache.isEmpty()) {
            initDefaultConfigs();
        }
    }

    /**
     * 初始化默认配置到数据库
     */
    private void initDefaultConfigs() {
        // AI配置默认值
        insertConfigIfNotExists("ai.api.key", "b731f82d-dc13-4b70-a841-999b2faeffe7", "ai", "string", "AI API密钥", 1, 1);
        insertConfigIfNotExists("ai.api.endpoint", "https://ark.cn-beijing.volces.com/api/coding", "ai", "string", "AI API端点地址", 0, 2);
        insertConfigIfNotExists("ai.model.name", "ark-code-latest", "ai", "string", "默认使用的AI模型", 0, 3);
        insertConfigIfNotExists("ai.max.tokens", "4096", "ai", "number", "最大token数限制", 0, 4);
        insertConfigIfNotExists("ai.temperature", "0.7", "ai", "string", "温度参数（0-2）", 0, 5);
        insertConfigIfNotExists("ai.timeout", "60", "ai", "number", "API超时时间（秒）", 0, 6);
        insertConfigIfNotExists("ai.enabled", "1", "ai", "boolean", "是否启用AI功能", 0, 7);

        // 重新加载缓存
        List<SysConfig> allConfigs = sysConfigMapper.findAll();
        if (allConfigs != null) {
            for (SysConfig config : allConfigs) {
                if (config != null && config.getConfigKey() != null) {
                    configCache.put(config.getConfigKey(), config);
                }
            }
        }
    }

    /**
     * 如果配置不存在则插入
     */
    private void insertConfigIfNotExists(String configKey, String configValue, String configGroup,
                                        String configType, String description, Integer isEncrypted, Integer sortOrder) {
        SysConfig existing = sysConfigMapper.findByKey(configKey);
        if (existing == null) {
            SysConfig config = new SysConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(configValue);
            config.setConfigGroup(configGroup);
            config.setConfigType(configType);
            config.setDescription(description);
            config.setIsEncrypted(isEncrypted);
            config.setIsSystem(0);
            config.setSortOrder(sortOrder);
            sysConfigMapper.insert(config);
        }
    }

    /**
     * 获取所有配置
     * @return 配置列表，不会返回null
     */
    public List<SysConfig> findAll() {
        List<SysConfig> list = sysConfigMapper.findAll();
        return list != null ? list : new ArrayList<>();
    }

    /**
     * 根据配置键获取配置
     * @param configKey 配置键
     * @return 配置信息
     */
    public SysConfig findByKey(String configKey) {
        if (configKey == null || configKey.trim().isEmpty()) {
            return null;
        }
        // 先从缓存获取
        SysConfig config = configCache.get(configKey);
        if (config == null && sysConfigMapper != null) {
            // 缓存中没有，从数据库查询
            config = sysConfigMapper.findByKey(configKey);
            if (config != null) {
                configCache.put(configKey, config);
            }
        }
        return config;
    }

    /**
     * 根据分组查询配置
     * @param configGroup 配置分组
     * @return 配置列表
     */
    public List<SysConfig> findByGroup(String configGroup) {
        if (configGroup == null || sysConfigMapper == null) {
            return new ArrayList<>();
        }
        return sysConfigMapper.findByGroup(configGroup);
    }

    /**
     * 获取配置值（自动解密）
     * @param configKey 配置键
     * @return 配置值（解密后）
     */
    public String getConfigValue(String configKey) {
        return getConfigValue(configKey, null);
    }

    /**
     * 获取配置值（自动解密，带默认值）
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值（解密后）
     */
    public String getConfigValue(String configKey, String defaultValue) {
        SysConfig config = findByKey(configKey);
        if (config == null || config.getConfigValue() == null || config.getConfigValue().trim().isEmpty()) {
            return defaultValue;
        }

        String value = config.getConfigValue();
        // 如果是加密存储，需要解密
        if (config.getIsEncrypted() != null && config.getIsEncrypted() == 1) {
            try {
                value = AESUtils.decrypt(value);
            } catch (Exception e) {
                // 解密失败，返回原始值
                return value;
            }
        }
        return value;
    }

    /**
     * 获取整数类型的配置值
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Integer getConfigInt(String configKey, Integer defaultValue) {
        String value = getConfigValue(configKey);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取布尔类型的配置值
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Boolean getConfigBoolean(String configKey, Boolean defaultValue) {
        String value = getConfigValue(configKey);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim());
    }

    /**
     * 获取Double类型的配置值
     * @param configKey 配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public Double getConfigDouble(String configKey, Double defaultValue) {
        String value = getConfigValue(configKey);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 更新配置值
     * @param configKey 配置键
     * @param configValue 配置值（明文，会自动加密）
     * @return 是否成功
     */
    public boolean updateConfigValue(String configKey, String configValue) {
        SysConfig config = findByKey(configKey);
        if (config == null) {
            return false;
        }

        String valueToStore = configValue;
        // 如果是加密存储，需要加密
        if (config.getIsEncrypted() != null && config.getIsEncrypted() == 1 && configValue != null && !configValue.trim().isEmpty()) {
            valueToStore = AESUtils.encrypt(configValue);
        }

        boolean success = sysConfigMapper.updateValueByKey(configKey, valueToStore);
        if (success) {
            // 更新缓存
            config.setConfigValue(valueToStore);
            configCache.put(configKey, config);
        }
        return success;
    }

    /**
     * 批量更新配置
     * @param configMap 配置键值对
     * @return 是否全部成功
     */
    public boolean updateConfigs(Map<String, String> configMap) {
        boolean allSuccess = true;
        for (Map.Entry<String, String> entry : configMap.entrySet()) {
            boolean success = updateConfigValue(entry.getKey(), entry.getValue());
            if (!success) {
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    /**
     * 新增配置
     * @param sysConfig 配置信息
     * @return 是否成功
     */
    public boolean insert(SysConfig sysConfig) {
        // 如果是加密存储，需要加密
        if (sysConfig.getIsEncrypted() != null && sysConfig.getIsEncrypted() == 1 && sysConfig.getConfigValue() != null && !sysConfig.getConfigValue().trim().isEmpty()) {
            sysConfig.setConfigValue(AESUtils.encrypt(sysConfig.getConfigValue()));
        }

        boolean success = sysConfigMapper.insert(sysConfig);
        if (success) {
            // 刷新缓存
            refreshCache();
        }
        return success;
    }

    /**
     * 删除配置
     * @param configKey 配置键
     * @return 是否成功
     */
    public boolean deleteByKey(String configKey) {
        boolean success = sysConfigMapper.deleteByKey(configKey);
        if (success) {
            // 从缓存移除
            configCache.remove(configKey);
        }
        return success;
    }

    /**
     * 获取AI相关配置
     * @return AI配置Map
     */
    public Map<String, String> getAiConfig() {
        Map<String, String> aiConfig = new HashMap<>();
        aiConfig.put("apiKey", getConfigValue("ai.api.key", ""));
        aiConfig.put("endpoint", getConfigValue("ai.api.endpoint", "https://ark.cn-beijing.volces.com/api/coding"));
        aiConfig.put("modelName", getConfigValue("ai.model.name", "ark-code-latest"));
        aiConfig.put("maxTokens", getConfigValue("ai.max.tokens", "4096"));
        aiConfig.put("temperature", getConfigValue("ai.temperature", "0.7"));
        aiConfig.put("timeout", getConfigValue("ai.timeout", "60"));
        aiConfig.put("enabled", getConfigValue("ai.enabled", "1"));
        return aiConfig;
    }
}
