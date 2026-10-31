// リストの API（API設計書 5.3）。関数の名前はモックアップの api.js と同じ

import { apiFetch } from '../../api/client.ts'
import type { ColumnResponse } from '../../api/types.ts'

// A-08 リストの追加（ボードの右端に足される）
export function addColumn(name: string): Promise<ColumnResponse> {
  return apiFetch<ColumnResponse>('/api/columns', 'POST', { name })
}
