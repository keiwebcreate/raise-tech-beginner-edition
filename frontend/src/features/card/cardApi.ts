// カードの API（API設計書 5.4）。関数の名前はモックアップの api.js と同じ

import { apiFetch } from '../../api/client.ts'
import type { CardRequest, CardResponse } from '../../api/types.ts'

// A-12 カードの追加（リストの一番下に足される）
export function addCard(columnId: number, title: string): Promise<CardResponse> {
  return apiFetch<CardResponse>(`/api/columns/${columnId}/cards`, 'POST', { title })
}

// A-13 カードの保存（全項目をまとめて置き換える）
export function saveCard(cardId: number, request: CardRequest): Promise<CardResponse> {
  return apiFetch<CardResponse>(`/api/cards/${cardId}`, 'PUT', request)
}

// A-14 カードの削除
export function deleteCard(cardId: number): Promise<void> {
  return apiFetch<void>(`/api/cards/${cardId}`, 'DELETE')
}
