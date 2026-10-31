import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { useAuth } from './AuthContext.ts'

// 画面の出し分け（画面設計書 3章）。ログインしているかを確かめている間は、何も出さない

// ログインが必要な画面（ボード）。ログインしていなければ /login へ
export function RequireLogin({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (user === undefined) return null
  if (user === null) return <Navigate to="/login" replace />
  return children
}

// ログインしていない人の画面（ログイン・登録）。ログイン済みなら / へ
export function GuestOnly({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  if (user === undefined) return null
  if (user !== null) return <Navigate to="/" replace />
  return children
}
