import { describe, expect, it } from 'vitest'
import type { BoardResponse, CardResponse } from '../../api/types.ts'
import { boardReducer } from './boardReducer.ts'

// テスト用の小さなボード
function sampleBoard(name = 'マイボード'): BoardResponse {
  return {
    id: 1,
    name,
    labels: [{ id: 1, name: '仕事', color: '#3e63dd' }],
    columns: [
      {
        id: 1,
        name: '未着手',
        cards: [
          {
            id: 1,
            title: '会議の資料を準備する',
            description: null,
            importance: 'HIGH',
            urgency: 'HIGH',
            dueDate: '2026-10-31',
            labelIds: [1],
          },
        ],
      },
      { id: 2, name: '作業中', cards: [] },
      { id: 3, name: '完了', cards: [] },
    ],
  }
}

function newCard(id: number, title: string): CardResponse {
  return { id, title, description: null, importance: 'MEDIUM', urgency: 'MEDIUM', dueDate: null, labelIds: [] }
}

describe('boardReducer', () => {
  it('loaded：読み込んだボードがそのまま入る', () => {
    const board = sampleBoard()

    const next = boardReducer(null, { type: 'loaded', board })

    expect(next).toEqual(board)
  })

  it('loaded：読み込み直すと、新しいボードに置き換わる', () => {
    const next = boardReducer(sampleBoard(), { type: 'loaded', board: sampleBoard('仕事用ボード') })

    expect(next?.name).toBe('仕事用ボード')
  })

  it('columnAdded：右端に、カードのないリストが足される', () => {
    const next = boardReducer(sampleBoard(), { type: 'columnAdded', column: { id: 4, name: '保留' } })

    expect(next?.columns.map((column) => column.name)).toEqual(['未着手', '作業中', '完了', '保留'])
    expect(next?.columns[3].cards).toEqual([])
  })

  it('cardAdded：指定したリストの一番下に足される。ほかのリストは変わらない', () => {
    const before = sampleBoard()

    const next = boardReducer(before, { type: 'cardAdded', columnId: 1, card: newCard(2, '牛乳を買う') })

    expect(next?.columns[0].cards.map((card) => card.title)).toEqual(['会議の資料を準備する', '牛乳を買う'])
    expect(next?.columns[1]).toBe(before.columns[1]) // 変わらないリストは同じもののまま
  })

  it('元のデータは書き換えない', () => {
    const before = sampleBoard()

    boardReducer(before, { type: 'cardAdded', columnId: 1, card: newCard(2, '牛乳を買う') })
    boardReducer(before, { type: 'columnAdded', column: { id: 4, name: '保留' } })

    expect(before).toEqual(sampleBoard())
  })

  it('まだ読み込んでいない（null）ときは何もしない', () => {
    expect(boardReducer(null, { type: 'columnAdded', column: { id: 4, name: '保留' } })).toBeNull()
  })
})
