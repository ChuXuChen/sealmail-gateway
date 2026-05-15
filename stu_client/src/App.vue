<template>
  <div id="app">
    <!-- 全局路由loading -->
    <div class="global-loading" v-if="isLoading">
      <div class="loading-spinner"></div>
      <p class="loading-text">加载中...</p>
    </div>
    <!-- 完全移除过渡动画，避免登录切换时的渲染开销 -->
    <router-view></router-view>
  </div>
</template>

<script>
export default {
  name: 'app',
  data() {
    return {
      isLoading: false
    }
  },
  mounted() {
    // 路由切换loading - 优化登录跳转白屏
    this.$router.beforeEach((to, from, next) => {
      this.isLoading = true
      // 登录跳转时保持loading，确保页面完全渲染
      this.loadingDelay = from.path === '/login' ? 200 : 0
      next()
    })
    this.$router.afterEach(() => {
      // 登录跳转时延迟隐藏loading，避免白屏
      setTimeout(() => {
        this.isLoading = false
      }, this.loadingDelay)
    })

    // 全局ESC快捷键关闭弹窗
    document.addEventListener('keydown', (e) => {
      // ESC键关闭弹窗
      if (e.key === 'Escape' || e.keyCode === 27) {
        // 查找所有打开的弹窗，关闭最上层的
        const dialogs = document.querySelectorAll('.el-dialog__wrapper:not([style*="display: none"])')
        if (dialogs.length > 0) {
          const topDialog = dialogs[dialogs.length - 1]
          const closeBtn = topDialog.querySelector('.el-dialog__headerbtn')
          if (closeBtn) {
            closeBtn.click()
            e.stopPropagation()
            e.preventDefault()
          }
        }
      }

      // Ctrl+Enter快捷键提交表单（在输入框中时）
      if ((e.ctrlKey || e.metaKey) && (e.key === 'Enter' || e.keyCode === 13)) {
        const activeElement = document.activeElement
        // 如果当前焦点在输入框或文本域中
        if (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA') {
          // 查找最近的表单提交按钮
          const form = activeElement.closest('form')
          if (form) {
            const submitBtn = form.querySelector('button[type="submit"], .el-button--primary')
            if (submitBtn && !submitBtn.disabled) {
              submitBtn.click()
              e.stopPropagation()
              e.preventDefault()
            }
          }
        }
      }
    })
  }
}
</script>

<style>

/* 全局样式重置 */
* {
  box-sizing: border-box;
}

html,
body,
#app {
  height: 100%;
  margin: 0;
  padding: 0;
  background: #f5f7fa;
}

/* 滚动条样式 */
::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-track {
  background: #f1f1f1;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb {
  background: #c1c1c1;
  border-radius: 3px;
}

::-webkit-scrollbar-thumb:hover {
  background: #a8a8a8;
}

/* 全局loading样式 */
.global-loading {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #4080ff;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loading-text {
  margin-top: 16px;
  color: #666;
  font-size: 14px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>

