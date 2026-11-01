import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import { MESSAGES } from '../../shared/lib/constants.ts'
import { validatePassword, validatePasswordConfirm } from '../../shared/lib/validation.ts'
import { Modal } from '../../shared/ui/Modal.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import styles from './AccountModal.module.css'
import { useAuth } from './AuthContext.ts'
import * as authApi from './authApi.ts'
import { FormField } from './FormField.tsx'

type Errors = { currentPassword?: string; newPassword?: string; newPasswordConfirm?: string }

// W-10 パスワードの変更（画面設計書 W-10、要件 U-13）
// 今のパスワードで本人か確かめてから変える。成功したら閉じて通知を出す
// ほかのブラウザ・端末はログアウトされ、この画面はログインしたまま（サーバーで行う。詳細設計書 3.8.9）
export function PasswordChangeModal({ onClose }: { onClose: () => void }) {
  const { user } = useAuth()
  const { showToast, showApiError } = useToast()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [errors, setErrors] = useState<Errors>({})
  const [busy, setBusy] = useState(false) // 通信中は閉じない・押せない
  const currentRef = useRef<HTMLInputElement>(null)
  const newRef = useRef<HTMLInputElement>(null)
  const confirmRef = useRef<HTMLInputElement>(null)

  // エラーが出たら、上から順に最初のエラーの入力欄にカーソルを置く（メールアドレスの変更 W-09 と同じ）
  useEffect(() => {
    if (errors.currentPassword) currentRef.current?.focus()
    else if (errors.newPassword) newRef.current?.focus()
    else if (errors.newPasswordConfirm) confirmRef.current?.focus()
  }, [errors])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) return

    const nextErrors: Errors = {}
    if (currentPassword === '') nextErrors.currentPassword = MESSAGES.required('今のパスワード')
    const newError = validatePassword(newPassword, '新しいパスワード')
    if (newError) nextErrors.newPassword = newError
    const confirmError = validatePasswordConfirm(newPassword, newPasswordConfirm, '新しいパスワード（確認）')
    if (confirmError) nextErrors.newPasswordConfirm = confirmError
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setBusy(true)
    try {
      // 新しいパスワード（確認）は送らない（登録と同じ）
      await authApi.changePassword(currentPassword, newPassword)
      showToast(MESSAGES.passwordChanged, 'success')
      onClose()
    } catch (e) {
      setBusy(false)
      if (!(e instanceof ApiError)) {
        showApiError(e)
        return
      }
      if (e.code === 'VALIDATION_ERROR') {
        const fieldErrors = fieldErrorsOf(e)
        // 今のパスワードが違うときは、パスワードを消して入れ直してもらう（新しいパスワードは残す）
        if (fieldErrors.currentPassword) setCurrentPassword('')
        setErrors({ currentPassword: fieldErrors.currentPassword, newPassword: fieldErrors.newPassword })
      } else if (e.code === 'RATE_LIMITED') {
        setCurrentPassword('')
        setErrors({ currentPassword: e.detail })
      } else {
        showApiError(e)
      }
    }
  }

  return (
    <Modal width={400} labelledBy="password-change-title" onRequestClose={() => !busy && onClose()}>
      <form onSubmit={handleSubmit} noValidate>
        <div className={styles.body}>
          <h3 id="password-change-title" className={styles.title}>
            パスワードの変更
          </h3>
          <p className="muted">変更すると、ほかのブラウザ・端末ではログアウトされます。</p>
          {/* 見えない入力欄。パスワードの管理ツール（ブラウザの保存機能など）が、どのアカウントのパスワードを
              書き換えるのかを知るために使う */}
          <input type="email" autoComplete="username" value={user?.email ?? ''} readOnly hidden />
          <FormField
            ref={currentRef}
            id="password-change-current"
            label="今のパスワード"
            type="password"
            autoComplete="current-password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            error={errors.currentPassword}
            disabled={busy}
            data-autofocus
          />
          <FormField
            ref={newRef}
            id="password-change-new"
            label="新しいパスワード"
            type="password"
            autoComplete="new-password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            error={errors.newPassword}
            help="8〜64文字の半角英数字・記号"
            disabled={busy}
          />
          <FormField
            ref={confirmRef}
            id="password-change-confirm"
            label="新しいパスワード（確認）"
            type="password"
            autoComplete="new-password"
            value={newPasswordConfirm}
            onChange={(e) => setNewPasswordConfirm(e.target.value)}
            error={errors.newPasswordConfirm}
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
