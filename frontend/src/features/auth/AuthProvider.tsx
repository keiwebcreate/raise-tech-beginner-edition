import { startTransition, useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
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
      // 画面の移動と一緒に反映させる（理由は下の withdrawn と同じ。2026-11-01 イシュー #43 で直した）
      startTransition(() => {
        navigate('/login', { replace: true, state })
        setUser(null)
      })
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
    // 通信に失敗しても、画面はログアウトした状態にする（失敗はここで受け止め、呼んだ側には伝えない）
    await authApi.logout().catch(() => {})
    navigate('/login', { replace: true })
    setUser(null)
  }, [navigate])

  // 退会した（A-19 が成功した）。サーバーでセッションは消えているので、画面をログアウトした状態にする（要件 U-11）
  const withdrawn = useCallback(() => {
    const state: LoginPageState = { notice: MESSAGES.withdrawn }
    // React Router は画面の移動を「急がない更新」（startTransition）で行う。setUser(null) も同じにして、一緒に反映させる。
    // 別々だと先に「ログインしていない」が反映され、RequireLogin がお知らせなしで /login へ移してしまう
    startTransition(() => {
      navigate('/login', { replace: true, state })
      setUser(null)
    })
  }, [navigate])

  return <AuthContext value={{ user, loggedIn, userUpdated: setUser, logout, withdrawn }}>{children}</AuthContext>
}
