import { useCallback, useId, useRef, useState, type ChangeEvent, type ReactNode } from 'react'
import type { LabelResponse, Level } from '../../api/types.ts'
import { LEVELS } from '../../shared/lib/constants.ts'
import { useEscapeKey } from '../../shared/lib/useEscapeKey.ts'
import { useOutsideClick } from '../../shared/lib/useOutsideClick.ts'
import { LabelChip } from '../label/LabelChip.tsx'
import { countConditions, DUE_FILTERS, EMPTY_FILTER, type DueFilter } from './cardFilter.ts'
import styles from './CardSearch.module.css'
import { useCardFilter } from './useCardFilter.ts'

// ヘッダーの検索欄と「絞り込み」ボタン（要件 S-01〜S-03、画面設計書 W-03）
// 「絞り込み」を押すと、ラベル・期限・重要度・緊急度のチェックボックスの小窓が開く。外のクリックか Esc で閉じる
export function CardSearch({ labels }: { labels: LabelResponse[] }) {
  const { filter, setFilter } = useCardFilter(labels)
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const panelId = useId()
  const close = useCallback(() => setOpen(false), [])
  useOutsideClick(rootRef, open, close)
  useEscapeKey(open, close)

  // 検索欄の文字。日本語の変換中は URL に入れないので、URL とは別に持つ
  const [text, setText] = useState(filter.query)
  // URL の文字が外から変わった（帯の「条件をクリア」など）ら、検索欄もそろえる
  // （React の「前の値と比べて、描くときに直す」書き方。useEffect で直すと1回余分に描き直すため）
  const [prevQuery, setPrevQuery] = useState(filter.query)
  if (filter.query !== prevQuery) {
    setPrevQuery(filter.query)
    setText(filter.query)
  }

  function handleTextChange(event: ChangeEvent<HTMLInputElement>) {
    setText(event.target.value)
    // 変換中（まだ確定していない文字がある）は反映しない。確定したら compositionend で反映する
    if (!(event.nativeEvent as InputEvent).isComposing) {
      setFilter({ ...filter, query: event.target.value })
    }
  }

  const count = countConditions(filter)

  return (
    <div ref={rootRef} className={styles.root}>
      <input
        type="search"
        className={`input ${styles.search}`}
        placeholder="カードを検索"
        aria-label="カードを検索"
        value={text}
        onChange={handleTextChange}
        onCompositionEnd={(event) => setFilter({ ...filter, query: event.currentTarget.value })}
      />
      <button
        type="button"
        className={`btn btn-ghost ${count > 0 ? styles.active : ''}`}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen(!open)}
      >
        絞り込み
        {/* 見える数字は読み上げず、読み上げには文で伝える（sr-only は画面に見えない文字） */}
        {count > 0 && (
          <>
            <span className={styles.badge} aria-hidden="true">
              {count}
            </span>
            <span className="sr-only">（{count}件選択中）</span>
          </>
        )}
      </button>
      {open && (
        <div id={panelId} className={styles.panel}>
          <fieldset className={styles.group}>
            <legend className={styles.legend}>ラベル</legend>
            {labels.map((label) => (
              <Option
                key={label.id}
                checked={filter.labelIds.includes(label.id)}
                onChange={() => setFilter({ ...filter, labelIds: toggle(filter.labelIds, label.id) })}
              >
                <LabelChip label={label} />
              </Option>
            ))}
            <Option checked={filter.noLabel} onChange={() => setFilter({ ...filter, noLabel: !filter.noLabel })}>
              ラベルなし
            </Option>
          </fieldset>
          <fieldset className={styles.group}>
            <legend className={styles.legend}>期限</legend>
            {DUE_FILTERS.map((due) => (
              <Option
                key={due.value}
                checked={filter.due.includes(due.value)}
                onChange={() => setFilter({ ...filter, due: toggle<DueFilter>(filter.due, due.value) })}
              >
                {due.text}
              </Option>
            ))}
          </fieldset>
          <LevelGroup
            legend="重要度"
            selected={filter.importance}
            onToggle={(level) => setFilter({ ...filter, importance: toggle(filter.importance, level) })}
          />
          <LevelGroup
            legend="緊急度"
            selected={filter.urgency}
            onToggle={(level) => setFilter({ ...filter, urgency: toggle(filter.urgency, level) })}
          />
          <div className={styles.foot}>
            {/* 選んだ条件だけを外す（検索の文字は残す） */}
            <button
              type="button"
              className="link-btn"
              disabled={count === 0}
              onClick={() => setFilter({ ...EMPTY_FILTER, query: filter.query })}
            >
              選んだ条件をクリア
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// チェックボックス1つ（ラベルの文字ごと押せる）
function Option({ checked, onChange, children }: { checked: boolean; onChange: () => void; children: ReactNode }) {
  return (
    <label className={styles.option}>
      <input type="checkbox" checked={checked} onChange={onChange} />
      {children}
    </label>
  )
}

// 重要度・緊急度（高・中・低）。高・中・低を横に並べる
function LevelGroup({
  legend,
  selected,
  onToggle,
}: {
  legend: string
  selected: Level[]
  onToggle: (level: Level) => void
}) {
  return (
    <fieldset className={styles.group}>
      <legend className={styles.legend}>{legend}</legend>
      <div className={styles.row}>
        {LEVELS.map((level) => (
          <Option key={level.value} checked={selected.includes(level.value)} onChange={() => onToggle(level.value)}>
            {level.text}
          </Option>
        ))}
      </div>
    </fieldset>
  )
}

// 入っていれば外し、なければ足す
function toggle<T>(values: T[], value: T): T[] {
  return values.includes(value) ? values.filter((v) => v !== value) : [...values, value]
}
