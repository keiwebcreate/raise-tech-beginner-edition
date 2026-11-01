import { useState } from 'react'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import type { LabelResponse } from '../../api/types.ts'
import { MAX_LENGTH } from '../../shared/lib/constants.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { InlineEdit } from '../../shared/ui/InlineEdit.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { useAuth } from '../auth/AuthContext.ts'
import { EmailChangeModal } from '../auth/EmailChangeModal.tsx'
import { PasswordChangeModal } from '../auth/PasswordChangeModal.tsx'
import { UserMenu, type AccountModalKind } from '../auth/UserMenu.tsx'
import { WithdrawModal } from '../auth/WithdrawModal.tsx'
import * as boardApi from './boardApi.ts'
import { useBoard } from './BoardContext.ts'
import styles from './BoardHeader.module.css'
import { CardSearch } from './CardSearch.tsx'
import { MobileMenu } from './MobileMenu.tsx'

type Props = {
  boardName?: string // 読み込み中・失敗したときは undefined（ボード名を出さない）
  isRenaming?: boolean
  onStartRename?: () => void
  onEndRename?: () => void
  onOpenLabels?: () => void // 「ラベル管理」（W-05）を開く。読み込み中・失敗したときは undefined（ボタンを出さない）
  labels?: LabelResponse[] // 絞り込みのラベルの選択肢。読み込み中・失敗したときは undefined（検索欄を出さない）
}

// 画面の上のヘッダー（画面設計書 W-03）
// - 登録したユーザー：メールアドレスと「▾」のメニュー（メールアドレスの変更・パスワードの変更・ログアウト・退会する）
// - ゲスト：「ゲスト」の札と「ログアウト」ボタン、ヘッダーの下に注意書きの帯（要件 U-07）。退会はない
// - スマートフォンの幅：「ラベル管理」とアカウントの操作は ☰（MobileMenu）にまとめ、2段にする（イシュー #47）
export function BoardHeader({
  boardName,
  isRenaming = false,
  onStartRename,
  onEndRename,
  onOpenLabels,
  labels,
}: Props) {
  const { user, logout } = useAuth()
  const { dispatch } = useBoard()
  const { showApiError } = useToast()
  const [loggingOut, setLoggingOut] = useState(false)
  // 開いているアカウントのモーダル（W-08〜W-10）。一度に開くのは1つだけ
  const [accountModal, setAccountModal] = useState<AccountModalKind | null>(null)
  const closeAccountModal = () => setAccountModal(null)

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
          // 右側。スマートフォン幅では箱をなくし（display: contents）、中身をヘッダーに直接並べる
          // （☰ は1段目の右端、検索は2段目。BoardHeader.module.css）
          <div className={styles.end}>
            {labels && (
              <div className={styles.search}>
                <CardSearch labels={labels} />
              </div>
            )}
            {/* パソコンの幅だけ */}
            <div className={styles.desktopActions}>
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
                  onOpenAccountModal={setAccountModal}
                />
              )}
            </div>
            {/* スマートフォンの幅だけ（☰） */}
            <div className={styles.mobileMenu}>
              <MobileMenu
                user={user}
                loggingOut={loggingOut}
                onLogout={handleLogout}
                onOpenAccountModal={setAccountModal}
                onOpenLabels={onOpenLabels}
              />
            </div>
          </div>
        )}
      </header>
      {accountModal === 'email' && <EmailChangeModal onClose={closeAccountModal} />}
      {accountModal === 'password' && <PasswordChangeModal onClose={closeAccountModal} />}
      {accountModal === 'withdraw' && <WithdrawModal onClose={closeAccountModal} />}
      {user?.isGuest && (
        <div className={styles.guestBar} role="note">
          ゲストモード：データはログアウトするか24時間たつと削除されます
        </div>
      )}
    </>
  )
}
