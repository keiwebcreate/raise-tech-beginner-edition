// カードの API（API設計書 5.4）。関数の名前はモックアップの api.js と同じ

import { apiFetch } from '../../api/client.ts'
import type { CardResponse } from '../../api/types.ts'

// A-12 カードの追加（リストの一番下に足される）
export function addCard(columnId: number, title: string): Promise<CardResponse> {
  return apiFetch<CardResponse>(`/api/columns/${columnId}/cards`, 'POST', { title })
}
