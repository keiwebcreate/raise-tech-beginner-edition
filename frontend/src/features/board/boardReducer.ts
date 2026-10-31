// ボードのデータの変え方を、この1か所にまとめる（詳細設計書 4.4）
// 「どんな出来事（action）が起きたか」を受け取り、新しいボードのデータを返す
// 今は読み込み（loaded）だけ。追加・変更・削除・移動は、それぞれの機能を作るときに足す

import type { BoardResponse } from '../../api/types.ts'

// まだ読み込んでいないときは null
export type BoardState = BoardResponse | null

export type BoardAction =
  // GET /api/board が成功した
  { type: 'loaded'; board: BoardResponse }

export function boardReducer(_state: BoardState, action: BoardAction): BoardState {
  switch (action.type) {
    case 'loaded':
      return action.board
  }
}
