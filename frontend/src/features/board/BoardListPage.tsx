import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import type { BoardSummaryResponse } from '../../api/types.ts'
import { LIMITS, MAX_LENGTH, MESSAGES } from '../../shared/lib/constants.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { InlineForm } from '../../shared/ui/InlineForm.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import * as boardApi from './boardApi.ts'
import { BoardHeader } from './BoardHeader.tsx'
import styles from './BoardListPage.module.css'

// W-11 ボード一覧（/。要件 B-02・B-03。イシュー #51）
// ボード名のタイルを作った順に並べ、押すとそのボード（/boards/{id}）を開く。最後に「＋ ボードを追加」
// 一覧のデータはこの画面だけで使うので、BoardContext ではなく useState で持つ
export function BoardListPage() {
  const navigate = useNavigate()
  const { showToast, showApiError } = useToast()
  const [boards, setBoards] = useState<BoardSummaryResponse[] | null>(null) // null：読み込み中
  const [loadError, setLoadError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false) // 「＋ ボードを追加」の入力欄を開いているか

  // 画面を開いたときに、一覧を1回読み込む
  useEffect(() => {
    let ignore = false // 画面を閉じたあとに結果が返ってきても、何もしないようにする目印
    boardApi
      .listBoards()
      .then((loaded) => {
        if (!ignore) setBoards(loaded)
      })
      .catch((error: unknown) => {
        if (!ignore) setLoadError(error instanceof ApiError ? error.detail : MESSAGES.network)
      })
    return () => {
      ignore = true
    }
  }, [])

  const isFull = boards !== null && boards.length >= LIMITS.boards

  function handleOpen() {
    // 上限に達していたら、入力欄を開かずに知らせる（「＋ リストを追加」と同じ。画面設計書 W-03）
    if (isFull) {
      showToast(MESSAGES.boardLimit)
      return
    }
    setAdding(true)
  }

  // A-23 ボードの作成。作ったら、そのボードを開く
  async function handleSubmit(value: string): Promise<string | null> {
    const error = validateText(value, 'ボード名', MAX_LENGTH.boardName)
    if (error) return error
    try {
      const created = await boardApi.createBoard(value.trim())
      navigate(`/boards/${created.id}`)
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
    <>
      <BoardHeader />
      <main className={styles.page}>
        <h1 className={styles.title}>ボード一覧</h1>
        {loadError ? (
          <div className={styles.message}>
            <p>ボードを読み込めませんでした</p>
            <p className="muted">{loadError}</p>
            <button type="button" className="btn btn-primary" onClick={() => location.reload()}>
              再読み込み
            </button>
          </div>
        ) : boards === null ? (
          <BoardListSkeleton />
        ) : (
          <>
            {boards.length === 0 && (
              <p className={`muted ${styles.empty}`}>ボードがありません。「＋ ボードを追加」から作ってください。</p>
            )}
            <ul className={styles.grid}>
              {boards.map((board) => (
                <li key={board.id}>
                  <Link to={`/boards/${board.id}`} className={styles.tile}>
                    {board.name}
                  </Link>
                </li>
              ))}
              <li>
                {adding ? (
                  <div className={styles.formBox}>
                    <InlineForm
                      label="ボード名"
                      placeholder="ボード名を入力"
                      submitText="ボードを追加"
                      onSubmit={handleSubmit}
                      onClose={() => setAdding(false)}
                    />
                  </div>
                ) : (
                  <button type="button" className={styles.add} onClick={handleOpen}>
                    ＋ ボードを追加
                  </button>
                )}
              </li>
            </ul>
          </>
        )}
      </main>
    </>
  )
}

// 読み込み中の仮の表示（スケルトン。画面設計書 5章）
function BoardListSkeleton() {
  return (
    <div className={styles.grid} aria-busy="true" aria-label="読み込み中">
      {[0, 1, 2].map((i) => (
        <div key={i} className={`${styles.tile} ${styles.skel}`} aria-hidden="true" />
      ))}
    </div>
  )
}
