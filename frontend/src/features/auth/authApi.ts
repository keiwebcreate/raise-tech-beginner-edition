// ログイン関係の API（API設計書 5.1）。関数の名前はモックアップの api.js と同じ

import { apiFetch } from '../../api/client.ts'
import type { Credentials, UserResponse } from '../../api/types.ts'

// A-01 ユーザー登録（成功するとログインした状態になる）
export function signup(credentials: Credentials): Promise<UserResponse> {
  return apiFetch<UserResponse>('/api/auth/signup', 'POST', credentials)
}

// A-02 ログイン
export function login(credentials: Credentials): Promise<UserResponse> {
  return apiFetch<UserResponse>('/api/auth/login', 'POST', credentials)
}

// A-04 ログアウト
export function logout(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', 'POST')
}

// A-05 ログイン中のユーザー（ログインしていなければ 401 の ApiError）
export function getMe(): Promise<UserResponse> {
  return apiFetch<UserResponse>('/api/auth/me')
}
