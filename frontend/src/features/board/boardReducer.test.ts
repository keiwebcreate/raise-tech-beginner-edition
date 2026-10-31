import { describe, expect, it } from 'vitest'
import type { BoardResponse, CardResponse } from '../../api/types.ts'
import { type BoardState, boardReducer } from './boardReducer.ts'

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

  it('boardRenamed：ボード名だけが変わる', () => {
    const before = sampleBoard()

    const next = boardReducer(before, { type: 'boardRenamed', name: '仕事用ボード' })

    expect(next?.name).toBe('仕事用ボード')
    expect(next?.columns).toBe(before.columns)
  })

  it('columnRenamed：指定したリストの名前だけが変わり、カードはそのまま', () => {
    const before = sampleBoard()

    const next = boardReducer(before, { type: 'columnRenamed', column: { id: 1, name: 'やること' } })

    expect(next?.columns.map((column) => column.name)).toEqual(['やること', '作業中', '完了'])
    expect(next?.columns[0].cards).toBe(before.columns[0].cards)
  })

  it('cardSaved：同じ ID のカードが置き換わり、場所は変わらない', () => {
    const saved = { ...newCard(1, '会議の資料を仕上げる'), importance: 'LOW' as const, labelIds: [] }

    const next = boardReducer(sampleBoard(), { type: 'cardSaved', card: saved })

    expect(next?.columns[0].cards).toEqual([saved])
    expect(next?.columns[1].cards).toEqual([])
  })

  it('cardDeleted：指定したカードだけが消え、ほかのリストは変わらない', () => {
    const before = boardReducer(sampleBoard(), { type: 'cardAdded', columnId: 1, card: newCard(2, '牛乳を買う') })

    const next = boardReducer(before, { type: 'cardDeleted', cardId: 1 })

    expect(next?.columns[0].cards.map((card) => card.title)).toEqual(['牛乳を買う'])
    expect(next?.columns[1]).toBe(before?.columns[1])
  })

  it('columnDeleted：指定したリストが中のカードごと消え、残りの順番は変わらない', () => {
    const next = boardReducer(sampleBoard(), { type: 'columnDeleted', columnId: 1 })

    expect(next?.columns.map((column) => column.name)).toEqual(['作業中', '完了'])
  })

  it('labelAdded：ラベルが一覧の最後に足される', () => {
    const next = boardReducer(sampleBoard(), { type: 'labelAdded', label: { id: 2, name: '急ぎ', color: '#e5484d' } })

    expect(next?.labels.map((label) => label.name)).toEqual(['仕事', '急ぎ'])
  })

  it('labelUpdated：同じ ID のラベルの名前と色が変わり、カードはそのまま', () => {
    const before = sampleBoard()
    const updated = { id: 1, name: '会社', color: '#30a46c' }

    const next = boardReducer(before, { type: 'labelUpdated', label: updated })

    expect(next?.labels).toEqual([updated])
    expect(next?.columns).toBe(before.columns)
  })

  it('labelDeleted：ラベルが消え、付いていたカードからも外れる。付いていないリストは変わらない', () => {
    const withTwoLabels = boardReducer(sampleBoard(), {
      type: 'labelAdded',
      label: { id: 2, name: '急ぎ', color: '#e5484d' },
    })
    const before = boardReducer(withTwoLabels, {
      type: 'cardSaved',
      card: { ...newCard(1, '会議の資料を準備する'), labelIds: [1, 2] },
    })

    const next = boardReducer(before, { type: 'labelDeleted', labelId: 1 })

    expect(next?.labels.map((label) => label.id)).toEqual([2])
    expect(next?.columns[0].cards[0].labelIds).toEqual([2])
    expect(next?.columns[1]).toBe(before?.columns[1])
  })

  // 「未着手」に A, B, C、「作業中」に X, Y があるボード
  function boardForMove(): BoardResponse {
    const board = sampleBoard()
    board.columns[0].cards = [newCard(11, 'A'), newCard(12, 'B'), newCard(13, 'C')]
    board.columns[1].cards = [newCard(21, 'X'), newCard(22, 'Y')]
    return board
  }

  function titles(state: BoardState, columnIndex: number): string[] {
    return state?.columns[columnIndex].cards.map((card) => card.title) ?? []
  }

  it('columnMoved：リストを右へ動かせる（API設計書 A-11 の例：A を 2 へ → B, C, A）', () => {
    const next = boardReducer(sampleBoard(), { type: 'columnMoved', columnId: 1, position: 2 })

    expect(next?.columns.map((column) => column.name)).toEqual(['作業中', '完了', '未着手'])
  })

  it('columnMoved：リストを左へ動かせる。中のカードはそのまま', () => {
    const before = sampleBoard()

    const next = boardReducer(before, { type: 'columnMoved', columnId: 3, position: 0 })

    expect(next?.columns.map((column) => column.name)).toEqual(['完了', '未着手', '作業中'])
    expect(next?.columns[1]).toBe(before.columns[0])
  })

  it('cardMoved：同じリストの中で、下へも上へも動かせる', () => {
    const down = boardReducer(boardForMove(), { type: 'cardMoved', cardId: 11, toColumnId: 1, position: 2 })
    const up = boardReducer(boardForMove(), { type: 'cardMoved', cardId: 13, toColumnId: 1, position: 0 })

    expect(titles(down, 0)).toEqual(['B', 'C', 'A'])
    expect(titles(up, 0)).toEqual(['C', 'A', 'B'])
  })

  it('cardMoved：別のリストの間に移すと、移動元から消えて移動先に入る。関係ないリストは変わらない', () => {
    const before = boardForMove()

    // API設計書 A-15 の例と同じ形：「X, Y」へ B を 1 で移す → X, B, Y
    const next = boardReducer(before, { type: 'cardMoved', cardId: 12, toColumnId: 2, position: 1 })

    expect(titles(next, 0)).toEqual(['A', 'C'])
    expect(titles(next, 1)).toEqual(['X', 'B', 'Y'])
    expect(next?.columns[2]).toBe(before.columns[2])
  })

  it('cardMoved：一番下や空のリストにも移せる', () => {
    const toBottom = boardReducer(boardForMove(), { type: 'cardMoved', cardId: 11, toColumnId: 2, position: 2 })
    const toEmpty = boardReducer(boardForMove(), { type: 'cardMoved', cardId: 11, toColumnId: 3, position: 0 })

    expect(titles(toBottom, 1)).toEqual(['X', 'Y', 'A'])
    expect(titles(toEmpty, 2)).toEqual(['A'])
  })

  it('cardMoved：カードか移動先のリストが見つからなければ何もしない', () => {
    const before = boardForMove()

    expect(boardReducer(before, { type: 'cardMoved', cardId: 99, toColumnId: 2, position: 0 })).toBe(before)
    expect(boardReducer(before, { type: 'cardMoved', cardId: 11, toColumnId: 99, position: 0 })).toBe(before)
  })

  it('restored：動かす前のボードに戻る', () => {
    const before = boardForMove()
    const moved = boardReducer(before, { type: 'cardMoved', cardId: 11, toColumnId: 2, position: 0 })

    const next = boardReducer(moved, { type: 'restored', board: before })

    expect(next).toBe(before)
  })

  it('まだ読み込んでいない（null）ときは何もしない', () => {
    expect(boardReducer(null, { type: 'columnAdded', column: { id: 4, name: '保留' } })).toBeNull()
  })
})
