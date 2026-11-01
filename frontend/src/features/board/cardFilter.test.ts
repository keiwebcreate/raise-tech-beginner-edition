import { describe, expect, it } from 'vitest'
import type { CardResponse } from '../../api/types.ts'
import {
  countConditions,
  EMPTY_FILTER,
  isFilterActive,
  matchesCard,
  normalizeText,
  parseFilter,
  searchWords,
  toSearchParams,
  withKnownLabels,
  type CardFilter,
} from './cardFilter.ts'

// テストの「今日」は 2026-10-28
const TODAY = new Date(2026, 9, 28)

function card(overrides: Partial<CardResponse> = {}): CardResponse {
  return {
    id: 1,
    title: 'カード',
    description: null,
    importance: 'MEDIUM',
    urgency: 'MEDIUM',
    dueDate: null,
    labelIds: [],
    ...overrides,
  }
}

function filter(overrides: Partial<CardFilter>): CardFilter {
  return { ...EMPTY_FILTER, ...overrides }
}

describe('normalizeText（文字のそろえ方）', () => {
  it('全角の英数字を半角に、大文字を小文字にする', () => {
    expect(normalizeText('ＰＤＦの資料１')).toBe('pdfの資料1')
  })

  it('カタカナをひらがなにする（半角カタカナも）', () => {
    expect(normalizeText('チェック')).toBe('ちぇっく')
    expect(normalizeText('ﾁｪｯｸ')).toBe('ちぇっく')
    expect(normalizeText('ヴァイオリン')).toBe('ゔぁいおりん')
  })

  it('漢字・長音・記号はそのまま', () => {
    expect(normalizeText('会議ー！')).toBe('会議ー!')
  })
})

describe('searchWords（言葉に分ける）', () => {
  it('半角・全角の空白で区切り、前後と続く空白は無視する', () => {
    expect(searchWords('  資料　会議  pdf ')).toEqual(['資料', '会議', 'pdf'])
  })

  it('空白だけなら言葉なし', () => {
    expect(searchWords(' 　 ')).toEqual([])
  })
})

describe('matchesCard（文字の検索）', () => {
  it('タイトルか説明文に含まれていれば合う', () => {
    expect(matchesCard(card({ title: '会議の資料' }), filter({ query: '資料' }), TODAY)).toBe(true)
    expect(matchesCard(card({ description: 'https://example.com を見る' }), filter({ query: 'example' }), TODAY)).toBe(
      true,
    )
    expect(matchesCard(card({ title: '会議の資料' }), filter({ query: '買い物' }), TODAY)).toBe(false)
  })

  it('ひらがな・全角・大文字の違いをそろえて探す', () => {
    const target = card({ title: 'ＰＤＦのチェック' })
    expect(matchesCard(target, filter({ query: 'pdf' }), TODAY)).toBe(true)
    expect(matchesCard(target, filter({ query: 'ちぇっく' }), TODAY)).toBe(true)
  })

  it('空白で区切った言葉は全部含むときだけ合う（タイトルと説明文にまたがってもよい）', () => {
    const target = card({ title: '会議の資料', description: '金曜までに印刷' })
    expect(matchesCard(target, filter({ query: '資料 印刷' }), TODAY)).toBe(true)
    expect(matchesCard(target, filter({ query: '資料 旅行' }), TODAY)).toBe(false)
  })
})

describe('matchesCard（絞り込み）', () => {
  it('ラベルは選んだどれかが付いていれば合う。「ラベルなし」はラベルのないカード', () => {
    const work = card({ labelIds: [1] })
    const shopping = card({ labelIds: [2] })
    const none = card({ labelIds: [] })
    const both = filter({ labelIds: [1, 2] })
    expect([work, shopping, none].map((c) => matchesCard(c, both, TODAY))).toEqual([true, true, false])
    const workOrNone = filter({ labelIds: [1], noLabel: true })
    expect([work, shopping, none].map((c) => matchesCard(c, workOrNone, TODAY))).toEqual([true, false, true])
  })

  it('期限は、期限切れ・今日と明日・それより先・期限なしで分ける（カードの色分けと同じ）', () => {
    const cards = [
      card({ dueDate: '2026-10-27' }), // 昨日：期限切れ
      card({ dueDate: '2026-10-28' }), // 今日
      card({ dueDate: '2026-10-29' }), // 明日
      card({ dueDate: '2026-10-30' }), // あさって：それより先
      card({ dueDate: null }),
    ]
    const result = (f: CardFilter) => cards.map((c) => matchesCard(c, f, TODAY))
    expect(result(filter({ due: ['overdue'] }))).toEqual([true, false, false, false, false])
    expect(result(filter({ due: ['soon'] }))).toEqual([false, true, true, false, false])
    expect(result(filter({ due: ['later'] }))).toEqual([false, false, false, true, false])
    expect(result(filter({ due: ['none'] }))).toEqual([false, false, false, false, true])
    expect(result(filter({ due: ['overdue', 'none'] }))).toEqual([true, false, false, false, true])
  })

  it('重要度・緊急度は選んだどれかなら合う', () => {
    const high = card({ importance: 'HIGH', urgency: 'LOW' })
    expect(matchesCard(high, filter({ importance: ['HIGH', 'MEDIUM'] }), TODAY)).toBe(true)
    expect(matchesCard(high, filter({ importance: ['LOW'] }), TODAY)).toBe(false)
    expect(matchesCard(high, filter({ urgency: ['LOW'] }), TODAY)).toBe(true)
    expect(matchesCard(high, filter({ urgency: ['HIGH'] }), TODAY)).toBe(false)
  })

  it('種類どうしは全部に合うときだけ合う（ラベル「仕事」か「買い物」、かつ重要度「高」、かつ文字）', () => {
    const f = filter({ query: '資料', labelIds: [1, 2], importance: ['HIGH'] })
    expect(matchesCard(card({ title: '資料', labelIds: [2], importance: 'HIGH' }), f, TODAY)).toBe(true)
    expect(matchesCard(card({ title: '資料', labelIds: [2], importance: 'LOW' }), f, TODAY)).toBe(false)
    expect(matchesCard(card({ title: '資料', labelIds: [3], importance: 'HIGH' }), f, TODAY)).toBe(false)
    expect(matchesCard(card({ title: '旅行', labelIds: [1], importance: 'HIGH' }), f, TODAY)).toBe(false)
  })

  it('条件がなければ全部合う', () => {
    expect(matchesCard(card(), EMPTY_FILTER, TODAY)).toBe(true)
  })
})

describe('URL との読み書き', () => {
  it('条件を URL にして、読み戻すと同じになる', () => {
    const f: CardFilter = {
      query: '資料 印刷',
      labelIds: [3, 5],
      noLabel: true,
      due: ['overdue', 'none'],
      importance: ['HIGH'],
      urgency: ['MEDIUM', 'LOW'],
    }
    const params = toSearchParams(f)
    expect(params.toString()).toBe(
      'q=%E8%B3%87%E6%96%99+%E5%8D%B0%E5%88%B7&label=3&label=5&label=none&due=overdue&due=none&importance=HIGH&urgency=MEDIUM&urgency=LOW',
    )
    expect(parseFilter(params)).toEqual(f)
  })

  it('条件がなければ URL は空', () => {
    expect(toSearchParams(EMPTY_FILTER).toString()).toBe('')
    expect(toSearchParams(filter({ query: '   ' })).toString()).toBe('')
  })

  it('知らない値・重なった値は無視する（URL は手で書き換えられるため）', () => {
    const params = new URLSearchParams('label=abc&label=0&label=3&label=3&due=tomorrow&importance=high&urgency=LOW')
    expect(parseFilter(params)).toEqual(filter({ labelIds: [3], urgency: ['LOW'] }))
  })
})

describe('条件の数', () => {
  it('ボタンに出す数は、選んだ条件の数（検索の文字は数えない）', () => {
    expect(countConditions(filter({ query: '資料', labelIds: [1, 2], noLabel: true, due: ['none'] }))).toBe(4)
  })

  it('空白だけの検索は、条件なしとみなす', () => {
    expect(isFilterActive(filter({ query: '  ' }))).toBe(false)
    expect(isFilterActive(filter({ query: '資料' }))).toBe(true)
    expect(isFilterActive(filter({ urgency: ['HIGH'] }))).toBe(true)
  })
})

describe('withKnownLabels（ボードにないラベルを外す）', () => {
  const labels = [
    { id: 1, name: '仕事', color: '#3e63dd' },
    { id: 2, name: '買い物', color: '#30a46c' },
  ]

  it('削除したラベル（ボードにない ID）は条件から外す', () => {
    expect(withKnownLabels(filter({ labelIds: [1, 9], noLabel: true }), labels)).toEqual(
      filter({ labelIds: [1], noLabel: true }),
    )
  })

  it('全部ボードにあれば、同じ条件をそのまま返す（作り直さない）', () => {
    const f = filter({ labelIds: [1, 2] })
    expect(withKnownLabels(f, labels)).toBe(f)
  })
})
