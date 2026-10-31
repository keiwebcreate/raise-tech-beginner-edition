// ボードのデータの変え方を、この1か所にまとめる（詳細設計書 4.4）
// 「どんな出来事（action）が起きたか」を受け取り、新しいボードのデータを返す
// 元のデータは書き換えず、変わったところだけを新しく作る（React が「変わった」と気づけるように）

import type { BoardResponse, CardResponse, ColumnResponse } from '../../api/types.ts'

// まだ読み込んでいないときは null
export type BoardState = BoardResponse | null

export type BoardAction =
  // GET /api/board が成功した
  | { type: 'loaded'; board: BoardResponse }
  // A-08 リストの追加が成功した（右端に足す）
  | { type: 'columnAdded'; column: ColumnResponse }
  // A-12 カードの追加が成功した（そのリストの一番下に足す）
  | { type: 'cardAdded'; columnId: number; card: CardResponse }

export function boardReducer(state: BoardState, action: BoardAction): BoardState {
  switch (action.type) {
    case 'loaded':
      return action.board

    case 'columnAdded':
      if (!state) return state
      return { ...state, columns: [...state.columns, { ...action.column, cards: [] }] }

    case 'cardAdded':
      if (!state) return state
      return {
        ...state,
        columns: state.columns.map((column) =>
          column.id === action.columnId ? { ...column, cards: [...column.cards, action.card] } : column,
        ),
      }
  }
}
