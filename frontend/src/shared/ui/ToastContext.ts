// 画面の右下に出す通知（トースト。画面設計書 5章）を、どの部品からでも出せるようにする入れ物
// 中身は ToastProvider が用意する

import { createContext, useContext } from 'react'

type ToastContextValue = {
  // メッセージを5秒間出す
  showToast: (message: string) => void
  // API のエラーを通知で出す。401（ログイン画面へ移る）のときは出さない
  showApiError: (error: unknown) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)

export function useToast(): ToastContextValue {
  const value = useContext(ToastContext)
  if (!value) {
    throw new Error('useToast は ToastProvider の中で使ってください')
  }
  return value
}
