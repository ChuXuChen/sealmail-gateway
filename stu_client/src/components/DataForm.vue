<template>
  <div class="data-form">
    <el-form
      ref="dataForm"
      :model="form"
      :rules="rules"
      :label-width="labelWidth"
      label-position="right"
      :inline="inline"
      :disabled="disabled"
    >
      <el-form-item
        v-for="item in fields"
        :key="item.prop"
        :label="item.label"
        :prop="item.prop"
        :label-width="item.labelWidth"
      >
        <!-- 输入框 -->
        <el-input
          v-if="item.type === 'input'"
          v-model="form[item.prop]"
          :placeholder="item.placeholder || `请输入${item.label}`"
          :clearable="item.clearable !== false"
          :disabled="item.disabled || disabled"
          :type="item.inputType || 'text'"
          :show-password="item.showPassword"
          :style="{ width: item.width || '100%' }"
          @keyup.enter.native="handleSubmit"
          @keyup.esc.native="handleReset"
        ></el-input>

        <!-- 下拉选择框 -->
        <el-select
          v-if="item.type === 'select'"
          v-model="form[item.prop]"
          :placeholder="item.placeholder || `请选择${item.label}`"
          :clearable="item.clearable !== false"
          :disabled="item.disabled || disabled"
          :filterable="item.filterable"
          :multiple="item.multiple"
          :style="{ width: item.width || '100%' }"
        >
          <el-option
            v-for="option in item.options"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          ></el-option>
        </el-select>

        <!-- 日期选择器 -->
        <el-date-picker
          v-if="item.type === 'date'"
          v-model="form[item.prop]"
          type="date"
          :placeholder="item.placeholder || `请选择${item.label}`"
          :disabled="item.disabled || disabled"
          :style="{ width: item.width || '100%' }"
          value-format="yyyy-MM-dd"
        ></el-date-picker>

        <!-- 日期时间选择器 -->
        <el-date-picker
          v-if="item.type === 'datetime'"
          v-model="form[item.prop]"
          type="datetime"
          :placeholder="item.placeholder || `请选择${item.label}`"
          :disabled="item.disabled || disabled"
          :style="{ width: item.width || '100%' }"
          value-format="yyyy-MM-dd HH:mm:ss"
        ></el-date-picker>

        <!-- 数字输入框 -->
        <el-input-number
          v-if="item.type === 'number'"
          v-model="form[item.prop]"
          :min="item.min || 0"
          :max="item.max || 999999"
          :disabled="item.disabled || disabled"
          :precision="item.precision || 0"
          :style="{ width: item.width || '100%' }"
        ></el-input-number>

        <!-- 单选框组 -->
        <el-radio-group
          v-if="item.type === 'radio'"
          v-model="form[item.prop]"
          :disabled="item.disabled || disabled"
        >
          <el-radio
            v-for="option in item.options"
            :key="option.value"
            :label="option.value"
          >
            {{ option.label }}
          </el-radio>
        </el-radio-group>

        <!-- 复选框组 -->
        <el-checkbox-group
          v-if="item.type === 'checkbox'"
          v-model="form[item.prop]"
          :disabled="item.disabled || disabled"
        >
          <el-checkbox
            v-for="option in item.options"
            :key="option.value"
            :label="option.value"
          >
            {{ option.label }}
          </el-checkbox>
        </el-checkbox-group>

        <!-- 文本域 -->
        <el-input
          v-if="item.type === 'textarea'"
          v-model="form[item.prop]"
          type="textarea"
          :rows="item.rows || 4"
          :placeholder="item.placeholder || `请输入${item.label}`"
          :disabled="item.disabled || disabled"
          :style="{ width: item.width || '100%' }"
          @keyup.enter.native="handleSubmit"
          @keyup.esc.native="handleReset"
        ></el-input>

        <!-- 自定义内容 -->
        <slot v-if="item.type === 'slot'" :name="item.prop" :row="form"></slot>
      </el-form-item>

      <!-- 表单底部按钮 -->
      <el-form-item v-if="showButtons" class="form-buttons text-center">
        <el-button @click="handleReset" :loading="resetLoading" :disabled="disabled">
          {{ resetText }}
        </el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading" :disabled="disabled">
          {{ submitText }}
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
export default {
  name: 'DataForm',
  props: {
    // 表单数据
    form: {
      type: Object,
      required: true
    },
    // 表单项配置
    fields: {
      type: Array,
      required: true,
      default: () => []
    },
    // 校验规则
    rules: {
      type: Object,
      default: () => ({})
    },
    // 标签宽度
    labelWidth: {
      type: String,
      default: '100px'
    },
    // 是否行内布局
    inline: {
      type: Boolean,
      default: false
    },
    // 是否禁用
    disabled: {
      type: Boolean,
      default: false
    },
    // 是否显示底部按钮
    showButtons: {
      type: Boolean,
      default: true
    },
    // 重置按钮文字
    resetText: {
      type: String,
      default: '重置'
    },
    // 提交按钮文字
    submitText: {
      type: String,
      default: '提交'
    },
    // 重置按钮loading状态
    resetLoading: {
      type: Boolean,
      default: false
    },
    // 提交按钮loading状态
    submitLoading: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    // 提交
    handleSubmit() {
      this.$refs.dataForm.validate((valid) => {
        if (valid) {
          this.$emit('submit', { ...this.form })
        }
      })
    },
    // 重置
    handleReset() {
      this.$refs.dataForm.resetFields()
      // 重置为初始值
      Object.keys(this.form).forEach(key => {
        this.form[key] = this.$options.propsData.form[key] || ''
      })
      this.$emit('reset')
    },
    // 重置校验
    clearValidate() {
      this.$refs.dataForm.clearValidate()
    },
    // 对部分表单字段进行校验
    validateField(props, callback) {
      this.$refs.dataForm.validateField(props, callback)
    }
  }
}
</script>

<style scoped>
.form-buttons {
  padding-top: 20px;
}
</style>
