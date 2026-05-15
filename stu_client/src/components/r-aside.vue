<template>
  <div class="aside-container">
    <el-aside width="220px" class="custom-aside">
      <!-- 系统Logo -->
      <div class="aside-logo">
        <i class="el-icon-s-home"></i>
        <span>智能学生管理系统</span>
      </div>

      <el-menu
        router
        :default-active="$route.path"
        class="custom-menu"
        background-color="transparent"
        text-color="#4B5563"
        active-text-color="#3B82F6"
      >
        <div v-for="(item, index) in filteredRoutes" :key="index">
          <div v-for="child in item.filteredChildren" :key="child.path">
            <!-- 有子菜单的渲染成二级菜单 -->
            <el-submenu v-if="child.filteredChildren && child.filteredChildren.length > 0" :index="child.path">
              <template slot="title">
                <i :class="getMenuIcon(child.name)"></i>
                <span>{{ child.name }}</span>
              </template>
              <el-menu-item v-for="grandChild in child.filteredChildren" :key="grandChild.path" :index="grandChild.path">
                <i class="el-icon-dot"></i>
                <span>{{ grandChild.name }}</span>
              </el-menu-item>
            </el-submenu>
            <!-- 没有子菜单的直接渲染成一级菜单 -->
            <el-menu-item v-else :index="child.path">
              <i :class="getMenuIcon(child.name)"></i>
              <span>{{ child.name }}</span>
            </el-menu-item>
          </div>
        </div>
      </el-menu>
    </el-aside>
  </div>
</template>

<script>
export default {
  name: "r-aside",
  data() {
    return {
      type: null
    }
  },
  computed: {
    filteredRoutes() {
      // 确保type有值，优先从sessionStorage获取
      const currentType = this.type || sessionStorage.getItem("type")
      if (!currentType) return []

      // 根据type匹配对应的路由name
      const routeNameMap = {
        'student': 'student',
        'teacher': 'teacher',
        'admin': 'admin',
        '1': 'student',
        '2': 'teacher',
        '3': 'admin'
      }
      const routeName = routeNameMap[currentType] || 'admin'

      return this.$router.options.routes.filter(item => item.name === routeName).map(route => ({
        ...route,
        filteredChildren: route.children
          // 过滤隐藏的和没有权限的子路由
          .filter(child => !child.meta?.hidden && this.hasPermission(child, currentType))
          .map(child => ({
            ...child,
            filteredChildren: child.children
              ? child.children.filter(grandChild => !grandChild.meta?.hidden && this.hasPermission(grandChild, currentType))
              : []
          }))
          // 保留：要么有可访问的子菜单，要么本身是叶子节点菜单
          .filter(child => {
            // 如果本身没有children，直接保留
            if (!child.children) return true
            // 如果有children，但是过滤后还有子菜单，保留
            return child.filteredChildren.length > 0
          })
      }))
    }
  },
  methods: {
    // 根据菜单名称获取对应的图标
    getMenuIcon(menuName) {
      const iconMap = {
        // 管理员菜单
        '系统配置': 'el-icon-setting',
        '首页': 'el-icon-s-home',
        '学生管理': 'el-icon-user',
        '教师管理': 'el-icon-s-custom',
        '课程管理': 'el-icon-s-management',
        '开课表管理': 'el-icon-date',
        '学生成绩管理': 'el-icon-s-data',
        '消息管理': 'el-icon-message',
        '数据统计': 'el-icon-s-marketing',
        '修改密码': 'el-icon-key',
        // 教师菜单
        '教师首页': 'el-icon-s-home',
        '课程设置': 'el-icon-s-management',
        '我的课程': 'el-icon-notebook-2',
        '成绩管理': 'el-icon-s-order',
        '教师成绩管理': 'el-icon-s-order',
        '资源管理': 'el-icon-folder',
        '测验管理': 'el-icon-edit',
        '授课统计': 'el-icon-s-data',
        // 学生菜单
        '学生首页': 'el-icon-s-home',
        '选课管理': 'el-icon-date',
        '选课': 'el-icon-check',
        '查询课表': 'el-icon-notebook-1',
        '学生成绩管理': 'el-icon-s-order',
        '成绩查询': 'el-icon-tickets',
        '资源下载': 'el-icon-download',
        '测验列表': 'el-icon-document',
        '在线答题': 'el-icon-edit-outline',
        '测验结果': 'el-icon-view'
      }
      return iconMap[menuName] || 'el-icon-s-promotion'
    },
    // 判断用户是否有权限访问该路由，兼容两种角色格式
    hasPermission(route, userType) {
      // 如果路由没有配置roles，默认允许访问
      if (!route.meta || !route.meta.roles) {
        return true
      }

      const roles = route.meta.roles
      // 兼容数字和字符串类型的用户类型
      const userTypeStr = String(userType)

      // 匹配：比如用户type是'3'或者'admin'都能匹配管理员角色
      return roles.some(role => {
        const roleStr = String(role)
        return roleStr === userTypeStr ||
               (roleStr === '1' && userTypeStr === 'student') ||
               (roleStr === '2' && userTypeStr === 'teacher') ||
               (roleStr === '3' && userTypeStr === 'admin') ||
               (roleStr === 'student' && userTypeStr === '1') ||
               (roleStr === 'teacher' && userTypeStr === '2') ||
               (roleStr === 'admin' && userTypeStr === '3')
      })
    }
  },
  created() {
    this.type = sessionStorage.getItem("type")
  }
}
</script>

<style scoped>
.aside-container {
  height: 100%;
}
.custom-aside {
  height: 100%;
  background: linear-gradient(180deg, #F8FAFC 0%, #EEF2F7 100%);
  border-right: 1px solid #E5E7EB;
  overflow: hidden;
}
.aside-logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  font-weight: 600;
  font-size: 16px;
  letter-spacing: 1px;
}
.aside-logo i {
  font-size: 24px;
  margin-right: 8px;
}
.custom-menu {
  border: none;
  padding: 10px 0;
  background: transparent;
}
::v-deep .el-menu-item {
  height: 50px;
  line-height: 50px;
  margin: 4px 10px;
  border-radius: 8px;
  transition: all 0.3s ease;
}
::v-deep .el-menu-item:hover {
  background: rgba(59, 130, 246, 0.1);
  color: #3B82F6;
  transform: translateX(3px);
}
::v-deep .el-menu-item.is-active {
  background: linear-gradient(135deg, #3B82F6 0%, #2563EB 100%);
  color: #fff !important;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.3);
}
::v-deep .el-submenu__title {
  height: 50px;
  line-height: 50px;
  margin: 4px 10px;
  border-radius: 8px;
  transition: all 0.3s ease;
}
::v-deep .el-submenu__title:hover {
  background: rgba(59, 130, 246, 0.1);
  color: #3B82F6;
}
::v-deep .el-submenu.is-active .el-submenu__title {
  background: rgba(59, 130, 246, 0.15);
  color: #3B82F6;
}
::v-deep .el-menu .el-menu-item [class^=el-icon-],
::v-deep .el-menu .el-submenu__title [class^=el-icon-] {
  margin-right: 8px;
  width: 24px;
  text-align: center;
  font-size: 18px;
  vertical-align: middle;
}
/* 二级菜单样式 */
::v-deep .el-menu--inline {
  background: transparent;
  padding-left: 10px !important;
}
::v-deep .el-menu--inline .el-menu-item {
  height: 45px;
  line-height: 45px;
  font-size: 13px;
}
::v-deep .el-menu--inline .el-menu-item i {
  font-size: 10px;
}
</style>