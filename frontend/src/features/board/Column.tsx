import { Draggable, Droppable } from '@hello-pangea/dnd'
import { useState } from 'react'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import type { ColumnWithCardsResponse, LabelResponse } from '../../api/types.ts'
import { MAX_LENGTH } from '../../shared/lib/constants.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog.tsx'
import { InlineEdit } from '../../shared/ui/InlineEdit.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { AddCardForm } from '../card/AddCardForm.tsx'
import { CardItem } from '../card/CardItem.tsx'
import { useBoard } from './BoardContext.ts'
import styles from './Column.module.css'
import { ColumnMenu } from './ColumnMenu.tsx'
import * as columnApi from './columnApi.ts'
import { cardDraggableId, cardsDroppableId, columnDraggableId, DRAG_TYPE } from './dragAndDrop.ts'

type Props = {
  column: ColumnWithCardsResponse
  index: number // 左から何番目か（ドラッグで使う）
  dragDisabled: boolean // ドラッグの保存を待っている間は true（リストもカードも動かせない）
  labels: LabelResponse[] // ボードのラベル全部（カードに渡す）
  isAddingCard: boolean // このリストの「＋ カードを追加」の入力欄が開いているか
  onOpenAddCard: () => void
  onCloseAddCard: () => void
  isRenaming: boolean // リスト名を変更中か
  onStartRename: () => void
  onEndRename: () => void
  onOpenCard: (cardId: number) => void
}

// リスト1つ。名前の変更、「…」メニューからの削除、カードの追加ができる
// 見出しをつかむとリストを、カードをつかむとカードをドラッグで動かせる（詳細設計書 4.5）
export function Column({
  column,
  index,
  dragDisabled,
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
  const [confirmingDelete, setConfirmingDelete] = useState(false)

  // A-10 リストの削除（確認ダイアログで「削除する」を押したあと）
  async function deleteColumn() {
    try {
      await columnApi.deleteColumn(column.id)
      dispatch({ type: 'columnDeleted', columnId: column.id })
    } catch (e) {
      showApiError(e)
    }
  }

  // 何が消えるかを具体的に書く。カードの枚数は手元のデータから数える（API設計書 A-10）
  const count = column.cards.length
  const deleteMessage =
    count > 0
      ? `「${column.name}」と中のカード${count}件を削除します。この操作は取り消せません。`
      : `「${column.name}」を削除します。この操作は取り消せません。`

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
    // つかむところは名前のボタン（カードと同じ形。クリック・Enter で名前の変更、スペースキーでつかむ）
    // 見出し全体をつかむところにすると、ボタン（名前・…メニュー）の入ったボタンになり、読み上げで中に届かないため
    // 名前の変更中は名前のボタンがなくなる（入力欄になる）ので、ドラッグもできなくする
    <Draggable
      draggableId={columnDraggableId(column.id)}
      index={index}
      isDragDisabled={dragDisabled || isRenaming}
      disableInteractiveElementBlocking
    >
      {(drag, dragState) => (
        <section
          ref={drag.innerRef}
          {...drag.draggableProps}
          className={`${styles.list} ${dragState.isDragging ? styles.dragging : ''}`}
        >
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
                displayProps={drag.dragHandleProps ?? undefined}
              />
            </h2>
            <span className={styles.count}>{column.cards.length}</span>
            <ColumnMenu onDelete={() => setConfirmingDelete(true)} />
          </div>
          <Droppable droppableId={cardsDroppableId(column.id)} type={DRAG_TYPE.card}>
            {(drop, dropState) => (
              <div
                ref={drop.innerRef}
                {...drop.droppableProps}
                className={`${styles.cards} ${dropState.isDraggingOver ? styles.over : ''}`}
              >
                {column.cards.map((card, cardIndex) => (
                  // カードの中のボタン（詳細を開く）とリンクをつかんでも動かせるようにする。クリックだけなら、それぞれの動きをする
                  <Draggable
                    key={card.id}
                    draggableId={cardDraggableId(card.id)}
                    index={cardIndex}
                    isDragDisabled={dragDisabled}
                    disableInteractiveElementBlocking
                  >
                    {(cardDrag, cardDragState) => (
                      <div ref={cardDrag.innerRef} {...cardDrag.draggableProps} className={styles.cardSlot}>
                        <CardItem
                          card={card}
                          labels={labels}
                          onOpen={() => onOpenCard(card.id)}
                          dragHandleProps={cardDrag.dragHandleProps}
                          isDragging={cardDragState.isDragging}
                        />
                      </div>
                    )}
                  </Draggable>
                ))}
                {drop.placeholder}
              </div>
            )}
          </Droppable>
          <AddCardForm column={column} isOpen={isAddingCard} onOpen={onOpenAddCard} onClose={onCloseAddCard} />
          {confirmingDelete && (
            <ConfirmDialog
              title="リストを削除しますか？"
              message={deleteMessage}
              onAnswer={(ok) => {
                setConfirmingDelete(false)
                if (ok) deleteColumn()
              }}
            />
          )}
        </section>
      )}
    </Draggable>
  )
}
