// API の JSON の型（API設計書 5章）。サーバーの DTO（record）と1対1で対応させる

// 重要度・緊急度
export type Level = 'HIGH' | 'MEDIUM' | 'LOW'

export type LabelResponse = {
  id: number
  name: string
  color: string // "#e5484d" のような8色のどれか
}

export type CardResponse = {
  id: number
  title: string
  description: string | null
  importance: Level
  urgency: Level
  dueDate: string | null // "2026-10-30" の形
  labelIds: number[]
}

export type ColumnWithCardsResponse = {
  id: number
  name: string
  cards: CardResponse[] // 上から順
}

// A-06 ボードの取得（GET /api/board）
export type BoardResponse = {
  id: number
  name: string
  labels: LabelResponse[] // 作成順
  columns: ColumnWithCardsResponse[] // 左から順
}

// エラーのときに返ってくる形（Problem Details。API設計書 4.2）
export type ProblemDetail = {
  status: number
  code: string
  detail: string
  errors?: FieldError[]
}

export type FieldError = {
  field: string
  message: string
}
