import {
  DragDropContext,
  Droppable,
  type DragStart,
  type DragUpdate,
  type DropResult,
  type ResponderProvided,
} from '@hello-pangea/dnd'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import type { BoardResponse } from '../../api/types.ts'
import { LIMITS, MESSAGES } from '../../shared/lib/constants.ts'
import { NotFoundPage } from '../../shared/pages/NotFoundPage.tsx'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { moveCard } from '../card/cardApi.ts'
import { CardModal } from '../card/CardModal.tsx'
import { LabelModal } from '../label/LabelModal.tsx'
import { AddColumnForm } from './AddColumnForm.tsx'
import * as boardApi from './boardApi.ts'
import { useBoard } from './BoardContext.ts'
import { BoardHeader } from './BoardHeader.tsx'
import styles from './BoardPage.module.css'
import { isFilterActive, matchesCard } from './cardFilter.ts'
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
import { FilterBar } from './FilterBar.tsx'
import { useCardFilter } from './useCardFilter.ts'

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

// W-03 ボード画面（/boards/{boardId}）
// ボードを切り替えたときは、App.tsx で key を変えて作り直す（前のボードのデータや入力欄を残さないため）
export function BoardPage({ boardId }: { boardId: number }) {
  const { board, dispatch } = useBoard()
  const navigate = useNavigate()
  const [loadError, setLoadError] = useState<string | null>(null)
  const [notFound, setNotFound] = useState(false) // 他人のボード・存在しないボード（W-07 を出す）
  const [confirmingDelete, setConfirmingDelete] = useState(false) // ボードの削除の確認ダイアログ
  const [openForm, setOpenForm] = useState<OpenForm>(null)
  const [openCardId, setOpenCardId] = useState<number | null>(null) // カード詳細を開いているカード
  const closeCard = useCallback(() => setOpenCardId(null), [])
  const [labelsOpen, setLabelsOpen] = useState(false) // ヘッダーから開いたラベル管理（W-05）
  // ドラッグの保存（A-11・A-15）を待っている間は、次のドラッグをさせない
  // （続けて動かしたあとで前の保存が失敗すると、戻す先の「動かす前」が古くなってしまうため）
  const [savingMove, setSavingMove] = useState(false)
  const { showToast, showApiError } = useToast()

  // 検索・絞り込み（要件 S-01〜S-04）。条件は URL から読む（ボードにないラベルは外す）
  const { filter, clear: clearFilter } = useCardFilter(board?.labels)
  const filtering = isFilterActive(filter)
  // 絞り込み中はカードを追加しないので、開いていた「＋ カードを追加」の入力欄は閉じる
  // （閉じないと、条件をクリアしたときに入力欄が開いたまま戻ってくる）
  // React の「描くときに直す」書き方。useEffect で閉じると1回余分に描き直すため
  if (filtering && openForm?.kind === 'card') {
    setOpenForm(null)
  }
  // 絞り込み中だけ、リストごとに条件に合うカードを選んでおく（絞り込んでいないときは null）
  // 期限の「今日」は matchesCard の中で決める（描く処理の中で new Date() を呼ばない。カードの期限の色分けと同じ）
  const visibleCards = useMemo(() => {
    if (!board || !filtering) return null
    return new Map(board.columns.map((column) => [column.id, column.cards.filter((c) => matchesCard(c, filter))]))
  }, [board, filter, filtering])

  // その入力欄がまだ開いているときだけ閉じる
  // （名前の保存を待っている間に、別の入力欄を開いていたら、そちらは閉じない）
  const closeForm = (form: OpenForm) => setOpenForm((current) => (isSameForm(current, form) ? null : current))

  // 画面を開いたときに、ボードを1回読み込む
  useEffect(() => {
    // 画面を閉じたあとに結果が返ってきても、何もしないようにする目印
    let ignore = false
    boardApi
      .getBoard(boardId)
      .then((loaded) => {
        if (!ignore) dispatch({ type: 'loaded', board: loaded })
      })
      .catch((error: unknown) => {
        if (ignore) return
        // 他人のボード・存在しない（消した）ボードの URL は「ページが見つかりません」（画面設計書 W-07）
        if (error instanceof ApiError && error.code === 'NOT_FOUND') {
          setNotFound(true)
        } else {
          setLoadError(error instanceof ApiError ? error.detail : MESSAGES.network)
        }
      })
    return () => {
      ignore = true
    }
  }, [dispatch, boardId])

  if (notFound) {
    return <NotFoundPage />
  }

  if (loadError) {
    return (
      <>
        <BoardHeader backToList />
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
        <BoardHeader backToList />
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

  // A-07 ボード名の変更（要件 B-01）
  async function renameBoard(name: string): Promise<string | null> {
    if (!board) return ''
    try {
      const saved = await boardApi.renameBoard(board.id, name)
      dispatch({ type: 'boardRenamed', name: saved.name })
      return null
    } catch (e) {
      if (e instanceof ApiError && e.code === 'VALIDATION_ERROR') {
        return fieldErrorsOf(e).name ?? e.detail
      }
      showApiError(e)
      return ''
    }
  }

  // A-24 ボードの削除（要件 B-04）。消したら一覧へ戻り、通知を出す
  async function deleteBoard() {
    if (!board) return
    try {
      await boardApi.deleteBoard(board.id)
      navigate('/', { replace: true })
      showToast(MESSAGES.boardDeleted, 'success')
    } catch (e) {
      showApiError(e)
    }
  }

  const renameBoardForm: OpenForm = { kind: 'renameBoard' }
  return (
    <>
      <BoardHeader
        backToList
        boardName={board.name}
        isRenaming={openForm?.kind === 'renameBoard'}
        onStartRename={() => setOpenForm(renameBoardForm)}
        onEndRename={() => closeForm(renameBoardForm)}
        onRename={renameBoard}
        onDeleteBoard={() => setConfirmingDelete(true)}
        onOpenLabels={() => setLabelsOpen(true)}
        labels={board.labels}
      />
      {visibleCards && (
        <FilterBar
          matched={[...visibleCards.values()].reduce((sum, cards) => sum + cards.length, 0)}
          total={board.columns.reduce((sum, column) => sum + column.cards.length, 0)}
          onClear={clearFilter}
        />
      )}
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
                        visibleCards={visibleCards?.get(column.id)}
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
      {confirmingDelete && (
        <ConfirmDialog
          title="ボードを削除しますか？"
          message={`「${board.name}」と中のリスト・カードをすべて削除します。ラベルはほかのボードで使えるので残ります。この操作は取り消せません。`}
          onAnswer={(ok) => {
            setConfirmingDelete(false)
            if (ok) deleteBoard()
          }}
        />
      )}
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
