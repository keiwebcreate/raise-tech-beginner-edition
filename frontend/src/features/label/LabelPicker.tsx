import { useCallback, useId, useRef, useState } from 'react'
import type { LabelResponse } from '../../api/types.ts'
import { useEscapeKey } from '../../shared/lib/useEscapeKey.ts'
import { useOutsideClick } from '../../shared/lib/useOutsideClick.ts'
import { LabelChip } from './LabelChip.tsx'
import styles from './LabelPicker.module.css'

type Props = {
  labels: LabelResponse[] // ボードのラベル全部（作成順）
  selectedIds: number[]
  onToggle: (labelId: number) => void
  onManage: () => void // 「ラベルを管理」（W-05）を開く
}

// カード詳細のラベル欄（画面設計書 W-04）。付いているラベルと「＋」を並べ、
// 「＋」で一覧を開いて付ける・外すを切り替える。一覧の外のクリックか Esc で閉じる
// 一覧の下の「ラベルを管理」で、ラベル管理（W-05）をカード詳細の上に重ねて開く
export function LabelPicker({ labels, selectedIds, onToggle, onManage }: Props) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const listId = useId() // aria-controls で、「＋」と開く一覧を結ぶ
  const selected = labels.filter((label) => selectedIds.includes(label.id))

  // 一覧の外をクリックするか Esc で閉じる（Esc でカード詳細は閉じない）
  const close = useCallback(() => setOpen(false), [])
  useOutsideClick(rootRef, open, close)
  useEscapeKey(open, close)

  return (
    <div ref={rootRef} className={styles.root}>
      <div className={styles.chips}>
        {selected.map((label) => (
          <LabelChip key={label.id} label={label} />
        ))}
        <button
          type="button"
          className={styles.add}
          aria-label="ラベルを選ぶ"
          aria-expanded={open}
          aria-controls={listId}
          onClick={() => setOpen(!open)}
        >
          ＋
        </button>
      </div>
      {open && (
        // role="menu" は「矢印キーで選ぶメニュー」の意味になるので付けない（Tab で選ぶ作りのため。品質チェック #43）
        // 選ぶものの一まとまりなので <fieldset>、見出しは <legend>（読み上げで「ラベルを選ぶ」のまとまりと伝わる）
        // 付いているかどうかは、押された状態のボタン（aria-pressed）で伝える
        <fieldset id={listId} className={styles.popover}>
          <legend className={styles.popoverTitle}>ラベルを選ぶ</legend>
          {labels.length === 0 && (
            <p className={`muted ${styles.empty}`}>ラベルがありません。「ラベルを管理」から作れます。</p>
          )}
          {labels.map((label) => {
            const on = selectedIds.includes(label.id)
            return (
              <button
                key={label.id}
                type="button"
                className={styles.option}
                aria-pressed={on}
                onClick={() => onToggle(label.id)}
              >
                <span className={`${styles.check} ${on ? styles.on : ''}`}>{on ? '✓' : ''}</span>
                <LabelChip label={label} />
              </button>
            )
          })}
          <div className={styles.foot}>
            <button
              type="button"
              className="link-btn"
              aria-haspopup="dialog"
              onClick={() => {
                setOpen(false)
                onManage()
              }}
            >
              ラベルを管理
            </button>
          </div>
        </fieldset>
      )}
    </div>
  )
}
