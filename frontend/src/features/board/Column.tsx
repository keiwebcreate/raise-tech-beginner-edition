import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import type { ColumnWithCardsResponse, LabelResponse } from '../../api/types.ts'
import { MAX_LENGTH } from '../../shared/lib/constants.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { InlineEdit } from '../../shared/ui/InlineEdit.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { AddCardForm } from '../card/AddCardForm.tsx'
import { CardItem } from '../card/CardItem.tsx'
import { useBoard } from './BoardContext.ts'
import styles from './Column.module.css'
import * as columnApi from './columnApi.ts'

type Props = {
  column: ColumnWithCardsResponse
  labels: LabelResponse[] // ボードのラベル全部（カードに渡す）
  isAddingCard: boolean // このリストの「＋ カードを追加」の入力欄が開いているか
  onOpenAddCard: () => void
  onCloseAddCard: () => void
  isRenaming: boolean // リスト名を変更中か
  onStartRename: () => void
  onEndRename: () => void
  onOpenCard: (cardId: number) => void
}

// リスト1つ（削除は、Delete を作るときに足す）
export function Column({
  column,
  labels,
  isAddingCard,
  onOpenAddCard,
  onCloseAddCard,
  isRenaming,
  onStartRename,
  onEndRename,
  onOpenCard,
}: Props) {
  const { dispatch } = useBoard()
  const { showApiError } = useToast()

  // A-09 リスト名の変更
  async function rename(name: string): Promise<string | null> {
    try {
      const saved = await columnApi.renameColumn(column.id, name)
      dispatch({ type: 'columnRenamed', column: saved })
      return null
    } catch (e) {
      if (e instanceof ApiError && e.code === 'VALIDATION_ERROR') {
        return fieldErrorsOf(e).name ?? e.detail
      }
      showApiError(e)
      return ''
    }
  }

  return (
    <section className={styles.list}>
      <div className={styles.head}>
        <h2 className={styles.heading}>
          <InlineEdit
            value={column.name}
            label="リスト名"
            isEditing={isRenaming}
            onStartEdit={onStartRename}
            onEndEdit={onEndRename}
            validate={(value) => validateText(value, 'リスト名', MAX_LENGTH.columnName)}
            onSave={rename}
            displayClassName={styles.name}
          />
        </h2>
        <span className={styles.count}>{column.cards.length}</span>
      </div>
      <div className={styles.cards}>
        {column.cards.map((card) => (
          <CardItem key={card.id} card={card} labels={labels} onOpen={() => onOpenCard(card.id)} />
        ))}
      </div>
      <AddCardForm column={column} isOpen={isAddingCard} onOpen={onOpenAddCard} onClose={onCloseAddCard} />
    </section>
  )
}
