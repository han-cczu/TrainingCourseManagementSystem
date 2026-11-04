import type { FormItemRule } from 'element-plus'

// 与后端 ValidationPatterns 保持一致（技术设计 §4.3）
export const USERNAME_PATTERN = /^[A-Za-z0-9_]{3,32}$/
export const PASSWORD_PATTERN = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/

export const usernameRules: FormItemRule[] = [
  { required: true, message: '请输入用户名', trigger: 'blur' },
  { pattern: USERNAME_PATTERN, message: '用户名需为 3-32 位字母、数字或下划线', trigger: 'blur' },
]

export const passwordRules: FormItemRule[] = [
  { required: true, message: '请输入密码', trigger: 'blur' },
  { pattern: PASSWORD_PATTERN, message: '密码需为 8-64 位，且同时包含字母和数字', trigger: 'blur' },
]

export const realNameRules: FormItemRule[] = [
  { required: true, message: '请输入姓名', trigger: 'blur' },
  { max: 32, message: '姓名最多 32 个字符', trigger: 'blur' },
]

export const departmentRules: FormItemRule[] = [{ max: 64, message: '部门最多 64 个字符', trigger: 'blur' }]

/** 确认密码：与 getPassword() 返回的值一致 */
export function confirmPasswordRules(getPassword: () => string): FormItemRule[] {
  return [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      trigger: 'blur',
      validator: (_rule, value: string, callback) =>
        value === getPassword() ? callback() : callback(new Error('两次输入的密码不一致')),
    },
  ]
}
