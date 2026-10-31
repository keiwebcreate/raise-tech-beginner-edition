import type { ColumnWithCardsResponse, LabelResponse } from '../../api/types.ts'
import { AddCardForm } from '../card/AddCardForm.tsx'
import { CardItem } from '../card/CardItem.tsx'
import styles from './Column.module.css'

type Props = {
  column: ColumnWithCardsResponse
  labels: LabelResponse[] // ボードのラベル全部（カードに渡す）
  isAddingCard: boolean // このリストの「＋ カードを追加」の入力欄が開いているか
  onOpenAddCard: () => void
  onCloseAddCard: () => void
}

// リスト1つ（名前の変更・削除は、それぞれの機能を作るときに足す）
export function Column({ column, labels, isAddingCard, onOpenAddCard, onCloseAddCard }: Props) {
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
      <AddCardForm column={column} isOpen={isAddingCard} onOpen={onOpenAddCard} onClose={onCloseAddCard} />
    </section>
  )
}
