<template>
  <el-dialog
    :title="title"
    :visible.sync="visibleDialog"
    :width="width"
    :top="top"
    :modal="modal"
    :close-on-click-modal="closeOnClickModal"
    :close-on-press-escape="closeOnPressEscape"
    :show-close="showClose"
    :custom-class="customClass"
    @open="handleOpen"
    @close="handleClose"
  >
    <!-- 内容区域 -->
    <div class="dialog-content">
      <slot></slot>
    </div>

    <!-- 底部按钮区域 -->
    <span slot="footer" class="dialog-footer" v-if="showFooter">
      <slot name="footer">
        <el-button @click="handleCancel" :loading="cancelLoading">{{ cancelText }}</el-button>
        <el-button type="primary" @click="handleConfirm" :loading="confirmLoading">{{ confirmText }}</el-button>
      </slot>
    </span>
  </el-dialog>
</template>

<script>
export default {
  name: 'CommonDialog',
  props: {
    // 弹窗是否显示
    visible: {
      type: Boolean,
      default: false
    },
    // 弹窗标题
    title: {
      type: String,
      default: '提示'
    },
    // 弹窗宽度
    width: {
      type: String,
      default: '500px'
    },
    // 弹窗距离顶部距离
    top: {
      type: String,
      default: '15vh'
    },
    // 是否显示遮罩
    modal: {
      type: Boolean,
      default: true
    },
    // 点击遮罩是否关闭弹窗
    closeOnClickModal: {
      type: Boolean,
      default: false
    },
    // 按下ESC是否关闭弹窗
    closeOnPressEscape: {
      type: Boolean,
      default: true
    },
    // 是否显示关闭按钮
    showClose: {
      type: Boolean,
      default: true
    },
    // 自定义类名
    customClass: {
      type: String,
      default: ''
    },
    // 是否显示底部按钮
    showFooter: {
      type: Boolean,
      default: true
    },
    // 取消按钮文字
    cancelText: {
      type: String,
      default: '取消'
    },
    // 确认按钮文字
    confirmText: {
      type: String,
      default: '确认'
    },
    // 取消按钮loading状态
    cancelLoading: {
      type: Boolean,
      default: false
    },
    // 确认按钮loading状态
    confirmLoading: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    visibleDialog: {
      get() {
        return this.visible
      },
      set(val) {
        this.$emit('update:visible', val)
      }
    }
  },
  methods: {
    handleOpen() {
      this.$emit('open')
    },
    handleClose() {
      this.$emit('close')
    },
    handleCancel() {
      this.$emit('cancel')
      this.visibleDialog = false
    },
    handleConfirm() {
      this.$emit('confirm')
    }
  }
}
</script>

<style scoped>
.dialog-content {
  max-height: 60vh;
  overflow-y: auto;
  padding-right: 10px;
  margin-right: -10px;
}
</style>
