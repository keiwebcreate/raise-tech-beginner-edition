import type { CSSProperties } from 'react'
import type { LabelResponse } from '../../api/types.ts'
import { LABEL_COLORS } from '../../shared/lib/constants.ts'
import styles from './LabelChip.module.css'

// ラベル1つ分の色付きの札
export function LabelChip({ label }: { label: LabelResponse }) {
  // 黄色など明るい色は、白い文字だと読みにくいので黒い文字にする
  const darkText = LABEL_COLORS.find((c) => c.code === label.color)?.darkText ?? false
  // 色は CSS 変数 --c で渡す（CSS の background: var(--c) が使う）
  const style = { '--c': label.color } as CSSProperties
  return (
    <span className={`${styles.chip} ${darkText ? styles.darkText : ''}`} style={style}>
      {label.name}
    </span>
  )
}
