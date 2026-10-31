import {
  DragDropContext,
  Droppable,
  type DragStart,
  type DragUpdate,
  type DropResult,
  type ResponderProvided,
} from '@hello-pangea/dnd'
import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/client.ts'
import type { BoardResponse } from '../../api/types.ts'
import { LIMITS, MESSAGES } from '../../shared/lib/constants.ts'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { moveCard } from '../card/cardApi.ts'
import { CardModal } from '../card/CardModal.tsx'
import { LabelModal } from '../label/LabelModal.tsx'
import { AddColumnForm } from './AddColumnForm.tsx'
import { getBoard } from './boardApi.ts'
import { useBoard } from './BoardContext.ts'
import { BoardHeader } from './BoardHeader.tsx'
import styles from './BoardPage.module.css'
import { Column } from './Column.tsx'
import { moveColumn } from './columnApi.ts'
import {
  BOARD_DROPPABLE_ID,
  DRAG_INSTRUCTIONS,
  DRAG_TYPE,
  endMessage,
  idFrom,
  startMessage,
  updateMessage,
} from './dragAndDrop.ts'

// 開いているその場の入力欄。一度に開くのは1つだけ（ほかを開いたら前のものは閉じる）
type OpenForm =
  | { kind: 'card'; columnId: number } // ＋ カードを追加
  | { kind: 'column' } // ＋ リストを追加
  | { kind: 'renameColumn'; columnId: number } // リスト名の変更
  | { kind: 'renameBoard' } // ボード名の変更
  | null

function isSameForm(a: OpenForm, b: OpenForm): boolean {
  return JSON.stringify(a) === JSON.stringify(b)
}

// W-03 ボード画面
export function BoardPage() {
  const { board, dispatch } = useBoard()
  const [loadError, setLoadError] = useState<string | null>(null)
  const [openForm, setOpenForm] = useState<OpenForm>(null)
  const [openCardId, setOpenCardId] = useState<number | null>(null) // カード詳細を開いているカード
  const closeCard = useCallback(() => setOpenCardId(null), [])
  const [labelsOpen, setLabelsOpen] = useState(false) // ヘッダーから開いたラベル管理（W-05）
  // ドラッグの保存（A-11・A-15）を待っている間は、次のドラッグをさせない
  // （続けて動かしたあとで前の保存が失敗すると、戻す先の「動かす前」が古くなってしまうため）
  const [savingMove, setSavingMove] = useState(false)
  const { showToast, showApiError } = useToast()

  // その入力欄がまだ開いているときだけ閉じる
  // （名前の保存を待っている間に、別の入力欄を開いていたら、そちらは閉じない）
  const closeForm = (form: OpenForm) => setOpenForm((current) => (isSameForm(current, form) ? null : current))

  // 画面を開いたときに、ボードを1回読み込む
  useEffect(() => {
    // 画面を閉じたあとに結果が返ってきても、何もしないようにする目印
    let ignore = false
    getBoard()
      .then((loaded) => {
        if (!ignore) dispatch({ type: 'loaded', board: loaded })
      })
      .catch((error: unknown) => {
        if (!ignore) setLoadError(error instanceof ApiError ? error.detail : MESSAGES.network)
      })
    return () => {
      ignore = true
    }
  }, [dispatch])

  if (loadError) {
    return (
      <>
        <BoardHeader />
        <div className={styles.centerBox}>
          <h1 className={styles.centerTitle}>ボードを読み込めませんでした</h1>
          <p className="muted">{loadError}</p>
          <button type="button" className="btn btn-primary" onClick={() => location.reload()}>
            再読み込み
          </button>
        </div>
      </>
    )
  }

  if (!board) {
    return (
      <>
        <BoardHeader />
        <BoardSkeleton />
      </>
    )
  }

  // ドロップしたとき（キーボードで置いたとき・やめたときも）。画面を先に変えてから保存する（API設計書 6.2）
  function handleDragEnd(result: DropResult, provided: ResponderProvided) {
    if (!board) return
    provided.announce(endMessage(board, result))
    const { source, destination, draggableId, type } = result
    // 置ける場所の外・やめた・元の場所に置いた：何もしない（ライブラリが元の位置に戻す）
    if (!destination) return
    if (source.droppableId === destination.droppableId && source.index === destination.index) return
    const position = destination.index

    if (type === DRAG_TYPE.column) {
      const columnId = idFrom(draggableId)
      dispatch({ type: 'columnMoved', columnId, position })
      saveMove(() => moveColumn(columnId, position), board)
      return
    }

    const cardId = idFrom(draggableId)
    const toColumnId = idFrom(destination.droppableId)
    const toColumn = board.columns.find((column) => column.id === toColumnId)
    // 満杯のリストへは、API を呼ぶ前に止める（画面設計書 W-03）。並びを変えないので、カードは元の位置に戻る
    if (source.droppableId !== destination.droppableId && toColumn && toColumn.cards.length >= LIMITS.cardsPerColumn) {
      showToast(MESSAGES.cardLimit)
      return
    }
    dispatch({ type: 'cardMoved', cardId, toColumnId, position })
    saveMove(() => moveCard(cardId, toColumnId, position), board)
  }

  // 保存に失敗したら、動かす前のボード（before）に戻して通知を出す
  async function saveMove(save: () => Promise<void>, before: BoardResponse) {
    setSavingMove(true)
    try {
      await save()
    } catch (e) {
      dispatch({ type: 'restored', board: before })
      showApiError(e)
    } finally {
      setSavingMove(false)
    }
  }

  const renameBoardForm: OpenForm = { kind: 'renameBoard' }
  return (
    <>
      <BoardHeader
        boardName={board.name}
        isRenaming={openForm?.kind === 'renameBoard'}
        onStartRename={() => setOpenForm(renameBoardForm)}
        onEndRename={() => closeForm(renameBoardForm)}
        onOpenLabels={() => setLabelsOpen(true)}
      />
      <DragDropContext
        onDragStart={(start: DragStart, provided: ResponderProvided) => provided.announce(startMessage(board, start))}
        onDragUpdate={(update: DragUpdate, provided: ResponderProvided) =>
          provided.announce(updateMessage(board, update))
        }
        onDragEnd={handleDragEnd}
        dragHandleUsageInstructions={DRAG_INSTRUCTIONS}
      >
        <main className={styles.scroll}>
          <div className={styles.board}>
            {/* リストの並び（横）。リストは見出しをつかんで動かす */}
            <Droppable droppableId={BOARD_DROPPABLE_ID} type={DRAG_TYPE.column} direction="horizontal">
              {(provided) => (
                <div ref={provided.innerRef} {...provided.droppableProps} className={styles.columns}>
                  {board.columns.map((column, index) => {
                    const addCardForm: OpenForm = { kind: 'card', columnId: column.id }
                    const renameForm: OpenForm = { kind: 'renameColumn', columnId: column.id }
                    return (
                      <Column
                        key={column.id}
                        column={column}
                        index={index}
                        dragDisabled={savingMove}
                        labels={board.labels}
                        isAddingCard={isSameForm(openForm, addCardForm)}
                        onOpenAddCard={() => setOpenForm(addCardForm)}
                        onCloseAddCard={() => closeForm(addCardForm)}
                        isRenaming={isSameForm(openForm, renameForm)}
                        onStartRename={() => setOpenForm(renameForm)}
                        onEndRename={() => closeForm(renameForm)}
                        onOpenCard={setOpenCardId}
                      />
                    )
                  })}
                  {/* ドラッグ中に、動かしているものの分の場所を空けておく */}
                  {provided.placeholder}
                </div>
              )}
            </Droppable>
            <AddColumnForm
              columnCount={board.columns.length}
              isOpen={openForm?.kind === 'column'}
              onOpen={() => setOpenForm({ kind: 'column' })}
              onClose={() => closeForm({ kind: 'column' })}
            />
          </div>
        </main>
      </DragDropContext>
      {openCardId !== null && <CardModal cardId={openCardId} onClose={closeCard} />}
      {labelsOpen && <LabelModal onClose={() => setLabelsOpen(false)} />}
    </>
  )
}

// 読み込み中の仮の表示（スケルトン。画面設計書 5章）
function BoardSkeleton() {
  const lists = [
    { titleWidth: '50%', heights: [56, 40, 48] },
    { titleWidth: '40%', heights: [48, 64] },
    { titleWidth: '30%', heights: [40] },
  ]
  return (
    <main className={styles.scroll} aria-busy="true" aria-label="読み込み中">
      <div className={styles.board}>
        {lists.map((list, i) => (
          <div key={i} className={styles.skeletonList} aria-hidden="true">
            <div className={`${styles.skel} ${styles.skelTitle}`} style={{ width: list.titleWidth }} />
            {list.heights.map((height, j) => (
              <div key={j} className={styles.skel} style={{ height }} />
            ))}
          </div>
        ))}
      </div>
    </main>
  )
}
