import { useCallback, useRef, useState } from 'react'
import { useOutsideClick } from '../../shared/lib/useOutsideClick.ts'
import styles from './ColumnMenu.module.css'

// リストの見出しの「…」と、押すと開くメニュー（画面設計書 W-03）
// メニューの外をクリックするか Esc で閉じる
export function ColumnMenu({ onDelete }: { onDelete: () => void }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const close = useCallback(() => setOpen(false), [])
  useOutsideClick(rootRef, open, close)

  return (
    <div
      ref={rootRef}
      className={styles.anchor}
      onKeyDown={(e) => {
        if (e.key === 'Escape') setOpen(false)
      }}
    >
      <button
        type="button"
        className="icon-btn"
        aria-label="リストのメニュー"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        …
      </button>
      {open && (
        <div className={styles.menu} role="menu">
          <div className={styles.title}>リストの操作</div>
          <button
            type="button"
            role="menuitem"
            className={styles.danger}
            onClick={() => {
              setOpen(false)
              onDelete()
            }}
          >
            リストを削除
          </button>
        </div>
      )}
    </div>
  )
}
