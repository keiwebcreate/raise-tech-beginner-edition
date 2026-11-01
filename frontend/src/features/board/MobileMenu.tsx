import { useCallback, useId, useRef, useState } from 'react'
import type { UserResponse } from '../../api/types.ts'
import type { AccountModalKind } from '../auth/UserMenu.tsx'
import { useEscapeKey } from '../../shared/lib/useEscapeKey.ts'
import { useOutsideClick } from '../../shared/lib/useOutsideClick.ts'
import menuStyles from '../../shared/ui/Menu.module.css'
import styles from './MobileMenu.module.css'

type Props = {
  user: UserResponse
  loggingOut: boolean // ログアウトの通信中は、もう一度押せないようにする
  onLogout: () => void
  onOpenAccountModal: (kind: AccountModalKind) => void // メールアドレス・パスワードの変更、退会のモーダルを開く
  onOpenLabels?: () => void // ラベル管理（W-05）を開く。ボードの読み込み中・失敗したときは undefined（出さない）
}

// スマートフォン幅のヘッダーの ☰ と、押すと開くメニュー（画面設計書 W-03。イシュー #47）
// パソコンの幅の「ラベル管理」「メールアドレス ▾」（ゲストは「ゲスト」の札と「ログアウト」）を、ここにまとめる
// どちらを見せるかは BoardHeader.module.css の @media で切り替える（隠したほうは読み上げにも出ない）
// 作りはリストの「…」（ColumnMenu）と同じ。外のクリックか Esc で閉じる
export function MobileMenu({ user, loggingOut, onLogout, onOpenAccountModal, onOpenLabels }: Props) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const menuId = useId()
  const close = useCallback(() => setOpen(false), [])
  useOutsideClick(rootRef, open, close)
  useEscapeKey(open, close)

  // 押したらメニューを閉じてから動かす
  const run = (action: () => void) => () => {
    setOpen(false)
    action()
  }

  return (
    <div ref={rootRef} className={menuStyles.anchor}>
      <button
        type="button"
        className="icon-btn"
        aria-label="メニュー"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen(!open)}
      >
        {/* ☰ の3本線。文字の「☰」はフォントによって形が変わるので、図で描く */}
        <svg width="20" height="20" viewBox="0 0 20 20" aria-hidden="true">
          <path d="M3 5h14M3 10h14M3 15h14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
        </svg>
      </button>
      {open && (
        <div id={menuId} className={menuStyles.menu}>
          {/* 誰でログインしているか（押せない見出し） */}
          <div className={`${menuStyles.title} ${styles.who}`}>{user.isGuest ? 'ゲストとして利用中' : user.email}</div>
          {onOpenLabels && (
            <button type="button" aria-haspopup="dialog" onClick={run(onOpenLabels)}>
              ラベル管理
            </button>
          )}
          {/* ゲストのメールアドレスはダミーで、パスワードは誰も知らないので、変更はない（要件 U-12・U-13） */}
          {!user.isGuest && (
            <>
              <button type="button" aria-haspopup="dialog" onClick={run(() => onOpenAccountModal('email'))}>
                メールアドレスの変更
              </button>
              <button type="button" aria-haspopup="dialog" onClick={run(() => onOpenAccountModal('password'))}>
                パスワードの変更
              </button>
            </>
          )}
          <button type="button" disabled={loggingOut} onClick={run(onLogout)}>
            ログアウト
          </button>
          {/* ゲストはログアウトでデータが消えるので、退会はない（要件 U-11） */}
          {!user.isGuest && (
            <button
              type="button"
              className={menuStyles.danger}
              aria-haspopup="dialog"
              onClick={run(() => onOpenAccountModal('withdraw'))}
            >
              退会する
            </button>
          )}
        </div>
      )}
    </div>
  )
}
