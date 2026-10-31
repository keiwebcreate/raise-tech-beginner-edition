// ボードのデータの変え方を、この1か所にまとめる（詳細設計書 4.4）
// 「どんな出来事（action）が起きたか」を受け取り、新しいボードのデータを返す
// 元のデータは書き換えず、変わったところだけを新しく作る（React が「変わった」と気づけるように）

import type { BoardResponse, CardResponse, ColumnResponse } from '../../api/types.ts'

// まだ読み込んでいないときは null
export type BoardState = BoardResponse | null

export type BoardAction =
  // GET /api/board が成功した
  | { type: 'loaded'; board: BoardResponse }
  // A-07 ボード名の変更が成功した
  | { type: 'boardRenamed'; name: string }
  // A-08 リストの追加が成功した（右端に足す）
  | { type: 'columnAdded'; column: ColumnResponse }
  // A-09 リスト名の変更が成功した
  | { type: 'columnRenamed'; column: ColumnResponse }
  // A-12 カードの追加が成功した（そのリストの一番下に足す）
  | { type: 'cardAdded'; columnId: number; card: CardResponse }
  // A-13 カードの保存が成功した（同じ ID のカードを置き換える。場所は変わらない）
  | { type: 'cardSaved'; card: CardResponse }

export function boardReducer(state: BoardState, action: BoardAction): BoardState {
  if (action.type === 'loaded') return action.board
  if (!state) return state // まだ読み込んでいないときは何もしない

  switch (action.type) {
    case 'boardRenamed':
      return { ...state, name: action.name }

    case 'columnAdded':
      return { ...state, columns: [...state.columns, { ...action.column, cards: [] }] }

    case 'columnRenamed':
      return {
        ...state,
        columns: state.columns.map((column) =>
          column.id === action.column.id ? { ...column, name: action.column.name } : column,
        ),
      }

    case 'cardAdded':
      return {
        ...state,
        columns: state.columns.map((column) =>
          column.id === action.columnId ? { ...column, cards: [...column.cards, action.card] } : column,
        ),
      }

    case 'cardSaved':
      return {
        ...state,
        columns: state.columns.map((column) =>
          column.cards.some((card) => card.id === action.card.id)
            ? { ...column, cards: column.cards.map((card) => (card.id === action.card.id ? action.card : card)) }
            : column,
        ),
      }
  }
}
