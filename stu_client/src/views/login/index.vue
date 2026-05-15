<template>
  <div class="login-container">
    <!-- 背景装饰 -->
    <div class="login-bg">
      <div class="bg-shape shape-1"></div>
      <div class="bg-shape shape-2"></div>
      <div class="bg-shape shape-3"></div>
      <div class="bg-shape shape-4"></div>
      <div class="bg-grid"></div>
    </div>

    <!-- 头部标题 -->
    <div class="login-header">
      <div class="header-content">
        <div class="header-icon-wrapper">
          <i class="el-icon-s-home header-icon"></i>
        </div>
        <h1 class="header-title text-gradient">智能学生管理系统</h1>
        <p class="header-subtitle">Smart Student Management System</p>
      </div>
    </div>

    <!-- 登录表单 -->
    <div class="login-main">
      <el-card class="login-card hover-lift" shadow="hover">
        <div class="card-header">
          <div class="title-decoration">
            <div class="decor-line"></div>
            <h2 class="card-title">欢迎登录</h2>
          </div>
          <p class="card-desc">请输入您的账号信息</p>
        </div>

        <el-form
          :model="loginForm"
          :rules="rules"
          ref="loginFormRef"
          label-width="80px"
          class="login-form"
        >
          <el-form-item label="账号" prop="id">
            <el-input
              v-model.number="loginForm.id"
              placeholder="请输入账号ID"
              prefix-icon="el-icon-user"
              size="large"
              clearable
              class="login-input"
            ></el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="loginForm.password"
              placeholder="请输入密码"
              show-password
              prefix-icon="el-icon-lock"
              size="large"
              clearable
              class="login-input"
            ></el-input>
          </el-form-item>

          <el-form-item label="身份" prop="type">
            <el-radio-group v-model="loginForm.type" size="medium" class="login-radio-group">
              <el-radio label="student" class="login-radio">
                <i class="el-icon-user"></i>
                <span>学生</span>
              </el-radio>
              <el-radio label="teacher" class="login-radio">
                <i class="el-icon-s-custom"></i>
                <span>教师</span>
              </el-radio>
              <el-radio label="admin" class="login-radio">
                <i class="el-icon-s-operation"></i>
                <span>管理员</span>
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item class="form-actions">
            <el-button
              type="primary"
              size="large"
              @click="handleLogin"
              :loading="loading"
              class="login-btn-primary"
            >
              <i v-if="!loading" class="el-icon-right"></i>
              {{ loading ? '登录中...' : '立即登录' }}
            </el-button>
            <el-button
              size="large"
              @click="handleReset"
              class="login-btn-reset"
            >
              <i class="el-icon-refresh"></i>
              重置表单
            </el-button>
          </el-form-item>
        </el-form>

        <!-- 底部小贴士 -->
        <div class="login-tips">
          <i class="el-icon-info"></i>
          <span>测试账号：学生2/123，教师4/123，管理员6/123</span>
        </div>
      </el-card>
    </div>

    <!-- 底部版权 -->
    <div class="login-footer">
      <p>© 2026 智能学生管理系统 All Rights Reserved | 毕业设计项目</p>
    </div>
  </div>
</template>
<script>
export default {
  name: 'Login',
  data() {
    return {
      loginForm: {
        id: null,
        password: null,
        type: null,
      },
      rules: {
        id: [
          { required: true, message: '请输入账号', trigger: 'blur' },
          { type: 'number', message: '账号必须为数字', trigger: 'blur' },
        ],
        password: [
          { required: true, message: '请输入密码', trigger: 'blur' },
          { min: 3, message: '密码长度不能少于3位', trigger: 'blur' }
        ],
        type: [
          { required: true, message: '请选择登录身份', trigger: 'change' }
        ],
      },
      loading: false
    };
  },
  mounted() {
    // 如果已经登录，跳转到首页
    if (this.$store.getters.token) {
      const userType = this.$store.getters.userType
      this.$router.push(`/${userType}`)
    }
  },
  methods: {
    async handleLogin() {
      this.$refs.loginFormRef.validate(async (valid) => {
        if (valid) {
          this.loading = true
          try {
            // 登录逻辑
            let loginUrl, userKey
            const { id, password, type } = this.loginForm

            if (type === 'student') {
              loginUrl = '/student/login'
              userKey = 'sid'
            } else {
              loginUrl = '/teacher/login'
              userKey = 'tid'
            }

            // 调用登录接口
            const loginRes = await this.$request.post(loginUrl, {
              [userKey]: id,
              password
            })

            if (loginRes === true) {
              // 暂停登录页动画，释放GPU资源，加快跳转
              const loginContainer = document.querySelector('.login-container')
              const bgShapes = document.querySelectorAll('.bg-shape')
              if (loginContainer) loginContainer.style.animationPlayState = 'paused'
              bgShapes.forEach(shape => shape.style.animationPlayState = 'paused')

              // 先保存必要的登录信息
              sessionStorage.setItem('token', 'true')
              sessionStorage.setItem('type', type)
              if (type === 'student') {
                sessionStorage.setItem('sid', id)
              } else {
                sessionStorage.setItem('tid', id)
              }

              try {
                // 并行获取用户信息和系统配置，确保跳转前所有必要数据都准备好
                const [userInfo, currentTerm, forbidCourseSelect] = await Promise.all([
                  // 获取用户信息，管理员账号在teacher表中
                  (async () => {
                    const userType = type === 'admin' ? 'teacher' : type
                    return this.$request.get(`/${userType}/findById/${id}`)
                  })(),
                  this.$request.get('/info/getCurrentTerm'),
                  this.$request.get('/info/getForbidCourseSelection')
                ])

                const userName = type === 'student' ? userInfo.sname : userInfo.tname

                // 校验管理员身份
                if (type === 'admin' && userName !== 'admin') {
                  this.$message.error('管理员登录失败，请检查登录类型')
                  sessionStorage.clear()
                  return
                }

                if (type === 'teacher' && userName === 'admin') {
                  this.$message.error('教师登录失败，请检查登录类型')
                  sessionStorage.clear()
                  return
                }

                // 保存到Vuex和sessionStorage
                await this.$store.dispatch('user/Login', {
                  token: 'true',
                  type: type,
                  id: id,
                  name: userName
                })

                sessionStorage.setItem('name', userName)
                sessionStorage.setItem('currentTerm', currentTerm)
                sessionStorage.setItem('ForbidCourseSelection', forbidCourseSelect)

                // 登录成功提示
                this.$message.success(`登录成功，欢迎 ${userName}!`)

                // 直接整页跳转，避开单页路由切换的渲染阻塞，完全消除白屏
                // 效果和手动刷新页面一样，sessionStorage里的登录状态不会丢失
                location.href = `/${type}`
              } catch (error) {
                console.error('登录数据加载失败:', error)
                this.$message.error('登录失败，请稍后重试')
                sessionStorage.clear()
              }
            } else {
              this.$message.error('登录失败，请检查账号密码')
            }
          } catch (error) {
            console.error('登录失败:', error)
            this.$message.error('登录失败，请稍后重试')
          } finally {
            this.loading = false
          }
        }
      })
    },

    handleReset() {
      this.$refs.loginFormRef.resetFields()
    }
  }
}
</script>

<style scoped>
.login-container {
  width: 100%;
  height: 100vh;
  position: relative;
  overflow: hidden;
  /* 更高级的渐变背景 */
  background: linear-gradient(-45deg, #667eea, #764ba2, #f093fb, #f5576c, #4facfe);
  background-size: 400% 400%;
  animation: gradientShift 15s ease infinite;
  will-change: background-position;
  transform: translateZ(0);
}

/* 背景装饰 */
.login-bg {
  position: absolute;
  width: 100%;
  height: 100%;
  top: 0;
  left: 0;
  overflow: hidden;
}

/* 网格背景 */
.bg-grid {
  position: absolute;
  width: 100%;
  height: 100%;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px);
  background-size: 50px 50px;
  animation: gridMove 20s linear infinite;
}

@keyframes gridMove {
  0% {
    transform: translate(0, 0);
  }
  100% {
    transform: translate(50px, 50px);
  }
}

.bg-shape {
  position: absolute;
  background: rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  animation: float 8s ease-in-out infinite;
  will-change: transform;
  transform: translateZ(0);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.shape-1 {
  width: 200px;
  height: 200px;
  top: 10%;
  left: 10%;
  border-radius: 30% 70% 70% 30% / 30% 30% 70% 70%;
  animation-delay: 0s;
  animation-duration: 12s;
}

.shape-2 {
  width: 300px;
  height: 300px;
  bottom: 15%;
  right: 15%;
  border-radius: 63% 37% 54% 46% / 55% 48% 52% 45%;
  animation-delay: 2s;
  animation-duration: 15s;
}

.shape-3 {
  width: 150px;
  height: 150px;
  bottom: 20%;
  left: 20%;
  border-radius: 50%;
  animation-delay: 4s;
  animation-duration: 10s;
}

.shape-4 {
  width: 180px;
  height: 180px;
  top: 40%;
  right: 10%;
  border-radius: 47% 53% 68% 32% / 37% 54% 46% 63%;
  animation-delay: 6s;
  animation-duration: 18s;
}

@keyframes float {
  0%, 100% {
    transform: translateY(0px) rotate(0deg);
  }
  25% {
    transform: translateY(-15px) rotate(90deg);
  }
  50% {
    transform: translateY(-25px) rotate(180deg);
  }
  75% {
    transform: translateY(-15px) rotate(270deg);
  }
}

/* 渐变背景动画 */
@keyframes gradientShift {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

/* 头部 */
.login-header {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  padding: 40px 50px;
  z-index: 10;
  animation: slideInDown 0.8s ease-out forwards;
}

@keyframes slideInDown {
  from {
    opacity: 0;
    transform: translateY(-30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.header-content {
  color: #fff;
}

.header-icon-wrapper {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 50px;
  height: 50px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(10px);
  border-radius: 50%;
  margin-right: 15px;
  vertical-align: middle;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.header-icon {
  font-size: 24px;
  color: #fff;
}

.header-title {
  display: inline-block;
  font-size: 36px;
  font-weight: 700;
  margin: 0;
  vertical-align: middle;
  letter-spacing: 2px;
}

.header-subtitle {
  font-size: 16px;
  margin: 8px 0 0 65px;
  opacity: 0.9;
  letter-spacing: 1px;
}

/* 主内容 */
.login-main {
  position: relative;
  z-index: 20;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  animation: fadeIn 0.8s ease-out 0.3s forwards;
  opacity: 0;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.login-card {
  width: 100%;
  max-width: 520px;
  border-radius: 20px;
  backdrop-filter: blur(20px);
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  overflow: hidden;
}

.login-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.card-header {
  text-align: center;
  padding: 30px 20px 20px;
  border-bottom: 1px solid #F3F4F6;
  margin-bottom: 30px;
}

.title-decoration {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8px;
}

.decor-line {
  width: 40px;
  height: 3px;
  border-radius: 2px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  margin: 0 10px;
}

.card-title {
  font-size: 30px;
  font-weight: 700;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
  margin: 0;
}

.card-desc {
  font-size: 14px;
  color: #6B7280;
  margin: 0;
}

.login-form {
  padding: 0 40px;
}

/* 输入框样式 */
.login-input {
  margin-bottom: 8px;
}

/* 单选按钮组样式 */
.login-radio-group {
  display: flex;
  justify-content: space-between;
  width: 100%;
}

.login-radio {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  transition: all 0.3s ease;
}

.login-radio:hover {
  transform: translateY(-2px);
}

.login-radio i {
  margin-right: 6px;
  font-size: 16px;
}

/* 按钮样式 */
.form-actions {
  margin-top: 30px;
}

.login-btn-primary {
  width: 100%;
  margin-bottom: 15px;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
}

.login-btn-primary i {
  margin-right: 8px;
}

.login-btn-reset {
  width: 100%;
  height: 48px;
  font-size: 16px;
}

.login-btn-reset i {
  margin-right: 8px;
}

/* 登录小贴士 */
.login-tips {
  margin-top: 20px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #EFF6FF 0%, #DBEAFE 100%);
  border-radius: 8px;
  font-size: 12px;
  color: #4B5563;
  display: flex;
  align-items: flex-start;
}

.login-tips i {
  color: #3B82F6;
  margin-right: 8px;
  margin-top: 1px;
  flex-shrink: 0;
}

.login-tips span {
  flex: 1;
  line-height: 1.5;
}

/* 底部 */
.login-footer {
  position: absolute;
  bottom: 20px;
  left: 0;
  width: 100%;
  text-align: center;
  color: rgba(255, 255, 255, 0.8);
  font-size: 13px;
  z-index: 10;
  animation: fadeIn 0.8s ease-out 0.6s forwards;
  opacity: 0;
}

/* 响应式适配 */
@media screen and (max-width: 768px) {
  .login-header {
    padding: 20px;
  }

  .header-icon-wrapper {
    width: 40px;
    height: 40px;
  }

  .header-icon {
    font-size: 20px;
  }

  .header-title {
    font-size: 24px;
  }

  .header-subtitle {
    font-size: 12px;
    margin-left: 55px;
  }

  .login-card {
    max-width: 100%;
  }

  .card-title {
    font-size: 24px;
  }

  .login-form {
    padding: 0 20px;
  }

  .login-radio-group {
    flex-direction: column;
    gap: 10px;
  }
}
</style>