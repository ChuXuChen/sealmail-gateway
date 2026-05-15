<template>
  <div class="common-table">
    <!-- 表格顶部操作栏 -->
    <div class="table-toolbar flex-between mb-15" v-if="showToolbar">
      <div class="toolbar-left">
        <slot name="toolbar-left"></slot>
      </div>
      <div class="toolbar-right flex">
        <el-button
          v-if="showExport"
          type="primary"
          icon="el-icon-download"
          size="small"
          @click="handleExport"
          :loading="exportLoading"
        >
          导出数据
        </el-button>
        <slot name="toolbar-right"></slot>
      </div>
    </div>

    <!-- 表格 -->
    <el-table
      ref="table"
      :data="tableData"
      :border="border"
      :stripe="stripe"
      :height="height"
      :max-height="maxHeight"
      :loading="loading"
      :row-key="rowKey"
      :highlight-current-row="highlightCurrentRow"
      :default-sort="defaultSort"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
      @row-click="handleRowClick"
    >
      <!-- 多选框列 -->
      <el-table-column
        v-if="showSelection"
        type="selection"
        width="55"
        align="center"
        :selectable="selectable"
      ></el-table-column>

      <!-- 序号列 -->
      <el-table-column
        v-if="showIndex"
        type="index"
        label="序号"
        width="60"
        align="center"
        :index="indexMethod"
      ></el-table-column>

      <!-- 动态列 -->
      <el-table-column
        v-for="column in columns"
        :key="column.prop"
        :prop="column.prop"
        :label="column.label"
        :width="column.width"
        :min-width="column.minWidth"
        :align="column.align || 'center'"
        :sortable="column.sortable"
        :fixed="column.fixed"
        :formatter="column.formatter"
        :show-overflow-tooltip="column.showOverflowTooltip !== false"
      >
        <template slot-scope="scope">
          <!-- 自定义列内容 -->
          <slot v-if="$scopedSlots[column.prop]" :name="column.prop" :row="scope.row">
          </slot>
          <!-- 默认显示 -->
          <span v-else>
            {{ scope.row[column.prop] }}
          </span>
        </template>
      </el-table-column>

      <!-- 操作列 -->
      <el-table-column
        v-if="showOperation"
        label="操作"
        :width="operationWidth || 180"
        align="center"
        fixed="right"
      >
        <template slot-scope="scope">
          <slot name="operation" :row="scope.row"></slot>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-container flex-end mt-15" v-if="showPagination">
      <el-pagination
        :current-page.sync="pagination.page"
        :page-size.sync="pagination.size"
        :total="pagination.total"
        :page-sizes="pageSizes"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      ></el-pagination>
    </div>
  </div>
</template>

<script>
import * as XLSX from 'xlsx'

export default {
  name: 'CommonTable',
  props: {
    // 表格数据
    tableData: {
      type: Array,
      default: () => []
    },
    // 列配置
    columns: {
      type: Array,
      required: true,
      default: () => []
    },
    // 是否显示边框
    border: {
      type: Boolean,
      default: true
    },
    // 是否显示斑马纹
    stripe: {
      type: Boolean,
      default: true
    },
    // 表格高度
    height: [String, Number],
    // 表格最大高度
    maxHeight: [String, Number],
    // 是否加载中
    loading: {
      type: Boolean,
      default: false
    },
    // 行key
    rowKey: {
      type: String,
      default: 'id'
    },
    // 是否高亮当前行
    highlightCurrentRow: {
      type: Boolean,
      default: false
    },
    // 默认排序
    defaultSort: {
      type: Object,
      default: () => ({})
    },
    // 是否显示多选框
    showSelection: {
      type: Boolean,
      default: false
    },
    // 多选可选项方法
    selectable: {
      type: Function,
      default: () => true
    },
    // 是否显示序号
    showIndex: {
      type: Boolean,
      default: true
    },
    // 是否显示操作列
    showOperation: {
      type: Boolean,
      default: true
    },
    // 操作列宽度
    operationWidth: {
      type: [String, Number],
      default: 180
    },
    // 是否显示顶部工具栏
    showToolbar: {
      type: Boolean,
      default: true
    },
    // 是否显示导出按钮
    showExport: {
      type: Boolean,
      default: false
    },
    // 导出按钮loading状态
    exportLoading: {
      type: Boolean,
      default: false
    },
    // 导出文件名
    exportFileName: {
      type: String,
      default: '导出数据'
    },
    // 是否显示分页
    showPagination: {
      type: Boolean,
      default: true
    },
    // 分页配置
    pagination: {
      type: Object,
      required: true,
      default: () => ({
        page: 1,
        size: 10,
        total: 0
      })
    },
    // 每页条数选项
    pageSizes: {
      type: Array,
      default: () => [10, 20, 50, 100]
    }
  },
  methods: {
    // 序号计算
    indexMethod(index) {
      return (this.pagination.page - 1) * this.pagination.size + index + 1
    },
    // 多选变化
    handleSelectionChange(selection) {
      this.$emit('selection-change', selection)
    },
    // 排序变化
    handleSortChange(sort) {
      this.$emit('sort-change', sort)
    },
    // 行点击
    handleRowClick(row, column, event) {
      this.$emit('row-click', row, column, event)
    },
    // 每页条数变化
    handleSizeChange(size) {
      this.$emit('size-change', size)
    },
    // 页码变化
    handlePageChange(page) {
      this.$emit('page-change', page)
    },
    // 导出数据
    handleExport() {
      if (this.tableData.length === 0) {
        this.$message.warning('没有数据可导出')
        return
      }
      this.$emit('export')
      // 默认导出当前页数据
      const exportData = this.tableData.map(item => {
        const obj = {}
        this.columns.forEach(col => {
          if (col.prop && !col.type) {
            obj[col.label] = item[col.prop] || ''
          }
        })
        return obj
      })
      const ws = XLSX.utils.json_to_sheet(exportData)
      const wb = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
      XLSX.writeFile(wb, `${this.exportFileName}_${new Date().getTime()}.xlsx`)
    },
    // 清空选中
    clearSelection() {
      this.$refs.table.clearSelection()
    },
    // 切换行选中
    toggleRowSelection(row, selected) {
      this.$refs.table.toggleRowSelection(row, selected)
    }
  }
}
</script>

<style scoped>
.common-table {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.pagination-container {
  padding: 10px 0;
}

/* 表格行过渡动画 */
::v-deep .el-table__row {
  transition: background-color 0.3s ease;
}

/* 操作栏按钮间隔 */
::v-deep .el-table__cell .el-button + .el-button {
  margin-left: 8px;
}

/* 表格阴影优化，去除边框 */
::v-deep .el-table {
  border: none;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

::v-deep .el-table th,
::v-deep .el-table tr {
  background-color: transparent;
}

::v-deep .el-table th.el-table__cell {
  background: linear-gradient(90deg, #f5f7fa 0%, #eef2f7 100%);
}
</style>
