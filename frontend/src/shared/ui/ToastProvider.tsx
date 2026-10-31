import { useCallback, useMemo, useRef, useState, type ReactNode } from 'react'
import { ApiError } from '../../api/client.ts'
import { MESSAGES } from '../lib/constants.ts'
import { ToastContext } from './ToastContext.ts'
import styles from './Toast.module.css'

type Toast = { id: number; message: string }

const SHOW_MS = 5000 // 5秒で消す（画面設計書 5章）

// 通知の一覧を持ち、画面の右下に並べて出す
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(1)

  const showToast = useCallback((message: string) => {
    const id = nextId.current++
    setToasts((current) => [...current, { id, message }])
    setTimeout(() => setToasts((current) => current.filter((toast) => toast.id !== id)), SHOW_MS)
  }, [])

  const showApiError = useCallback(
    (error: unknown) => {
      if (error instanceof ApiError && error.code === 'UNAUTHENTICATED') return
      showToast(error instanceof ApiError ? error.detail : MESSAGES.network)
    },
    [showToast],
  )

  const value = useMemo(() => ({ showToast, showApiError }), [showToast, showApiError])

  return (
    <ToastContext value={value}>
      {children}
      <div className={styles.root}>
        {toasts.map((toast) => {
          // 「通信に失敗しました。時間をおいて…」を、最初の「。」で見出しと説明に分ける
          const [title, ...rest] = toast.message.split('。')
          const detail = rest.join('。')
          return (
            <div key={toast.id} className={styles.toast} role="alert">
              <span className={styles.icon}>!</span>
              <div>
                <strong className={styles.title}>{title}</strong>
                {detail && <span className={styles.detail}>{detail}</span>}
              </div>
            </div>
          )
        })}
      </div>
    </ToastContext>
  )
}
