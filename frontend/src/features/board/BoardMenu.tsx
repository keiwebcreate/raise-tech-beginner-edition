import { useCallback, useId, useRef, useState } from 'react'
import { useEscapeKey } from '../../shared/lib/useEscapeKey.ts'
import { useOutsideClick } from '../../shared/lib/useOutsideClick.ts'
import styles from '../../shared/ui/Menu.module.css'

// ボード名の横の「…」と、押すと開くメニュー（画面設計書 W-03。イシュー #51）
// 作りはリストの「…」（ColumnMenu）と同じ。外のクリックか Esc で閉じる
// スマートフォンの幅では出さず、同じ項目を ☰（MobileMenu）に入れる（BoardHeader.module.css）
export function BoardMenu({ onDelete }: { onDelete: () => void }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const menuId = useId() // aria-controls で、ボタンと開く部分を結ぶ
  const close = useCallback(() => setOpen(false), [])
  useOutsideClick(rootRef, open, close)
  useEscapeKey(open, close)

  return (
    <div ref={rootRef} className={styles.anchor}>
      <button
        type="button"
        className="icon-btn"
        aria-label="ボードのメニュー"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen(!open)}
      >
        …
      </button>
      {open && (
        <div id={menuId} className={`${styles.menu} ${styles.alignStart}`}>
          <div className={styles.title}>ボードの操作</div>
          <button
            type="button"
            className={styles.danger}
            aria-haspopup="dialog"
            onClick={() => {
              setOpen(false)
              onDelete()
            }}
          >
            ボードを削除
          </button>
        </div>
      )}
    </div>
  )
}
