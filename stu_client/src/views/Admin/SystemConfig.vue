<template>
  <page-layout
    title="系统配置"
    subtitle="配置系统相关参数，包括AI接口设置"
    :breadcrumb-list="breadcrumbList"
    v-loading="loading"
    element-loading-text="加载中..."
  >
    <el-card shadow="hover" class="config-card">
      <div slot="header" class="card-header">
        <span>AI 接口配置</span>
        <el-button type="primary" @click="saveConfig" :loading="saving">保存配置</el-button>
      </div>

      <el-form ref="configForm" :model="configForm" label-width="150px" class="config-form">
        <el-form-item label="API 密钥" prop="ai.api.key">
          <el-input
            v-model="configForm.ai.api.key"
            type="password"
            placeholder="请输入API密钥"
            show-password
            autocomplete="off"
          >
            <template slot="append">
              <el-tooltip content="AI服务的API密钥，加密存储">
                <i class="el-icon-info"></i>
              </el-tooltip>
            </template>
          </el-input>
          <div class="form-tip">留空表示不修改现有密钥</div>
        </el-form-item>

        <el-form-item label="API 端点地址" prop="ai.api.endpoint">
          <el-input
            v-model="configForm.ai.api.endpoint"
            placeholder="请输入API端点地址"
            autocomplete="off"
          >
            <template slot="append">
              <el-tooltip content="AI服务的API调用地址">
                <i class="el-icon-info"></i>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="模型名称" prop="ai.model.name">
          <el-input
            v-model="configForm.ai.model.name"
            placeholder="请输入模型名称"
            autocomplete="off"
          >
            <template slot="append">
              <el-tooltip content="使用的AI模型名称">
                <i class="el-icon-info"></i>
              </el-tooltip>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="最大 Token 数" prop="ai.max.tokens">
          <el-input-number
            v-model="configForm.ai.max.tokens"
            :min="100"
            :max="8192"
            :step="100"
            style="width: 200px"
          />
          <span class="form-unit">个</span>
          <div class="form-tip">API调用生成内容的最大长度</div>
        </el-form-item>

        <el-form-item label="温度参数" prop="ai.temperature">
          <el-input-number
            v-model="configForm.ai.temperature"
            :min="0"
            :max="2"
            :step="0.1"
            style="width: 200px"
          />
          <div class="form-tip">控制生成内容的随机性，0表示最确定，2表示最随机</div>
        </el-form-item>

        <el-form-item label="请求超时时间" prop="ai.timeout">
          <el-input-number
            v-model="configForm.ai.timeout"
            :min="10"
            :max="300"
            :step="10"
            style="width: 200px"
          />
          <span class="form-unit">秒</span>
          <div class="form-tip">API请求的超时时间</div>
        </el-form-item>

        <el-form-item label="AI 功能开关" prop="ai.enabled">
          <el-switch
            v-model="configForm.ai.enabled"
            active-value="1"
            inactive-value="0"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 其他配置分组可以在这里添加 -->

  </page-layout>
</template>

<script>
import { getAllConfigs, updateConfigs, refreshConfigCache } from '@/api/config'

export default {
  name: 'SystemConfig',
  data() {
    return {
      breadcrumbList: [
        { name: '首页', path: '/' },
        { name: '系统配置', path: '/admin/system/config' }
      ],
      loading: false,
      saving: false,
      configForm: {
        ai: {
          api: {
            key: '',
            endpoint: ''
          },
          model: {
            name: ''
          },
          max: {
            tokens: 4096
          },
          temperature: '0.7',
          timeout: 60,
          enabled: '1'
        }
      },
      originalConfigs: {} // 保存原始配置，用于对比哪些需要更新
    }
  },
  mounted() {
    this.loadConfigData()
  },
  methods: {
    // 加载配置数据
    async loadConfigData() {
      this.loading = true
      try {
        const res = await getAllConfigs()
        this.originalConfigs = {}
        res.forEach(config => {
          this.originalConfigs[config.configKey] = config
          // 如果是加密的配置项，不显示值，用户输入新值才会更新
          if (config.isEncrypted !== 1) {
            // 把点分隔的路径赋值到嵌套对象
            this.setNestedValue(this.configForm, config.configKey, config.configValue)
          }
        })
      } catch (error) {
        console.error('加载配置失败:', error)
        this.$message.error('加载配置失败')
      } finally {
        this.loading = false
      }
    },

    // 辅助方法：设置嵌套对象的值
    setNestedValue(obj, path, value) {
      const keys = path.split('.')
      let current = obj
      for (let i = 0; i < keys.length - 1; i++) {
        if (!current[keys[i]]) {
          current[keys[i]] = {}
        }
        current = current[keys[i]]
      }
      current[keys[keys.length - 1]] = value
    },
    // 辅助方法：获取嵌套对象的值
    getNestedValue(obj, path) {
      return path.split('.').reduce((current, key) => {
        return current && current[key] !== undefined ? current[key] : undefined
      }, obj)
    },
    // 保存配置
    async saveConfig() {
      this.saving = true
      try {
        // 只提交有修改的配置项，加密的配置项只有用户输入了新值才提交
        const updateData = {}
        Object.keys(this.originalConfigs).forEach(key => {
          const original = this.originalConfigs[key]
          const currentValue = this.getNestedValue(this.configForm, key)
          // 加密的配置项：只有用户输入了新值才提交
          if (original && original.isEncrypted === 1) {
            if (currentValue && currentValue.trim() !== '') {
              updateData[key] = currentValue.trim()
            }
          } else {
            // 非加密的配置项：值有变化的时候提交
            if (currentValue != original.configValue) {
              updateData[key] = currentValue
            }
          }
        })
        // 处理新增的配置项（如果有的话）
        const allConfigKeys = ['ai.api.key', 'ai.api.endpoint', 'ai.model.name', 'ai.max.tokens', 'ai.temperature', 'ai.timeout', 'ai.enabled']
        allConfigKeys.forEach(key => {
          if (!this.originalConfigs[key]) {
            const currentValue = this.getNestedValue(this.configForm, key)
            updateData[key] = currentValue
          }
        })

        if (Object.keys(updateData).length === 0) {
          this.$message.info('没有修改任何配置')
          return
        }

        const res = await updateConfigs(updateData)
        if (res) {
          this.$message.success('配置保存成功')
          // 刷新缓存
          await refreshConfigCache()
          // 重新加载配置
          await this.loadConfigData()
        } else {
          this.$message.error('保存配置失败')
        }
      } catch (error) {
        console.error('保存配置失败:', error)
        this.$message.error('保存配置失败')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.config-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.config-form {
  max-width: 800px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 5px;
}

.form-unit {
  margin-left: 10px;
  color: #606266;
}
</style>
