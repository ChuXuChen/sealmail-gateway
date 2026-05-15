package com.auggie.student_server.controller;

import com.auggie.student_server.annotation.RequiresRoles;
import com.auggie.student_server.entity.SysConfig;
import com.auggie.student_server.service.SysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置Controller
 * 仅管理员可访问
 */
@RestController
@RequestMapping("/admin/config")
@CrossOrigin("*")
@RequiresRoles("3") // 仅管理员可访问
public class SysConfigController {

    @Autowired
    private SysConfigService sysConfigService;

    /**
     * 获取所有配置
     */
    @GetMapping("/list")
    public Map<String, Object> getAllConfigs() {
        Map<String, Object> result = new HashMap<>();
        try {
            System.out.println("调用获取所有配置接口");
            List<SysConfig> configList = sysConfigService.findAll();
            System.out.println("查询到配置数量: " + configList.size());
            // 对于加密的配置，不返回真实值，返回空字符串或者掩码
            for (SysConfig config : configList) {
                if (config.getIsEncrypted() != null && config.getIsEncrypted() == 1) {
                    // 不返回真实的加密值，返回占位符，前端显示为密码框
                    config.setConfigValue("");
                }
            }
            result.put("code", 200);
            result.put("data", configList);
            result.put("message", "获取配置成功");
        } catch (Exception e) {
            System.out.println("获取配置失败: " + e.getMessage());
            e.printStackTrace();
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 根据分组获取配置
     */
    @GetMapping("/group/{group}")
    public Map<String, Object> getConfigByGroup(@PathVariable String group) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<SysConfig> configList = sysConfigService.findByGroup(group);
            // 对于加密的配置，不返回真实值
            for (SysConfig config : configList) {
                if (config.getIsEncrypted() != null && config.getIsEncrypted() == 1) {
                    config.setConfigValue("");
                }
            }
            result.put("code", 200);
            result.put("data", configList);
            result.put("message", "获取配置成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 更新配置
     * 接收配置键值对的Map
     */
    @PostMapping("/update")
    public Map<String, Object> updateConfigs(@RequestBody Map<String, String> configMap) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean success = sysConfigService.updateConfigs(configMap);
            result.put("code", success ? 200 : 500);
            result.put("data", success);
            result.put("message", success ? "配置更新成功" : "配置更新失败");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 刷新配置缓存
     */
    @PostMapping("/refresh")
    public Map<String, Object> refreshCache() {
        Map<String, Object> result = new HashMap<>();
        try {
            sysConfigService.refreshCache();
            result.put("code", 200);
            result.put("message", "缓存刷新成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }

    /**
     * 获取AI配置（供内部使用，不需要权限验证？不，这个接口也应该只有管理员能访问）
     */
    @GetMapping("/ai")
    public Map<String, Object> getAiConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> aiConfig = sysConfigService.getAiConfig();
            // 不返回密钥
            aiConfig.remove("apiKey");
            result.put("code", 200);
            result.put("data", aiConfig);
            result.put("message", "获取AI配置成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", e.getMessage());
        }
        return result;
    }
}
