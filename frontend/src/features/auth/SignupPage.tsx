import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import { MESSAGES } from '../../shared/lib/constants.ts'
import { validateEmail, validatePassword, validatePasswordConfirm } from '../../shared/lib/validation.ts'
import { useAuth } from './AuthContext.ts'
import styles from './AuthPage.module.css'
import * as authApi from './authApi.ts'
import { FormField } from './FormField.tsx'

type Errors = { email?: string; password?: string; passwordConfirm?: string }

// W-02 ユーザー登録。成功したら自動でログインしてボードへ（要件 U-01）
export function SignupPage() {
  const { loggedIn } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [alert, setAlert] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setAlert(null)

    // 送信したときに入力チェックをして、エラーを入力欄の下に出す（詳細設計書 4.6）
    const nextErrors: Errors = {}
    const emailError = validateEmail(email)
    const passwordError = validatePassword(password)
    const confirmError = validatePasswordConfirm(password, passwordConfirm)
    if (emailError) nextErrors.email = emailError
    if (passwordError) nextErrors.password = passwordError
    if (confirmError) nextErrors.passwordConfirm = confirmError
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setBusy(true)
    try {
      // パスワード（確認）は送らない
      loggedIn(await authApi.signup({ email, password }))
    } catch (error) {
      setBusy(false)
      if (error instanceof ApiError && error.code === 'EMAIL_DUPLICATED') {
        setErrors({ email: error.detail })
      } else if (error instanceof ApiError && error.code === 'VALIDATION_ERROR') {
        // サーバーの入力チェックのエラーも、同じ場所（入力欄の下）に出す
        setErrors(fieldErrorsOf(error))
      } else {
        setAlert(error instanceof ApiError ? error.detail : MESSAGES.network)
      }
    }
  }

  return (
    <div className={styles.wrap}>
      <form className={styles.card} onSubmit={handleSubmit} noValidate>
        <h1 className={styles.logo}>TaskBoard</h1>
        <p className={styles.sub}>アカウントを作成</p>
        {alert && (
          <div className={styles.alert} role="alert">
            {alert}
          </div>
        )}
        <FormField
          id="signup-email"
          label="メールアドレス"
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errors.email}
        />
        <FormField
          id="signup-password"
          label="パスワード"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
          help="8〜64文字の半角英数字・記号"
        />
        <FormField
          id="signup-password-confirm"
          label="パスワード（確認）"
          type="password"
          autoComplete="new-password"
          value={passwordConfirm}
          onChange={(e) => setPasswordConfirm(e.target.value)}
          error={errors.passwordConfirm}
        />
        <button className="btn btn-primary btn-block" type="submit" disabled={busy}>
          登録する
        </button>
        <div className={styles.foot}>
          アカウントをお持ちの方は <Link to="/login">ログインはこちら</Link>
        </div>
      </form>
    </div>
  )
}
