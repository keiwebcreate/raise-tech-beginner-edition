import { useCallback, useRef, useState } from 'react'
import type { LabelResponse } from '../../api/types.ts'
import { useOutsideClick } from '../../shared/lib/useOutsideClick.ts'
import { LabelChip } from './LabelChip.tsx'
import styles from './LabelPicker.module.css'

type Props = {
  labels: LabelResponse[] // ボードのラベル全部（作成順）
  selectedIds: number[]
  onToggle: (labelId: number) => void
}

// カード詳細のラベル欄（画面設計書 W-04）。付いているラベルと「＋」を並べ、
// 「＋」で一覧を開いて付ける・外すを切り替える。一覧の外のクリックか Esc で閉じる
// 「ラベルを管理」（W-05）へのリンクは、ラベル管理を作るときに足す
export function LabelPicker({ labels, selectedIds, onToggle }: Props) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const selected = labels.filter((label) => selectedIds.includes(label.id))

  // 一覧の外をクリックしたら閉じる
  const close = useCallback(() => setOpen(false), [])
  useOutsideClick(rootRef, open, close)

  return (
    <div
      ref={rootRef}
      className={styles.root}
      onKeyDown={(e) => {
        if (e.key === 'Escape' && open) {
          e.stopPropagation() // モーダルは閉じず、一覧だけを閉じる
          setOpen(false)
        }
      }}
    >
      <div className={styles.chips}>
        {selected.map((label) => (
          <LabelChip key={label.id} label={label} />
        ))}
        <button
          type="button"
          className={styles.add}
          aria-label="ラベルを選ぶ"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          ＋
        </button>
      </div>
      {open && (
        <div className={styles.popover} role="menu">
          <div className={styles.popoverTitle}>ラベルを選ぶ</div>
          {labels.length === 0 && <p className={`muted ${styles.empty}`}>ラベルがありません</p>}
          {labels.map((label) => {
            const on = selectedIds.includes(label.id)
            return (
              <button
                key={label.id}
                type="button"
                className={styles.option}
                role="menuitemcheckbox"
                aria-checked={on}
                onClick={() => onToggle(label.id)}
              >
                <span className={`${styles.check} ${on ? styles.on : ''}`}>{on ? '✓' : ''}</span>
                <LabelChip label={label} />
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
}
