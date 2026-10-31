import { useState } from 'react'
import { useAuth } from '../auth/AuthContext.ts'
import styles from './BoardHeader.module.css'

// 画面の上のヘッダー。読み込み中・失敗したときはボード名なしで出す
// ボード名の変更・ラベル管理・メニュー（退会）は、それぞれの機能を作るときに足す
export function BoardHeader({ boardName }: { boardName?: string }) {
  const { user, logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)

  async function handleLogout() {
    setLoggingOut(true)
    await logout()
  }

  return (
    <header className={styles.header}>
      <span className={styles.appName}>TaskBoard</span>
      {boardName !== undefined && (
        <>
          <span className={styles.sep} />
          <h1 className={styles.boardName}>{boardName}</h1>
        </>
      )}
      {user && (
        <div className={styles.right}>
          <span className={styles.user}>{user.email}</span>
          <button type="button" className="btn btn-ghost" onClick={handleLogout} disabled={loggingOut}>
            ログアウト
          </button>
        </div>
      )}
    </header>
  )
}
