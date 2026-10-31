// ボードの API（API設計書 5.2）

import { apiFetch } from '../../api/client.ts'
import type { BoardResponse } from '../../api/types.ts'

// A-06 ボードの取得
export function getBoard(): Promise<BoardResponse> {
  return apiFetch<BoardResponse>('/api/board')
}
