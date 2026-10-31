// すべての API はこの apiFetch を通して呼ぶ（詳細設計書 4.3）
// - 変更する通信（POST など）には CSRF のヘッダーを付ける
// - 失敗したら Problem Details を読んで ApiError を投げる
// - 401 UNAUTHENTICATED（セッション切れなど）のときは、登録された処理（ログイン画面へ移す）を呼ぶ

import { MESSAGES } from '../shared/lib/constants.ts'
import type { FieldError, ProblemDetail } from './types.ts'

// API の失敗。画面ではこれを受け取って、メッセージ（detail）などを出す
export class ApiError extends Error {
  readonly status: number // HTTP のステータスコード。つながらなかったときは 0
  readonly code: string // "NOT_FOUND" など（API設計書 4.3）
  readonly detail: string // 画面にそのまま出せるメッセージ
  readonly errors: FieldError[] // 入力チェックのエラー（項目ごと）

  constructor(status: number, code: string, detail: string, errors: FieldError[] = []) {
    super(detail)
    this.status = status
    this.code = code
    this.detail = detail
    this.errors = errors
  }
}

// API の errors（[{ field, message }]）を { field: message } の形にする。入力欄の下に出すときに使う
export function fieldErrorsOf(error: ApiError): Record<string, string> {
  const result: Record<string, string> = {}
  for (const { field, message } of error.errors) {
    result[field] ??= message
  }
  return result
}

// 401 UNAUTHENTICATED のときに呼ぶ処理。React の画面の移動はここではできないので、AuthProvider が登録する
let unauthenticatedHandler: (() => void) | null = null

export function setUnauthenticatedHandler(handler: (() => void) | null): void {
  unauthenticatedHandler = handler
}

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export async function apiFetch<T>(path: string, method: Method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    // Cookie の XSRF-TOKEN を、送るたびに読んでヘッダーに付ける（API設計書 2.3）
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  let response: Response
  try {
    response = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    // サーバーにつながらなかった
    throw new ApiError(0, 'INTERNAL_ERROR', MESSAGES.network)
  }

  if (response.ok) {
    // 204 No Content のときは中身がない
    return (response.status === 204 ? undefined : await response.json()) as T
  }
  const problem = await readProblem(response)
  if (problem?.code === 'UNAUTHENTICATED') {
    unauthenticatedHandler?.()
  }
  throw new ApiError(
    response.status,
    problem?.code ?? 'INTERNAL_ERROR',
    problem?.detail ?? MESSAGES.network,
    problem?.errors ?? [],
  )
}

// エラーの中身（Problem Details）を読む。JSON でなければ null
async function readProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return null
  }
}

function readCookie(name: string): string | null {
  const found = document.cookie.split('; ').find((item) => item.startsWith(`${name}=`))
  return found ? decodeURIComponent(found.slice(name.length + 1)) : null
}
