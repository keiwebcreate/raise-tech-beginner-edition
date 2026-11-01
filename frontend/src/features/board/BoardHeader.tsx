import { useState } from 'react'
import { Link } from 'react-router'
import type { LabelResponse } from '../../api/types.ts'
import { MAX_LENGTH } from '../../shared/lib/constants.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { InlineEdit } from '../../shared/ui/InlineEdit.tsx'
import { useAuth } from '../auth/AuthContext.ts'
import { EmailChangeModal } from '../auth/EmailChangeModal.tsx'
import { PasswordChangeModal } from '../auth/PasswordChangeModal.tsx'
import { UserMenu, type AccountModalKind } from '../auth/UserMenu.tsx'
import { WithdrawModal } from '../auth/WithdrawModal.tsx'
import styles from './BoardHeader.module.css'
import { BoardMenu } from './BoardMenu.tsx'
import { CardSearch } from './CardSearch.tsx'
import { MobileMenu } from './MobileMenu.tsx'

type Props = {
  backToList?: boolean // 「← ボード一覧」を出す（ボード画面。一覧の画面では出さない）
  boardName?: string // 読み込み中・失敗したとき、一覧の画面では undefined（ボード名を出さない）
  isRenaming?: boolean
  onStartRename?: () => void
  onEndRename?: () => void
  // ボード名の保存（A-07）。返す値は InlineEdit の onSave と同じ（null：成功、メッセージ：入力欄の下に出す）
  onRename?: (name: string) => Promise<string | null>
  onDeleteBoard?: () => void // 「ボードを削除」（確認ダイアログを開く）。undefined なら出さない
  onOpenLabels?: () => void // 「ラベル管理」（W-05）を開く。undefined なら出さない
  labels?: LabelResponse[] // 絞り込みのラベルの選択肢。undefined なら検索欄を出さない
}

// 画面の上のヘッダー（画面設計書 W-03・W-11）。ボード画面とボード一覧の画面で使う
// - 登録したユーザー：メールアドレスと「▾」のメニュー（メールアドレスの変更・パスワードの変更・ログアウト・退会する）
// - ゲスト：「ゲスト」の札と「ログアウト」ボタン、ヘッダーの下に注意書きの帯（要件 U-07）。退会はない
// - スマートフォンの幅：「ラベル管理」「ボードを削除」とアカウントの操作は ☰（MobileMenu）にまとめ、2段にする（イシュー #47）
export function BoardHeader({
  backToList = false,
  boardName,
  isRenaming = false,
  onStartRename,
  onEndRename,
  onRename,
  onDeleteBoard,
  onOpenLabels,
  labels,
}: Props) {
  const { user, logout } = useAuth()
  const [loggingOut, setLoggingOut] = useState(false)
  // 開いているアカウントのモーダル（W-08〜W-10）。一度に開くのは1つだけ
  const [accountModal, setAccountModal] = useState<AccountModalKind | null>(null)
  const closeAccountModal = () => setAccountModal(null)

  async function handleLogout() {
    setLoggingOut(true)
    await logout()
  }

  return (
    <>
      <header className={styles.header}>
        <span className={styles.appName}>TaskBoard</span>
        {backToList && (
          // スマートフォンの幅では「←」だけを見せる。「ボード一覧」は読み上げ用に残す（BoardHeader.module.css）
          <Link to="/" className={styles.back}>
            <span aria-hidden="true">←</span>
            <span className={styles.backText}>ボード一覧</span>
          </Link>
        )}
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
                onSave={(name) => onRename?.(name) ?? Promise.resolve(null)}
                displayClassName={styles.boardName}
                inputClassName={styles.boardNameEdit}
              />
            </h1>
            {/* パソコンの幅だけ。スマートフォンの幅では ☰ に入れる */}
            {onDeleteBoard && !isRenaming && (
              <div className={styles.boardMenu}>
                <BoardMenu onDelete={onDeleteBoard} />
              </div>
            )}
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
                onDeleteBoard={onDeleteBoard}
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
