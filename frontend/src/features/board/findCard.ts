import type { BoardResponse, CardResponse, ColumnWithCardsResponse } from '../../api/types.ts'

// ボードの中からカードを探す。見つかったらカードと、入っているリストを返す
export function findCard(
  board: BoardResponse,
  cardId: number,
): { card: CardResponse; column: ColumnWithCardsResponse } | null {
  for (const column of board.columns) {
    const card = column.cards.find((c) => c.id === cardId)
    if (card) return { card, column }
  }
  return null
}
