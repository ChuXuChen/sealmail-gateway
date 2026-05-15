/**
 * 通用校验规则
 */

// 手机号校验
export function validatePhone(rule, value, callback) {
  const reg = /^1[3-9]\d{9}$/
  if (value && !reg.test(value)) {
    callback(new Error('请输入正确的手机号'))
  } else {
    callback()
  }
}

// 邮箱校验
export function validateEmail(rule, value, callback) {
  const reg = /^([a-zA-Z0-9_-])+@([a-zA-Z0-9_-])+(.[a-zA-Z0-9_-])+/
  if (value && !reg.test(value)) {
    callback(new Error('请输入正确的邮箱地址'))
  } else {
    callback()
  }
}

// 身份证号校验
export function validateIdCard(rule, value, callback) {
  const reg = /(^\d{18}$)|(^\d{17}(\d|X|x)$)/
  if (value && !reg.test(value)) {
    callback(new Error('请输入正确的身份证号'))
  } else {
    callback()
  }
}

// 数字校验（正整数）
export function validatePositiveInteger(rule, value, callback) {
  const reg = /^[1-9]\d*$/
  if (value && !reg.test(value)) {
    callback(new Error('请输入正整数'))
  } else {
    callback()
  }
}

// 数字校验（非负整数）
export function validateNonNegativeInteger(rule, value, callback) {
  const reg = /^\d+$/
  if (value && !reg.test(value)) {
    callback(new Error('请输入非负整数'))
  } else {
    callback()
  }
}

// 金额校验（保留两位小数）
export function validateMoney(rule, value, callback) {
  const reg = /(^[1-9]([0-9]+)?(\.[0-9]{1,2})?$)|(^(0){1}$)|(^[0-9]\.[0-9]([0-9])?$)/
  if (value && !reg.test(value)) {
    callback(new Error('请输入正确的金额，最多保留两位小数'))
  } else {
    callback()
  }
}

// 密码校验（6-20位，包含字母和数字）
export function validatePassword(rule, value, callback) {
  const reg = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{6,20}$/
  if (value && !reg.test(value)) {
    callback(new Error('密码长度为6-20位，必须包含字母和数字'))
  } else {
    callback()
  }
}

// 中文校验
export function validateChinese(rule, value, callback) {
  const reg = /^[\u4e00-\u9fa5]+$/
  if (value && !reg.test(value)) {
    callback(new Error('请输入中文'))
  } else {
    callback()
  }
}

// 车牌号校验
export function validateCarNumber(rule, value, callback) {
  const reg = /^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-HJ-NP-Z][A-HJ-NP-Z0-9]{4}[A-HJ-NP-Z0-9挂学警港澳]$/
  if (value && !reg.test(value)) {
    callback(new Error('请输入正确的车牌号'))
  } else {
    callback()
  }
}

// URL校验
export function validateUrl(rule, value, callback) {
  const reg = /^https?:\/\/([\w-]+\.)+[\w-]+(\/[\w-./?%&=]*)?$/
  if (value && !reg.test(value)) {
    callback(new Error('请输入正确的URL地址'))
  } else {
    callback()
  }
}
