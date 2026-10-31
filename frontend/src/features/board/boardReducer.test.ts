import { describe, expect, it } from 'vitest'
import { createSampleBoard } from '../../mocks/sampleData.ts'
import { boardReducer } from './boardReducer.ts'

describe('boardReducer', () => {
  it('loaded：読み込んだボードがそのまま入る', () => {
    const board = createSampleBoard()

    const next = boardReducer(null, { type: 'loaded', board })

    expect(next).toEqual(board)
  })

  it('loaded：読み込み直すと、新しいボードに置き換わる', () => {
    const before = createSampleBoard()
    const after = { ...createSampleBoard(), name: '仕事用ボード' }

    const next = boardReducer(before, { type: 'loaded', board: after })

    expect(next?.name).toBe('仕事用ボード')
  })
})
