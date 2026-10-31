import { useRef, useState, type KeyboardEvent } from 'react'
import { isSubmitEnter } from '../lib/keyboard.ts'
import styles from './InlineEdit.module.css'

type Props = {
  value: string // 今の名前
  label: string // 入力欄の名前（読み上げ用）
  isEditing: boolean
  onStartEdit: () => void
  onEndEdit: () => void
  // 入力チェック。問題がなければ null
  validate: (value: string) => string | null
  // 保存。null：成功／メッセージ：入力欄の下に出して編集を続ける／空文字：失敗（通知済み）で編集を終える
  onSave: (value: string) => Promise<string | null>
  displayClassName: string // 名前を表示しているときの見た目
  inputClassName?: string // 入力欄の入れ物の見た目（幅など）
}

// その場での名前の変更（リスト名・ボード名。画面設計書 W-03）
// 名前をクリックすると入力欄に変わる。Enter で確定、Esc で取り消し、入力欄の外をクリックしても確定する
export function InlineEdit({ isEditing, value, onStartEdit, displayClassName, ...editProps }: Props) {
  if (!isEditing) {
    return (
      <button type="button" className={displayClassName} onClick={onStartEdit} title="クリックして名前を変更">
        {value}
      </button>
    )
  }
  // 入力欄は開くたびに作り直す（前回の入力やエラーが残らないように）
  return <EditingInput value={value} {...editProps} />
}

function EditingInput({
  value,
  label,
  onEndEdit,
  validate,
  onSave,
  inputClassName,
}: Omit<Props, 'isEditing' | 'onStartEdit' | 'displayClassName'>) {
  const [draft, setDraft] = useState(value)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  // 保存中に入力欄から外れても（押せない状態にすると外れる）、二重に保存しないための目印
  const savingRef = useRef(false)

  async function commit(trigger: 'enter' | 'blur') {
    if (savingRef.current) return
    const message = validate(draft)
    if (message) {
      // 空などのとき：Enter ならエラーを出して編集を続け、外をクリックしたら元の名前に戻す（画面設計書 W-03）
      if (trigger === 'blur') onEndEdit()
      else setError(message)
      return
    }
    const name = draft.trim()
    if (name === value) {
      onEndEdit() // 変わっていなければ、保存せずに閉じる
      return
    }

    savingRef.current = true
    setSaving(true)
    const result = await onSave(name)
    savingRef.current = false
    if (result === null || result === '') {
      onEndEdit()
    } else {
      setSaving(false)
      setError(result)
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (isSubmitEnter(event)) {
      event.preventDefault()
      commit('enter')
    } else if (event.key === 'Escape') {
      onEndEdit() // 取り消し
    }
  }

  return (
    <span className={`${styles.wrap} ${inputClassName ?? ''}`}>
      <input
        className={`input ${styles.input} ${error ? 'is-error' : ''}`}
        aria-label={label}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={() => commit('blur')}
        disabled={saving}
        // 開いたときにカーソルを置き、文字の最後に合わせる
        autoFocus
        onFocus={(e) => e.currentTarget.setSelectionRange(e.currentTarget.value.length, e.currentTarget.value.length)}
      />
      {error && <span className={`error ${styles.error}`}>{error}</span>}
    </span>
  )
}
