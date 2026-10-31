import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { setUnauthenticatedHandler } from '../../api/client.ts'
import type { UserResponse } from '../../api/types.ts'
import { MESSAGES } from '../../shared/lib/constants.ts'
import { AuthContext, type LoginPageState } from './AuthContext.ts'
import * as authApi from './authApi.ts'

// ログイン中のユーザーを持ち、AuthContext で中の画面に配る
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null | undefined>(undefined)
  const navigate = useNavigate()

  // 401 の処理の中から「今ログイン中か」を見るために、最新の user を覚えておく
  const userRef = useRef(user)
  useEffect(() => {
    userRef.current = user
  }, [user])

  // アプリを開いたとき（再読み込みも）に1回だけ、ログインしているかを確かめる（API設計書 6.1）
  useEffect(() => {
    authApi
      .getMe()
      .then(setUser)
      .catch(() => setUser(null))
  }, [])

  // 操作中にセッションが切れた（401）ら、お知らせを付けてログイン画面へ（画面設計書 3章）
  useEffect(() => {
    setUnauthenticatedHandler(() => {
      if (!userRef.current) return // もともとログインしていない（アプリを開いたときの確認など）
      const state: LoginPageState = { notice: MESSAGES.sessionExpired }
      navigate('/login', { replace: true, state })
      setUser(null)
    })
    return () => setUnauthenticatedHandler(null)
  }, [navigate])

  const loggedIn = useCallback(
    (loggedInUser: UserResponse) => {
      setUser(loggedInUser)
      navigate('/', { replace: true })
    },
    [navigate],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      // 通信に失敗しても、画面はログアウトした状態にする
      navigate('/login', { replace: true })
      setUser(null)
    }
  }, [navigate])

  return <AuthContext value={{ user, loggedIn, logout }}>{children}</AuthContext>
}
