import Vue from 'vue';
import VueRouter from 'vue-router';
// 首屏必要组件同步引入
import login from '../views/login/index.vue';
import admin from '../views/Admin/index.vue';
import teacher from "@/views/Teacher/index";
import student from "@/views/Student/index";

// 首页同步引入，加快加载速度
import adminHome from '@/views/Admin/home'
import teacherHome from '@/views/Teacher/home'
import studentHome from '@/views/Student/home'
const studentManage = () => import('@/views/Admin/studentManage/index')
const addStudent = () => import('@/views/Admin/studentManage/addStudent')
const editorStudent = () => import('@/views/Admin/studentManage/editorStudent')
const teacherManage = () => import('@/views/Admin/teacherManage/index')
const addTeacher = () => import('@/views/Admin/teacherManage/addTeacher')
const editorTeacher = () => import('@/views/Admin/teacherManage/editorTeacher')
const courseManage = () => import('@/views/Admin/courseManage/index')
const addCourse = () => import('@/views/Admin/courseManage/addCourse')
const queryStudent = () => import('@/views/Admin/studentManage/queryStudent')
const queryTeacher = () => import('@/views/Admin/teacherManage/queryTeacher')
const editorCourse = () => import('@/views/Admin/courseManage/editorCourse')
const courseList = () => import('@/views/Admin/courseManage/courseList')
const queryCourse = () => import('@/views/Admin/courseManage/queryCourse')
const offerCourse = () => import('@/views/Teacher/offerCourse')
const setCourse = () => import('@/views/Teacher/setCourse')
const myOfferCourse = () => import('@/views/Teacher/myOfferCourse')
const CourseTeacherManage = () => import('@/views/Admin/selectCourseManage/index')
const queryCourseTeacher = () => import('@/views/Admin/selectCourseManage/queryCourseTeacher')
const editorCourseTeacher = () => import('@/views/Admin/selectCourseManage/editorCourseTeacher')
const studentSelectCourseManage = () => import('@/views/Student/selectCourse/index')
const selectCourse = () => import('@/views/Student/selectCourse/selectCourse')
const querySelectedCourse = () => import('@/views/Student/selectCourse/querySelectedCourse')
const studentCourseGrade = () => import('@/views/Student/courseGrade/index')
const queryCourseGrade = () => import('@/views/Student/courseGrade/queryCourseGrade')
const queryGradeCourse = () => import('@/views/Admin/gradeCourseManage/queryGradeCourse')
const editorGradeCourse = () => import('@/views/Admin/gradeCourseManage/editorGradeCourse')
const teacherGradeCourseManage = () => import('@/views/Teacher/teacherGradeCourseManage/index')
const teacherQueryGradeCourse = () => import('@/views/Teacher/teacherGradeCourseManage/teacherQueryGradeCourse')
const teacherGradeCourseList = () => import('@/views/Teacher/teacherGradeCourseManage/teacherGradeCourseList')
const teacherEditorGradeCourse = () => import('@/views/Teacher/teacherGradeCourseManage/teacherEditorGradeCourse')
const updateInfo = () => import('@/components/updateInfo')
const sendMessage = () => import('@/views/Admin/messageManage/sendMessage')
const adminStatistics = () => import('@/views/Admin/statistics')
const teacherStatistics = () => import('@/views/Teacher/statistics')
const systemConfig = () => import('@/views/Admin/SystemConfig')

Vue.use(VueRouter)

const routes = [
  {
    // 随便定义的首页
    path: '/',
    name: 'index',
    component: login,
    redirect: '/login'
  },
  {
    // 登陆页
    path: '/login',
    name: 'login',
    component: login
  },
  // 公共修改密码页面 - 所有登录用户都可以访问
  {
    path: '/updateInfo',
    name: '修改密码',
    component: updateInfo,
    meta: {
      requireAuth: true,
      roles: ['1', '2', '3', 'student', 'teacher', 'admin']
    }
  },
  {
    // admin 的路由
    path: '/admin',
    name: 'admin',
    redirect: '/adminHome',
    component: admin,
    meta: {requireAuth: true, roles: ['3', 'admin']},
    children: [
      {
        path: '/adminHome',
        name: 'admin 主页',
        component: adminHome,
        meta: {requireAuth: true, roles: ['3', 'admin']}
      },
      {
        path: '/studentManage',
        name: '学生管理',
        component: studentManage,
        meta: {requireAuth: true, roles: ['3', 'admin']},
        children: [
          {
            path: '/addStudent',
            name: '添加学生',
            component: addStudent,
            meta: {requireAuth: true, roles: ['3', 'admin'], hidden: true}
          },
          {
            path: '/editorStudent',
            name: '编辑学生',
            component: editorStudent,
            meta: {requireAuth: true, hidden: true, roles: ['3', 'admin']}
          },
          {
            path: '/queryStudent',
            name: '学生列表',
            component: queryStudent,
            meta: {requireAuth: true, roles: ['3', 'admin']},
          }
        ]
      },
      {
        path: '/teacherManage',
        name: '教师管理',
        component: teacherManage,
        meta: {requireAuth: true, roles: ['3', 'admin']},
        children: [
          {
            path: '/addTeacher',
            name: '添加教师',
            component: addTeacher,
            meta: {requireAuth: true, roles: ['3', 'admin'], hidden: true}
          },
          {
            path: '/queryTeacher',
            name: '教师列表',
            component: queryTeacher,
            meta: {requireAuth: true, roles: ['3', 'admin']},
            children: [
            ]
          },
          {
            path: '/editorTeacher',
            name: '编辑教师',
            component: editorTeacher,
            meta: {requireAuth: true, hidden: true, roles: ['3', 'admin']}
          },
        ]
      },
      {
        path: '/courseManage',
        name: '课程管理',
        component: courseManage,
        meta: {requireAuth: true, roles: ['3', 'admin']},
        children: [
          {
            path: '/addCourse',
            name: '添加课程',
            component: addCourse,
            meta: {requireAuth: true, roles: ['3', 'admin'], hidden: true}
          },
          {
            path: '/queryCourse',
            name: '课程列表',
            component: queryCourse,
            meta: {requireAuth: true, roles: ['3', 'admin']},
            children: [
              {
                path: '/courseList',
                name: '课程列表',
                component: courseList,
                meta: {requireAuth: true, roles: ['3', 'admin']}
              },
            ]
          },
          {
            path: '/editorCourse',
            name: '编辑课程',
            component: editorCourse,
            meta: {requireAuth: true, hidden: true, roles: ['3', 'admin']}
          },
        ]
      },
      {
        path: '/CourseTeacher',
        name: '开课表管理',
        component: CourseTeacherManage,
        meta: {requireAuth: true, roles: ['3', 'admin']},
        children: [
          {
            path: '/addCourseTeacher',
            name: '添加开课',
            component: () => import('@/views/Admin/selectCourseManage/addCourseTeacher'),
            meta: {requireAuth: true, roles: ['3', 'admin'], hidden: true}
          },
          {
            path: '/queryCourseTeacher',
            name: '开课列表',
            component: queryCourseTeacher,
            meta: {requireAuth: true, roles: ['3', 'admin']},
          },
          {
            path: '/editorCourseTeacher',
            name: '编辑开课',
            component: editorCourseTeacher,
            meta: {requireAuth: true, hidden: true, roles: ['3', 'admin']}
          }

        ]
      },
      {
        name: '学生成绩管理',
        path: "/gradeCourseManage",
        component: studentManage,
        meta: {requireAuth: true, roles: ['3', 'admin']},
        children: [
          {
            path: '/queryGradeCourse',
            name: '学生成绩查询',
            component: queryGradeCourse,
            meta: {requireAuth: true, roles: ['3', 'admin']},
          },
          {
            path: '/editorGradeCourse',
            name: '编辑',
            component: editorGradeCourse,
            meta: {requireAuth: true, hidden: true, roles: ['3', 'admin']}
          }
        ]
      },
      {
        path: '/messageManage',
        name: '消息管理',
        component: () => import('@/views/Admin/messageManage/sendMessage'),
        meta: {requireAuth: true, roles: ['3', 'admin']},
        children: [
          {
            path: '/sendMessage',
            name: '发送全体消息',
            component: sendMessage,
            meta: {requireAuth: true, roles: ['3', 'admin']}
          }
        ]
      },
      {
        path: 'statistics',
        name: '数据统计',
        component: { render: (h) => h('router-view') },
        meta: {requireAuth: true, roles: ['3', 'admin']},
        children: [
          {
            path: '/admin/statistics',
            name: '统计概览',
            component: adminStatistics,
            meta: {requireAuth: true, roles: ['3', 'admin']}
          }
        ]
      },
      {
        path: '/admin/system/config',
        name: '系统配置',
        component: systemConfig,
        meta: {requireAuth: true, roles: ['3', 'admin']}
      },
    ]
  },
  {
    path: '/teacher',
    name: 'teacher',
    component: teacher,
    redirect: '/teacherHome',
    meta: {requireAuth: true, roles: ['2', 'teacher']},
    children: [
      {
        path: '/teacherHome',
        name: '教师主页',
        meta: {requireAuth: true, roles: ['2', 'teacher']},
        component: teacherHome
      },
      {
        path: '/courseManage',
        name: '课程设置',
        meta: {requireAuth: true, roles: ['2', 'teacher']},
        component: setCourse,
        children: [
          {
            path: '/myOfferCourse',
            name: '我开设的课程',
            component: myOfferCourse,
            meta: {requireAuth: true, roles: ['2', 'teacher']}
          },/*
          {
            path: '/offerCourse',
            name: '开设课程',
            component: offerCourse,
            meta: {requireAuth: true, roles: ['2', 'teacher']}
          },*/
        ]
      },
      {
        name: '教师成绩管理',
        path: '/teacherQueryGradeCourseManage',
        component: teacherGradeCourseManage,
        meta: {requireAuth: true, roles: ['2', 'teacher']},
        children: [
          {
            path: '/teacherQueryGradeCourseManage',
            name: '成绩管理',
            component: teacherQueryGradeCourse,
            meta: {requireAuth: true, roles: ['2', 'teacher']}
          },
          {
            path: '/teacherEditorGradeCourse',
            name: '编辑成绩',
            component: teacherEditorGradeCourse,
            meta: {requireAuth: true, hidden: true, roles: ['2', 'teacher']}
          }
        ]
      },
      {
        path: '/teacherResourceManage',
        name: '资源管理',
        component: () => import('@/views/Teacher/resourceManage'),
        meta: {requireAuth: true, roles: ['2', 'teacher']},
        children: [
          {
            path: '/teacherResourceManage',
            name: '资源管理',
            component: () => import('@/views/Teacher/resourceManage'),
            meta: {requireAuth: true, roles: ['2', 'teacher']}
          }
        ]
      },
      {
        path: '/quizManage',
        name: '测验管理',
        component: () => import('@/views/Teacher/quizManage'),
        meta: {requireAuth: true, roles: ['2', 'teacher']},
        children: [
          {
            path: '/quizManage',
            name: '测验管理',
            component: () => import('@/views/Teacher/quizManage'),
            meta: {requireAuth: true, roles: ['2', 'teacher']}
          }
        ]
      },
      {
        path: 'statistics',
        name: '数据统计',
        component: { render: (h) => h('router-view') },
        meta: {requireAuth: true, roles: ['2', 'teacher']},
        children: [
          {
            path: '/teacher/statistics',
            name: '授课统计',
            component: teacherStatistics,
            meta: {requireAuth: true, roles: ['2', 'teacher']}
          }
        ]
      }
    ]
  },
  {
    path: '/student',
    name: 'student',
    component: student,
    redirect: '/studentHome',
    meta: {requireAuth: true, roles: ['1', 'student']},
    children: [
      {
        path: '/studentHome',
        name: '学生主页',
        component: studentHome,
        meta: {requireAuth: true, roles: ['1', 'student']}
      },
      {
        path: '/studentSelectCourseManage',
        name: '选课管理',
        component: studentSelectCourseManage,
        meta: {requireAuth: true, roles: ['1', 'student']},
        children: [
          {
            path: '/studentSelectCourse',
            name: '选课',
            component: selectCourse,
            meta: {requireAuth: true, roles: ['1', 'student']}
          },
          {
            path: '/querySelectedCourse',
            name: '查询课表',
            component: querySelectedCourse,
            meta: {requireAuth: true, roles: ['1', 'student']}
          }
        ]
      },
      {
        path: '/courseGrade',
        name: '学生成绩管理',
        component: studentCourseGrade,
        meta: {requireAuth: true, roles: ['1', 'student']},
        children: [
          {
            path: '/queryCourseGrade',
            name: '成绩查询',
            component: queryCourseGrade,
            meta: {requireAuth: true, roles: ['1', 'student']}
          },
        ]
      },
      {
        path: '/studentResourceDownload',
        name: '资源下载',
        component: () => import('@/views/Student/resourceDownload'),
        meta: {requireAuth: true, roles: ['1', 'student']},
        children: [
          {
            path: '/studentResourceDownload',
            name: '资源下载',
            component: () => import('@/views/Student/resourceDownload'),
            meta: {requireAuth: true, roles: ['1', 'student']}
          }
        ]
      },
      {
        path: '/quizList',
        name: '测验列表',
        component: () => import('@/views/Student/quizList'),
        meta: {requireAuth: true, roles: ['1', 'student']},
        children: [
          {
            path: '/quizList',
            name: '测验列表',
            component: () => import('@/views/Student/quizList'),
            meta: {requireAuth: true, roles: ['1', 'student']}
          }
        ]
      },
      {
        path: '/quizTaking',
        name: '在线答题',
        component: () => import('@/views/Student/quizTaking'),
        meta: {requireAuth: true, hidden: true, roles: ['1', 'student']}
      },
      {
        path: '/quizResult',
        name: '测验结果',
        component: () => import('@/views/Student/quizResult'),
        meta: {requireAuth: true, hidden: true, roles: ['1', 'student']}
      }
    ]
  }
]

const router = new VueRouter({
  mode: 'history',
  base: process.env.BASE_URL,
  routes
})

export default router

/*
  session 设置：
    1. token
    2. name
    3. type
    4. tid
    5. sid
    5. 系统信息 info
 */