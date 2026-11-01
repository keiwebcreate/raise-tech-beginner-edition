import { useCallback, useId, useRef, useState } from 'react'
import { useEscapeKey } from '../../shared/lib/useEscapeKey.ts'
import { useOutsideClick } from '../../shared/lib/useOutsideClick.ts'
import styles from '../../shared/ui/Menu.module.css'

// リストの見出しの「…」と、押すと開くメニュー（画面設計書 W-03）
// メニューの外をクリックするか Esc で閉じる
// 開け閉めするボタン（aria-expanded・aria-controls）と、ふつうのボタンの並びとして作る。
// role="menu" は「矢印キーで選ぶメニュー」の意味になるので付けない（Tab で選ぶ作りのため。品質チェック #43）
export function ColumnMenu({ onDelete }: { onDelete: () => void }) {
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
        aria-label="リストのメニュー"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen(!open)}
      >
        …
      </button>
      {open && (
        <div id={menuId} className={styles.menu}>
          <div className={styles.title}>リストの操作</div>
          <button
            type="button"
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
