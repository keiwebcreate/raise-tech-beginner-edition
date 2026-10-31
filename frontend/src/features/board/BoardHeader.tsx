import styles from './BoardHeader.module.css'

// 画面の上のヘッダー。読み込み中・失敗したときはボード名なしで出す
// ボード名の変更・ラベル管理・ログイン中の人のメニューは、それぞれの機能を作るときに足す
export function BoardHeader({ boardName }: { boardName?: string }) {
  return (
    <header className={styles.header}>
      <span className={styles.appName}>TaskBoard</span>
      {boardName !== undefined && (
        <>
          <span className={styles.sep} />
          <h1 className={styles.boardName}>{boardName}</h1>
        </>
      )}
    </header>
  )
}
