import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import { Modal } from '../../shared/ui/Modal.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { useAuth } from './AuthContext.ts'
import * as authApi from './authApi.ts'
import { FormField } from './FormField.tsx'
import styles from './AccountModal.module.css'

// W-08 退会の確認（画面設計書 W-08、要件 U-11）
// パスワードで本人か確かめてから、アカウントとすべてのデータを削除する
// 最初にカーソルがあるのは「キャンセル」（押し間違いで消さないため。W-06 と同じ）
export function WithdrawModal({ onClose }: { onClose: () => void }) {
  const { withdrawn } = useAuth()
  const { showApiError } = useToast()
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false) // 通信中は閉じない・押せない
  const passwordRef = useRef<HTMLInputElement>(null)

  // エラーが出たら、入力し直せるようにパスワード欄にカーソルを置く
  // （通信中は入力欄を押せない状態にしているので、押せる状態に戻って画面が変わったあとで置く）
  useEffect(() => {
    if (error) passwordRef.current?.focus()
  }, [error])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault() // ブラウザの標準の送信（ページの再読み込み）を止める
    if (password === '' || busy) return
    setBusy(true)
    setError(null)
    try {
      await authApi.withdraw(password)
      withdrawn() // ログイン画面へ移る（このモーダルも一緒に閉じる）
    } catch (e) {
      setBusy(false)
      setPassword('')
      // パスワード違い（400）と回数制限（429）は、入力欄の下に出す。それ以外は通知
      if (e instanceof ApiError && (e.code === 'VALIDATION_ERROR' || e.code === 'RATE_LIMITED')) {
        setError(fieldErrorsOf(e).password ?? e.detail)
      } else {
        showApiError(e)
      }
    }
  }

  return (
    <Modal width={400} labelledBy="withdraw-title" onRequestClose={() => !busy && onClose()}>
      <form onSubmit={handleSubmit} noValidate>
        <div className={styles.body}>
          <h3 id="withdraw-title" className={styles.title}>
            退会しますか？
          </h3>
          <p className="muted">アカウントと、すべてのリスト・カード・ラベルを削除します。この操作は取り消せません。</p>
          <FormField
            ref={passwordRef}
            id="withdraw-password"
            label="パスワード"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={error ?? undefined}
            disabled={busy}
          />
        </div>
        <div className={styles.foot}>
          {/* data-autofocus：開いたときに、このボタンにカーソルを置く（Modal が探して置く） */}
          <button type="button" className="btn" data-autofocus onClick={onClose} disabled={busy}>
            キャンセル
          </button>
          {/* パスワードが空のときは押せない（W-08）。押せないときは、入力欄の Enter でも送られない */}
          <button type="submit" className="btn btn-danger" disabled={password === '' || busy}>
            退会する
          </button>
        </div>
      </form>
    </Modal>
  )
}
