import { useCallback, useId, useRef, useState } from 'react'
import { useEscapeKey } from '../../shared/lib/useEscapeKey.ts'
import { useOutsideClick } from '../../shared/lib/useOutsideClick.ts'
import menuStyles from '../../shared/ui/Menu.module.css'
import styles from './UserMenu.module.css'

type Props = {
  email: string
  loggingOut: boolean // ログアウトの通信中は、もう一度押せないようにする
  onLogout: () => void
  onWithdraw: () => void // 退会の確認（W-08）を開く
}

// ヘッダーのメールアドレスと「▾」のボタン、押すと開くメニュー（画面設計書 W-03）
// 「ログアウト」「退会する」（赤字）を選べる。メニューの外をクリックするか Esc で閉じる
// ゲストには出さない（ゲストは「ゲスト」の札と「ログアウト」ボタン。BoardHeader）
// 作りはリストの「…」（ColumnMenu）と同じ。role="menu" を付けない理由もそちらに書いた
export function UserMenu({ email, loggingOut, onLogout, onWithdraw }: Props) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const menuId = useId()
  const close = useCallback(() => setOpen(false), [])
  useOutsideClick(rootRef, open, close)
  useEscapeKey(open, close)

  return (
    <div ref={rootRef} className={menuStyles.anchor}>
      <button
        type="button"
        className={`btn btn-ghost ${styles.toggle}`}
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen(!open)}
      >
        <span className={styles.email}>{email}</span>
        <span aria-hidden="true">▾</span>
      </button>
      {open && (
        <div id={menuId} className={menuStyles.menu}>
          <button
            type="button"
            disabled={loggingOut}
            onClick={() => {
              setOpen(false)
              onLogout()
            }}
          >
            ログアウト
          </button>
          <button
            type="button"
            className={menuStyles.danger}
            onClick={() => {
              setOpen(false)
              onWithdraw()
            }}
          >
            退会する
          </button>
        </div>
      )}
    </div>
  )
}
