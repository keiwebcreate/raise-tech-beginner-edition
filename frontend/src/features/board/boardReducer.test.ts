import { describe, expect, it } from 'vitest'
import type { BoardResponse } from '../../api/types.ts'
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
})
