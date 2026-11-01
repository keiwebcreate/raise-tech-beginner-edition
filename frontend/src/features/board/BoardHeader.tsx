import { useState } from 'react'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import { MAX_LENGTH } from '../../shared/lib/constants.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { InlineEdit } from '../../shared/ui/InlineEdit.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { useAuth } from '../auth/AuthContext.ts'
import { UserMenu } from '../auth/UserMenu.tsx'
import { WithdrawModal } from '../auth/WithdrawModal.tsx'
import * as boardApi from './boardApi.ts'
import { useBoard } from './BoardContext.ts'
import styles from './BoardHeader.module.css'

type Props = {
  boardName?: string // 読み込み中・失敗したときは undefined（ボード名を出さない）
  isRenaming?: boolean
  onStartRename?: () => void
  onEndRename?: () => void
  onOpenLabels?: () => void // 「ラベル管理」（W-05）を開く。読み込み中・失敗したときは undefined（ボタンを出さない）
}

// 画面の上のヘッダー（画面設計書 W-03）
// - 登録したユーザー：メールアドレスと「▾」のメニュー（ログアウト・退会する）
// - ゲスト：「ゲスト」の札と「ログアウト」ボタン、ヘッダーの下に注意書きの帯（要件 U-07）。退会はない
export function BoardHeader({ boardName, isRenaming = false, onStartRename, onEndRename, onOpenLabels }: Props) {
  const { user, logout } = useAuth()
  const { dispatch } = useBoard()
  const { showApiError } = useToast()
  const [loggingOut, setLoggingOut] = useState(false)
  const [withdrawOpen, setWithdrawOpen] = useState(false) // 退会の確認（W-08）

  async function handleLogout() {
    setLoggingOut(true)
    await logout()
  }

  // A-07 ボード名の変更
  async function rename(name: string): Promise<string | null> {
    try {
      const saved = await boardApi.renameBoard(name)
      dispatch({ type: 'boardRenamed', name: saved.name })
      return null
    } catch (e) {
      if (e instanceof ApiError && e.code === 'VALIDATION_ERROR') {
        return fieldErrorsOf(e).name ?? e.detail
      }
      showApiError(e)
      return ''
    }
  }

  return (
    <>
      <header className={styles.header}>
        <span className={styles.appName}>TaskBoard</span>
        {boardName !== undefined && (
          <>
            <span className={styles.sep} />
            <h1 className={styles.heading}>
              <InlineEdit
                value={boardName}
                label="ボード名"
                isEditing={isRenaming}
                onStartEdit={() => onStartRename?.()}
                onEndEdit={() => onEndRename?.()}
                validate={(value) => validateText(value, 'ボード名', MAX_LENGTH.boardName)}
                onSave={rename}
                displayClassName={styles.boardName}
                inputClassName={styles.boardNameEdit}
              />
            </h1>
          </>
        )}
        {user && (
          <div className={styles.right}>
            {onOpenLabels && (
              <button type="button" className="btn btn-ghost" aria-haspopup="dialog" onClick={onOpenLabels}>
                ラベル管理
              </button>
            )}
            {user.isGuest ? (
              <>
                <span className={styles.guestBadge}>ゲスト</span>
                <button type="button" className="btn btn-ghost" onClick={handleLogout} disabled={loggingOut}>
                  ログアウト
                </button>
              </>
            ) : (
              <UserMenu
                email={user.email}
                loggingOut={loggingOut}
                onLogout={handleLogout}
                onWithdraw={() => setWithdrawOpen(true)}
              />
            )}
          </div>
        )}
      </header>
      {withdrawOpen && <WithdrawModal onClose={() => setWithdrawOpen(false)} />}
      {user?.isGuest && (
        <div className={styles.guestBar} role="note">
          ゲストモード：データはログアウトするか24時間たつと削除されます
        </div>
      )}
    </>
  )
}
