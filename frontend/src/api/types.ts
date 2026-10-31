// API の JSON の型（API設計書 5章）。サーバーの DTO（record）と1対1で対応させる

// ログイン中のユーザー（A-01〜A-03・A-05 で返る）
export type UserResponse = {
  id: number
  email: string // ゲストのときはダミーのアドレス
  isGuest: boolean
}

// A-01 登録・A-02 ログインで送る
export type Credentials = {
  email: string
  password: string
}

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

// A-08 リストの追加などで返る（中のカードは含まない）
export type ColumnResponse = {
  id: number
  name: string
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
