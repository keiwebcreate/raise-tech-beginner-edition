// ドラッグ＆ドロップ（@hello-pangea/dnd。詳細設計書 4.5）で使う名前と、読み上げの文言
// ライブラリは、動かすもの（Draggable）と置き場所（Droppable）を文字列の ID で見分ける

import type { DragStart, DragUpdate, DropResult } from '@hello-pangea/dnd'
import type { BoardResponse } from '../../api/types.ts'

// 置き場所の種類。リストはリストの並びにだけ、カードはカードの並びにだけ置ける
export const DRAG_TYPE = { column: 'COLUMN', card: 'CARD' } as const

// リストの並び（横）の置き場所。ボードに1つだけ
export const BOARD_DROPPABLE_ID = 'board'

export const columnDraggableId = (columnId: number) => `column-${columnId}`
export const cardDraggableId = (cardId: number) => `card-${cardId}`
// カードの並び（縦）の置き場所。リストごとに1つ
export const cardsDroppableId = (columnId: number) => `cards-${columnId}`

// 'card-12' → 12
export function idFrom(dndId: string): number {
  return Number(dndId.slice(dndId.indexOf('-') + 1))
}

// キーボードでの動かし方（つかめるところにカーソルを合わせたときに読み上げる）
export const DRAG_INSTRUCTIONS =
  'スペースキーでつかみ、矢印キーで動かし、もう一度スペースキーで置きます。Esc キーでやめます。'

// 以下は、動かしている間の読み上げ（ライブラリの標準は英語なので、日本語にする）

export function startMessage(board: BoardResponse, start: DragStart): string {
  return `${nameOf(board, start)}をつかみました。`
}

export function updateMessage(board: BoardResponse, update: DragUpdate): string {
  if (!update.destination) return '置ける場所の外です。'
  return `${placeOf(board, update.destination.droppableId, update.destination.index)}に動かしました。`
}

export function endMessage(board: BoardResponse, result: DropResult): string {
  if (result.reason === 'CANCEL') return `${nameOf(board, result)}の移動をやめ、元の位置に戻しました。`
  if (!result.destination) return `置ける場所の外なので、${nameOf(board, result)}を元の位置に戻しました。`
  return `${nameOf(board, result)}を${placeOf(board, result.destination.droppableId, result.destination.index)}に置きました。`
}

// 「リスト「未着手」」「カード「会議の資料」」
function nameOf(board: BoardResponse, start: DragStart): string {
  const id = idFrom(start.draggableId)
  if (start.type === DRAG_TYPE.column) {
    return `リスト「${board.columns.find((column) => column.id === id)?.name ?? ''}」`
  }
  const card = board.columns.flatMap((column) => column.cards).find((c) => c.id === id)
  return `カード「${card?.title ?? ''}」`
}

// 「左から2番目」「リスト「作業中」の上から1番目」
function placeOf(board: BoardResponse, droppableId: string, index: number): string {
  if (droppableId === BOARD_DROPPABLE_ID) return `左から${index + 1}番目`
  const column = board.columns.find((c) => c.id === idFrom(droppableId))
  return `リスト「${column?.name ?? ''}」の上から${index + 1}番目`
}
