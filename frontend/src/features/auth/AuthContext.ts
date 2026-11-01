// ログイン中のユーザーを、どの画面からでも使えるようにする入れ物（詳細設計書 4.2）
// 中身は AuthProvider が用意する

import { createContext, useContext } from 'react'
import type { UserResponse } from '../../api/types.ts'

type AuthContextValue = {
  // undefined：確かめている途中 ／ null：ログインしていない ／ それ以外：ログイン中のユーザー
  user: UserResponse | null | undefined
  // ログイン・登録が成功したときに呼ぶ
  loggedIn: (user: UserResponse) => void
  // ログインしたまま、ユーザー情報が変わったときに呼ぶ（メールアドレスの変更 A-20）。画面は移らない
  userUpdated: (user: UserResponse) => void
  // ログアウトして、ログイン画面へ移る
  logout: () => Promise<void>
  // 退会が成功したときに呼ぶ。ログイン画面へ移り、「退会しました」を出す
  withdrawn: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth は AuthProvider の中で使ってください')
  }
  return value
}

// ログイン画面に1回だけ出すお知らせ（セッション切れなど）。React Router の state で渡す（詳細設計書 4.2）
export type LoginPageState = {
  notice?: string
}
