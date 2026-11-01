// ボードの API（API設計書 5.2）

import { apiFetch } from '../../api/client.ts'
import type { BoardResponse, BoardSummaryResponse } from '../../api/types.ts'

// A-22 ボードの一覧（作った順）
export function listBoards(): Promise<BoardSummaryResponse[]> {
  return apiFetch<BoardSummaryResponse[]>('/api/boards')
}

// A-23 ボードの作成（「未着手／作業中／完了」の3リストが入る）
export function createBoard(name: string): Promise<BoardSummaryResponse> {
  return apiFetch<BoardSummaryResponse>('/api/boards', 'POST', { name })
}

// A-06 ボードの取得
export function getBoard(boardId: number): Promise<BoardResponse> {
  return apiFetch<BoardResponse>(`/api/boards/${boardId}`)
}

// A-07 ボード名の変更
export function renameBoard(boardId: number, name: string): Promise<BoardSummaryResponse> {
  return apiFetch<BoardSummaryResponse>(`/api/boards/${boardId}`, 'PATCH', { name })
}

// A-24 ボードの削除（中のリストとカードも消える。ラベルは残る）
export function deleteBoard(boardId: number): Promise<void> {
  return apiFetch<void>(`/api/boards/${boardId}`, 'DELETE')
}
