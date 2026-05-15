<template>
  <div class="search-form">
    <el-form
      ref="searchForm"
      :model="form"
      :inline="inline"
      :label-width="labelWidth"
      label-position="right"
    >
      <el-form-item
        v-for="item in fields"
        :key="item.prop"
        :label="item.label"
        :prop="item.prop"
        :rules="item.rules"
      >
        <!-- 输入框 -->
        <el-input
          v-if="item.type === 'input'"
          v-model="form[item.prop]"
          :placeholder="item.placeholder || `请输入${item.label}`"
          :clearable="item.clearable !== false"
          :style="{ width: item.width || '200px' }"
          @keyup.enter.native="handleSearch"
        ></el-input>

        <!-- 下拉选择框 -->
        <el-select
          v-if="item.type === 'select'"
          v-model="form[item.prop]"
          :placeholder="item.placeholder || `请选择${item.label}`"
          :clearable="item.clearable !== false"
          :style="{ width: item.width || '200px' }"
          :filterable="item.filterable"
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
          :style="{ width: item.width || '200px' }"
          value-format="yyyy-MM-dd"
        ></el-date-picker>

        <!-- 日期范围选择器 -->
        <el-date-picker
          v-if="item.type === 'daterange'"
          v-model="form[item.prop]"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :style="{ width: item.width || '300px' }"
          value-format="yyyy-MM-dd"
        ></el-date-picker>

        <!-- 数字输入框 -->
        <el-input-number
          v-if="item.type === 'number'"
          v-model="form[item.prop]"
          :min="item.min || 0"
          :max="item.max || 999999"
          :style="{ width: item.width || '200px' }"
        ></el-input-number>
      </el-form-item>

      <!-- 操作按钮 -->
      <el-form-item class="search-buttons">
        <el-button type="primary" icon="el-icon-search" @click="handleSearch" :loading="loading">
          搜索
        </el-button>
        <el-button icon="el-icon-refresh" @click="handleReset">
          重置
        </el-button>
        <slot name="extra"></slot>
      </el-form-item>
    </el-form>
  </div>
</template>

<script>
export default {
  name: 'SearchForm',
  props: {
    // 表单数据对象
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
    // 是否行内布局
    inline: {
      type: Boolean,
      default: true
    },
    // 标签宽度
    labelWidth: {
      type: String,
      default: '80px'
    },
    // 搜索按钮loading状态
    loading: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    // 搜索
    handleSearch() {
      this.$refs.searchForm.validate((valid) => {
        if (valid) {
          this.$emit('search', { ...this.form })
        }
      })
    },
    // 重置
    handleReset() {
      this.$refs.searchForm.resetFields()
      // 重置为初始值
      Object.keys(this.form).forEach(key => {
        this.form[key] = this.$options.propsData.form[key] || ''
      })
      this.$emit('reset')
    }
  }
}
</script>

<style scoped>
.search-form {
  width: 100%;
}

.search-buttons {
  margin-left: 20px;
}
</style>
