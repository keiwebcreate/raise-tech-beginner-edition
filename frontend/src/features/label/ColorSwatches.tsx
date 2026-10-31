import type { CSSProperties } from 'react'
import { LABEL_COLORS } from '../../shared/lib/constants.ts'
import styles from './ColorSwatches.module.css'

type Props = {
  name: string // ラジオボタンのまとまりの名前（画面の中で重ならないように）
  value: string // 選んでいる色コード
  onChange: (color: string) => void
}

// ラベルの8色の選択肢（画面設計書 6章）
// 中身は HTML のラジオボタンなので、矢印キーで選べ、読み上げでも「色・選択中」と伝わる。見た目だけを色の四角にする
export function ColorSwatches({ name, value, onChange }: Props) {
  return (
    <fieldset className={styles.group}>
      <legend className={styles.hidden}>色</legend>
      {LABEL_COLORS.map((color) => (
        <label
          key={color.code}
          className={styles.swatch}
          style={{ '--c': color.code } as CSSProperties}
          title={color.name}
        >
          <input
            type="radio"
            className={styles.hidden}
            name={name}
            value={color.code}
            checked={color.code === value}
            onChange={() => onChange(color.code)}
          />
          <span className={styles.hidden}>{color.name}</span>
        </label>
      ))}
    </fieldset>
  )
}
