import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/lib/constants.ts'
import { useAuth, type LoginPageState } from './AuthContext.ts'
import styles from './AuthPage.module.css'
import * as authApi from './authApi.ts'
import { FormField } from './FormField.tsx'

type Errors = { email?: string; password?: string }

// W-01 ログイン。「ゲストとして試す」で、登録せずにサンプル入りのボードを試せる（要件 U-06）
export function LoginPage() {
  const { loggedIn } = useAuth()
  // 前の画面からのお知らせ（セッション切れなど）
  const notice = (useLocation().state as LoginPageState | null)?.notice

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [alert, setAlert] = useState<string | null>(null)
  const [busy, setBusy] = useState(false) // 通信中はボタンを押せなくする

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault() // ブラウザの標準の送信（ページの再読み込み）を止める
    setAlert(null)

    // 画面側のチェックは「空でないか」だけ（API設計書 A-02）
    const nextErrors: Errors = {}
    if (email.trim() === '') nextErrors.email = MESSAGES.required('メールアドレス')
    if (password === '') nextErrors.password = MESSAGES.required('パスワード')
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setBusy(true)
    try {
      loggedIn(await authApi.login({ email, password }))
    } catch (error) {
      setBusy(false)
      setPassword('') // 失敗したらパスワード欄を空にする（画面設計書 W-01）
      setAlert(error instanceof ApiError ? error.detail : MESSAGES.network)
    }
  }

  // A-03 ゲストとして試す
  async function handleGuest() {
    setAlert(null)
    setErrors({})
    setBusy(true)
    try {
      loggedIn(await authApi.guest())
    } catch (error) {
      setBusy(false)
      setAlert(error instanceof ApiError ? error.detail : MESSAGES.network)
    }
  }

  return (
    <div className={styles.wrap}>
      <form className={styles.card} onSubmit={handleSubmit} noValidate>
        <h1 className={styles.logo}>TaskBoard</h1>
        <p className={styles.sub}>ログインしてボードを開く</p>
        {notice && !alert && <div className={styles.notice}>{notice}</div>}
        {alert && (
          <div className={styles.alert} role="alert">
            {alert}
          </div>
        )}
        <FormField
          id="login-email"
          label="メールアドレス"
          type="email"
          autoComplete="username"
          placeholder="you@example.com"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errors.email}
        />
        <FormField
          id="login-password"
          label="パスワード"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
        />
        <button className="btn btn-primary btn-block" type="submit" disabled={busy}>
          ログイン
        </button>
        <div className={styles.divider}>または</div>
        <button className="btn btn-block" type="button" onClick={handleGuest} disabled={busy}>
          ゲストとして試す
        </button>
        <p className={styles.guestHelp}>登録なしで、サンプルデータ入りのボードを試せます</p>
        <div className={styles.foot}>
          アカウントをお持ちでない方は <Link to="/signup">新規登録はこちら</Link>
        </div>
      </form>
    </div>
  )
}
