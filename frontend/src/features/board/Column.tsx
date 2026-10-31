import type { ColumnWithCardsResponse, LabelResponse } from '../../api/types.ts'
import { CardItem } from '../card/CardItem.tsx'
import styles from './Column.module.css'

type Props = {
  column: ColumnWithCardsResponse
  labels: LabelResponse[] // ボードのラベル全部（カードに渡す）
}

// リスト1つ（表示だけ。名前の変更・削除・カードの追加は、それぞれの機能を作るときに足す）
export function Column({ column, labels }: Props) {
  return (
    <section className={styles.list}>
      <div className={styles.head}>
        <h2 className={styles.name}>{column.name}</h2>
        <span className={styles.count}>{column.cards.length}</span>
      </div>
      <div className={styles.cards}>
        {column.cards.map((card) => (
          <CardItem key={card.id} card={card} labels={labels} />
        ))}
      </div>
    </section>
  )
}
