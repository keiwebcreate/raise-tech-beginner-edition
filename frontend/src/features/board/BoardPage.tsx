import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../../shared/lib/constants.ts'
import { CardModal } from '../card/CardModal.tsx'
import { AddColumnForm } from './AddColumnForm.tsx'
import { getBoard } from './boardApi.ts'
import { useBoard } from './BoardContext.ts'
import { BoardHeader } from './BoardHeader.tsx'
import styles from './BoardPage.module.css'
import { Column } from './Column.tsx'

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

  const renameBoardForm: OpenForm = { kind: 'renameBoard' }
  return (
    <>
      <BoardHeader
        boardName={board.name}
        isRenaming={openForm?.kind === 'renameBoard'}
        onStartRename={() => setOpenForm(renameBoardForm)}
        onEndRename={() => closeForm(renameBoardForm)}
      />
      <main className={styles.scroll}>
        <div className={styles.board}>
          {board.columns.map((column) => {
            const addCardForm: OpenForm = { kind: 'card', columnId: column.id }
            const renameForm: OpenForm = { kind: 'renameColumn', columnId: column.id }
            return (
              <Column
                key={column.id}
                column={column}
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
          <AddColumnForm
            columnCount={board.columns.length}
            isOpen={openForm?.kind === 'column'}
            onOpen={() => setOpenForm({ kind: 'column' })}
            onClose={() => closeForm({ kind: 'column' })}
          />
        </div>
      </main>
      {openCardId !== null && <CardModal cardId={openCardId} onClose={closeCard} />}
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
