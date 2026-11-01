import type { ComponentProps } from 'react'

// ComponentProps<'input'>：<input> に渡せるもの全部（React 19 では ref も入る。退会の画面でカーソルを置くのに使う）
type Props = {
  label: string
  error?: string
  help?: string
} & ComponentProps<'input'>

// ラベル・入力欄・エラー・説明の組（ログイン・登録の画面で使う）
// error があれば、入力欄を赤くして下にメッセージを出す
export function FormField({ label, error, help, id, ...inputProps }: Props) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        className={`input ${error ? 'is-error' : ''}`}
        aria-invalid={error ? true : undefined}
        {...inputProps}
      />
      {error && <div className="error">{error}</div>}
      {help && <div className="help">{help}</div>}
    </div>
  )
}
