// カードの検索・絞り込み（要件 S-01〜S-04、詳細設計書 4.8）
// 条件は URL に入れる（例：/?q=資料&label=3&label=none&due=overdue&importance=HIGH）。サーバーには保存しない
// ここには画面を持たない関数だけを置く（テストは cardFilter.test.ts）

import type { CardResponse, LabelResponse, Level } from '../../api/types.ts'
import { dueStatus } from '../../shared/lib/due.ts'

// 期限の区切り。カードの色分け（要件 C-05）と同じ
export type DueFilter = 'overdue' | 'soon' | 'later' | 'none'

export const DUE_FILTERS: { value: DueFilter; text: string }[] = [
  { value: 'overdue', text: '期限切れ' },
  { value: 'soon', text: '今日・明日' },
  { value: 'later', text: 'それより先' },
  { value: 'none', text: '期限なし' },
]

export type CardFilter = {
  query: string // 検索欄に入れたまま（前後の空白も残す。入力欄にそのまま戻すため）
  labelIds: number[]
  noLabel: boolean // 「ラベルなし」
  due: DueFilter[]
  importance: Level[]
  urgency: Level[]
}

export const EMPTY_FILTER: CardFilter = {
  query: '',
  labelIds: [],
  noLabel: false,
  due: [],
  importance: [],
  urgency: [],
}

// URL の名前（?q=…&label=…）
const PARAM = {
  query: 'q',
  label: 'label',
  due: 'due',
  importance: 'importance',
  urgency: 'urgency',
} as const

const NO_LABEL = 'none' // 「ラベルなし」を URL で表す値
const LEVEL_VALUES: readonly string[] = ['HIGH', 'MEDIUM', 'LOW']
const DUE_VALUES: readonly string[] = DUE_FILTERS.map((d) => d.value)

// URL から条件を読む。知らない値・同じ値の重なりは無視する（URL は手で書き換えられるため）
export function parseFilter(params: URLSearchParams): CardFilter {
  const labels = params.getAll(PARAM.label)
  return {
    query: params.get(PARAM.query) ?? '',
    labelIds: unique(labels.filter((v) => /^[1-9][0-9]*$/.test(v)).map(Number)),
    noLabel: labels.includes(NO_LABEL),
    due: unique(params.getAll(PARAM.due).filter((v): v is DueFilter => DUE_VALUES.includes(v))),
    importance: unique(params.getAll(PARAM.importance).filter(isLevel)),
    urgency: unique(params.getAll(PARAM.urgency).filter(isLevel)),
  }
}

// 条件を URL の形にする。空の条件は入れない（何も選んでいなければ、URL は / のまま）
export function toSearchParams(filter: CardFilter): URLSearchParams {
  const params = new URLSearchParams()
  if (filter.query.trim() !== '') params.set(PARAM.query, filter.query)
  for (const id of filter.labelIds) params.append(PARAM.label, String(id))
  if (filter.noLabel) params.append(PARAM.label, NO_LABEL)
  for (const d of filter.due) params.append(PARAM.due, d)
  for (const level of filter.importance) params.append(PARAM.importance, level)
  for (const level of filter.urgency) params.append(PARAM.urgency, level)
  return params
}

// ボードにないラベル（URL を手で書き換えた、絞り込みに使っていたラベルを削除した）を条件から外す
// 外さないと、小窓にチェックの付いた項目がないのに、どのカードも合わなくなる（品質チェック #45）
export function withKnownLabels(filter: CardFilter, labels: LabelResponse[]): CardFilter {
  const known = filter.labelIds.filter((id) => labels.some((label) => label.id === id))
  return known.length === filter.labelIds.length ? filter : { ...filter, labelIds: known }
}

// 「絞り込み」ボタンに出す数（選んだ条件の数。検索の文字は数えない）
export function countConditions(filter: CardFilter): number {
  return (
    filter.labelIds.length +
    (filter.noLabel ? 1 : 0) +
    filter.due.length +
    filter.importance.length +
    filter.urgency.length
  )
}

// 何か条件があるか（検索の文字か、絞り込みの条件）
export function isFilterActive(filter: CardFilter): boolean {
  return searchWords(filter.query).length > 0 || countConditions(filter) > 0
}

/**
 * 検索のために文字をそろえる（要件 S-01）。
 * 1. NFKC：全角の英数字・記号を半角に、半角カタカナを全角にする（「ＰＤＦ」→「PDF」、「ﾁｪｯｸ」→「チェック」）
 * 2. 小文字にする（「PDF」→「pdf」）
 * 3. カタカナをひらがなにする（「チェック」→「ちぇっく」）。文字コードがちょうど 0x60 ずれているので引く
 */
export function normalizeText(text: string): string {
  return text
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[ァ-ヶ]/g, (ch) => String.fromCharCode(ch.charCodeAt(0) - 0x60))
}

// 検索の文字を言葉に分ける。空白（全角の空白も）で区切る
export function searchWords(query: string): string[] {
  return normalizeText(query)
    .split(/\s+/)
    .filter((word) => word !== '')
}

/**
 * カードが条件に合うか（要件 S-02）。
 * - 同じ種類の中は「どれか」（ラベル「仕事」か「買い物」）、種類どうしは「全部」（かつ重要度「高」）
 * - 検索の言葉は、タイトルか説明文に全部含まれていること
 * - 選んでいない種類は、条件にしない
 */
export function matchesCard(card: CardResponse, filter: CardFilter, today: Date = new Date()): boolean {
  const words = searchWords(filter.query)
  if (words.length > 0) {
    const text = normalizeText(`${card.title}\n${card.description ?? ''}`)
    if (!words.every((word) => text.includes(word))) return false
  }
  if (filter.labelIds.length > 0 || filter.noLabel) {
    const hasLabel = card.labelIds.some((id) => filter.labelIds.includes(id))
    const isNoLabel = filter.noLabel && card.labelIds.length === 0
    if (!hasLabel && !isNoLabel) return false
  }
  if (filter.due.length > 0 && !filter.due.includes(dueFilterOf(card.dueDate, today))) return false
  if (filter.importance.length > 0 && !filter.importance.includes(card.importance)) return false
  if (filter.urgency.length > 0 && !filter.urgency.includes(card.urgency)) return false
  return true
}

// カードの期限が、どの区切りに入るか
function dueFilterOf(dueDate: string | null, today: Date): DueFilter {
  if (dueDate === null) return 'none'
  const status = dueStatus(dueDate, today)
  return status === 'normal' ? 'later' : status
}

function isLevel(value: string): value is Level {
  return LEVEL_VALUES.includes(value)
}

function unique<T>(values: T[]): T[] {
  return [...new Set(values)]
}
