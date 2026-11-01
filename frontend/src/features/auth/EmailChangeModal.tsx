import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import { MESSAGES } from '../../shared/lib/constants.ts'
import { validateEmail } from '../../shared/lib/validation.ts'
import { Modal } from '../../shared/ui/Modal.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import styles from './AccountModal.module.css'
import { useAuth } from './AuthContext.ts'
import * as authApi from './authApi.ts'
import { FormField } from './FormField.tsx'

type Errors = { email?: string; currentPassword?: string }

// W-09 メールアドレスの変更（画面設計書 W-09、要件 U-12）
// 今のパスワードで本人か確かめてから変える。成功したら閉じて通知を出し、ヘッダーのメールアドレスも変わる
// ほかのブラウザ・端末はログアウトされ、この画面はログインしたまま（サーバーで行う。詳細設計書 3.8.9）
export function EmailChangeModal({ onClose }: { onClose: () => void }) {
  const { user, userUpdated } = useAuth()
  const { showToast, showApiError } = useToast()
  const [email, setEmail] = useState('')
  const [currentPassword, setCurrentPassword] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [busy, setBusy] = useState(false) // 通信中は閉じない・押せない
  const emailRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)

  // エラーが出たら、直す入力欄にカーソルを置く（メールアドレスが先）。
  // 通信中は入力欄を押せない状態にしているので、押せる状態に戻って画面が変わったあとで置く（退会 W-08 と同じ）
  useEffect(() => {
    if (errors.email) emailRef.current?.focus()
    else if (errors.currentPassword) passwordRef.current?.focus()
  }, [errors])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return

    const nextErrors: Errors = {}
    const emailError = validateEmail(email)
    if (emailError) nextErrors.email = emailError
    if (currentPassword === '') nextErrors.currentPassword = MESSAGES.required('今のパスワード')
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setBusy(true)
    try {
      // 前後の空白は取り除いて送る（サーバーは空白入りを形式の誤りにする）
      const updated = await authApi.changeEmail(email.trim(), currentPassword)
      userUpdated(updated)
      showToast(MESSAGES.emailChanged, 'success')
      onClose()
    } catch (e) {
      setBusy(false)
      if (!(e instanceof ApiError)) {
        showApiError(e)
        return
      }
      if (e.code === 'EMAIL_DUPLICATED') {
        setErrors({ email: e.detail })
      } else if (e.code === 'VALIDATION_ERROR') {
        // 今のパスワードが違うときは、パスワードを消して入れ直してもらう
        const fieldErrors = fieldErrorsOf(e)
        if (fieldErrors.currentPassword) setCurrentPassword('')
        setErrors({ email: fieldErrors.email, currentPassword: fieldErrors.currentPassword })
      } else if (e.code === 'RATE_LIMITED') {
        // 回数制限は、パスワード欄の下に出す（退会 W-08 と同じ）
        setCurrentPassword('')
        setErrors({ currentPassword: e.detail })
      } else {
        showApiError(e)
      }
    }
  }

  return (
    <Modal width={400} labelledBy="email-change-title" onRequestClose={() => !busy && onClose()}>
      <form onSubmit={handleSubmit} noValidate>
        <div className={styles.body}>
          <h3 id="email-change-title" className={styles.title}>
            メールアドレスの変更
          </h3>
          <p className="muted">
            今のメールアドレス：{user?.email}
            <br />
            変更すると、ほかのブラウザ・端末ではログアウトされます。
          </p>
          <FormField
            ref={emailRef}
            id="email-change-email"
            label="新しいメールアドレス"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={errors.email}
            disabled={busy}
            data-autofocus
          />
          <FormField
            ref={passwordRef}
            id="email-change-password"
            label="今のパスワード"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            error={errors.currentPassword}
            disabled={busy}
          />
        </div>
        <div className={styles.foot}>
          <button type="button" className="btn" onClick={onClose} disabled={busy}>
            キャンセル
          </button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            変更する
          </button>
        </div>
      </form>
    </Modal>
  )
}
