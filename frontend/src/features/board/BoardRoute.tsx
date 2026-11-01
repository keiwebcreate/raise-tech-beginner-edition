import { useParams } from 'react-router'
import { NotFoundPage } from '../../shared/pages/NotFoundPage.tsx'
import { BoardPage } from './BoardPage.tsx'
import { BoardProvider } from './BoardProvider.tsx'

// /boards/{boardId} の URL からボードの ID を取り出して、ボード画面を出す（詳細設計書 4.2）
// - ID が数字でない・大きすぎる（JavaScript で正しく扱える整数を超える）ときは「ページが見つかりません」（W-07）
//   大きすぎる数字をそのまま送ると、サーバーが 400 を返し「ボードを読み込めませんでした」になってしまうため（品質チェック #51）
// - key に ID を入れる：別のボードへ移ったときに、前のボードのデータ・開いていた入力欄・モーダルを残さず作り直す
export function BoardRoute() {
  const { boardId } = useParams()
  const id = Number(boardId)
  if (!boardId || !/^\d+$/.test(boardId) || !Number.isSafeInteger(id)) return <NotFoundPage />
  return (
    <BoardProvider key={id}>
      <BoardPage boardId={id} />
    </BoardProvider>
  )
}
