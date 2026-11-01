import styles from './FilterBar.module.css'

type Props = {
  matched: number // 条件に合うカードの数
  total: number // ボードのカードの数
  onClear: () => void
}

// 絞り込み中に、ヘッダーの下に出す帯（要件 S-03、画面設計書 W-03）
// 何件が合っているか、カードを動かせないこと、条件のクリアを伝える
// 件数の文は <output>（結果の表示。role="status" と同じ意味）にして、条件を変えるたびに読み上げで伝わるようにする
export function FilterBar({ matched, total, onClear }: Props) {
  return (
    <div className={styles.bar}>
      <output aria-live="polite">
        {matched === 0 ? '条件に合うカードはありません' : `${total}件中${matched}件のカードを表示しています`}
        。絞り込み中は、カードの移動と追加はできません。
      </output>
      <button type="button" className="link-btn" onClick={onClear}>
        条件をクリア
      </button>
    </div>
  )
}
