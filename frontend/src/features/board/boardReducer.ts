// ボードのデータの変え方を、この1か所にまとめる（詳細設計書 4.4）
// 「どんな出来事（action）が起きたか」を受け取り、新しいボードのデータを返す
// 元のデータは書き換えず、変わったところだけを新しく作る（React が「変わった」と気づけるように）

import type { BoardResponse, CardResponse, ColumnResponse, LabelResponse } from '../../api/types.ts'

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
  // A-10 リストの削除が成功した（中のカードも一緒に消える）
  | { type: 'columnDeleted'; columnId: number }
  // A-11 リストをドラッグで動かした（API の返事を待たずに先に変える。position は移動したあとの並びで左から何番目か）
  | { type: 'columnMoved'; columnId: number; position: number }
  // A-12 カードの追加が成功した（そのリストの一番下に足す）
  | { type: 'cardAdded'; columnId: number; card: CardResponse }
  // A-13 カードの保存が成功した（同じ ID のカードを置き換える。場所は変わらない）
  | { type: 'cardSaved'; card: CardResponse }
  // A-14 カードの削除が成功した
  | { type: 'cardDeleted'; cardId: number }
  // A-15 カードをドラッグで動かした（先に変える。toColumnId は移動先のリスト、position は移動先で上から何番目か）
  | { type: 'cardMoved'; cardId: number; toColumnId: number; position: number }
  // ドラッグの保存（A-11・A-15）に失敗したので、動かす前のボードに戻す
  | { type: 'restored'; board: BoardResponse }
  // A-16 ラベルの追加が成功した（一覧の最後に足す。作成順）
  | { type: 'labelAdded'; label: LabelResponse }
  // A-17 ラベルの変更が成功した（同じ ID のラベルを置き換える）
  | { type: 'labelUpdated'; label: LabelResponse }
  // A-18 ラベルの削除が成功した（付いていたカードからも外す。要件 T-03）
  | { type: 'labelDeleted'; labelId: number }

export function boardReducer(state: BoardState, action: BoardAction): BoardState {
  if (action.type === 'loaded' || action.type === 'restored') return action.board
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

    case 'columnDeleted':
      return { ...state, columns: state.columns.filter((column) => column.id !== action.columnId) }

    case 'columnMoved': {
      const column = state.columns.find((c) => c.id === action.columnId)
      if (!column) return state
      return { ...state, columns: moveTo(state.columns, column, action.position) }
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

    case 'cardDeleted':
      return {
        ...state,
        columns: state.columns.map((column) =>
          column.cards.some((card) => card.id === action.cardId)
            ? { ...column, cards: column.cards.filter((card) => card.id !== action.cardId) }
            : column,
        ),
      }

    case 'cardMoved': {
      const from = state.columns.find((column) => column.cards.some((card) => card.id === action.cardId))
      const card = from?.cards.find((c) => c.id === action.cardId)
      if (!from || !card || !state.columns.some((column) => column.id === action.toColumnId)) return state
      return {
        ...state,
        columns: state.columns.map((column) => {
          // 同じリストの中なら、取り除いて差し込む
          if (column.id === from.id && column.id === action.toColumnId) {
            return { ...column, cards: moveTo(column.cards, card, action.position) }
          }
          // 移動元からは取り除き、移動先には差し込む
          if (column.id === from.id) return { ...column, cards: column.cards.filter((c) => c.id !== card.id) }
          if (column.id === action.toColumnId)
            return { ...column, cards: insertAt(column.cards, card, action.position) }
          return column
        }),
      }
    }

    case 'labelAdded':
      return { ...state, labels: [...state.labels, action.label] }

    case 'labelUpdated':
      return {
        ...state,
        labels: state.labels.map((label) => (label.id === action.label.id ? action.label : label)),
      }

    case 'labelDeleted':
      return {
        ...state,
        labels: state.labels.filter((label) => label.id !== action.labelId),
        // そのラベルが付いていたカードだけを作り直す
        columns: state.columns.map((column) =>
          column.cards.some((card) => card.labelIds.includes(action.labelId))
            ? {
                ...column,
                cards: column.cards.map((card) =>
                  card.labelIds.includes(action.labelId)
                    ? { ...card, labelIds: card.labelIds.filter((id) => id !== action.labelId) }
                    : card,
                ),
              }
            : column,
        ),
      }
  }
}

// item を取り除いてから、position 番目に差し込んだ新しい配列を返す（サーバーの並び替えと同じ数え方。API設計書 A-11・A-15）
function moveTo<T>(items: T[], item: T, position: number): T[] {
  return insertAt(
    items.filter((i) => i !== item),
    item,
    position,
  )
}

// position 番目に差し込んだ新しい配列を返す
function insertAt<T>(items: T[], item: T, position: number): T[] {
  return [...items.slice(0, position), item, ...items.slice(position)]
}
