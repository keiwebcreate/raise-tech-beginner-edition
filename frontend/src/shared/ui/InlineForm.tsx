import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { isSubmitEnter } from '../lib/keyboard.ts'
import styles from './InlineForm.module.css'

type Props = {
  label: string // 入力欄の名前（読み上げ用）
  placeholder: string
  submitText: string
  help?: string
  // 送信したときの処理。返す値で動きが変わる
  // - null：成功。入力欄を空にして、続けて入力できるようにする
  // - メッセージ：入力欄の下に出す（入力はそのまま残す）
  // - 空文字：失敗したが、ここには何も出さない（通知で知らせたときなど。入力はそのまま残す）
  onSubmit: (value: string) => Promise<string | null>
  onClose: () => void
}

// その場の入力欄（「＋ カードを追加」「＋ リストを追加」を押したときに出るもの）
// Enter で送信し、成功したら空にして続けて入力できる。Esc か ✕ で閉じる（画面設計書 W-03）
export function InlineForm({ label, placeholder, submitText, help, onSubmit, onClose }: Props) {
  const [value, setValue] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  // 開いたとき・送信が終わったときに、入力欄にカーソルを置く（送信中は押せない状態にするので外れる）
  useEffect(() => {
    if (!saving) inputRef.current?.focus()
  }, [saving])

  async function submit() {
    if (saving) return
    setSaving(true)
    const message = await onSubmit(value)
    setSaving(false)
    setError(message)
    if (message === null) setValue('')
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (isSubmitEnter(event)) {
      event.preventDefault()
      submit()
    } else if (event.key === 'Escape') {
      onClose()
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    submit()
  }

  return (
    <form className={styles.form} onSubmit={handleSubmit} noValidate>
      <input
        ref={inputRef}
        className={`input ${error ? 'is-error' : ''}`}
        aria-label={label}
        placeholder={placeholder}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        // Enter は onKeyDown で扱う（日本語の変換中の Enter で送信しないため）
        onKeyDown={handleKeyDown}
        disabled={saving}
      />
      {error && <div className={`error ${styles.error}`}>{error}</div>}
      <div className={styles.row}>
        <button type="submit" className="btn btn-primary" disabled={saving}>
          {submitText}
        </button>
        <button type="button" className="icon-btn" aria-label="閉じる" onClick={onClose}>
          ✕
        </button>
        {help && <span className="help">{help}</span>}
      </div>
    </form>
  )
}
