// ラベルの API（API設計書 5.5）。関数の名前はモックアップの api.js と同じ
// ラベルの一覧は GET /api/board（A-06）に入っているので、一覧を取る関数はない

import { apiFetch } from '../../api/client.ts'
import type { LabelRequest, LabelResponse } from '../../api/types.ts'

// A-16 ラベルの追加
export function addLabel(request: LabelRequest): Promise<LabelResponse> {
  return apiFetch<LabelResponse>('/api/labels', 'POST', request)
}

// A-17 ラベルの変更（名前と色）
export function updateLabel(labelId: number, request: LabelRequest): Promise<LabelResponse> {
  return apiFetch<LabelResponse>(`/api/labels/${labelId}`, 'PUT', request)
}

// A-18 ラベルの削除（付いていたカードからも外れる）
export function deleteLabel(labelId: number): Promise<void> {
  return apiFetch<void>(`/api/labels/${labelId}`, 'DELETE')
}
