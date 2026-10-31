import styles from './ConfirmDialog.module.css'
import { Modal } from './Modal.tsx'

type Props = {
  title: string
  message: string // 何が消えるかを具体的に書く（画面設計書 W-06）
  okText?: string
  // 「削除する」などを押したら true、キャンセル・Esc・背景のクリックなら false
  onAnswer: (ok: boolean) => void
}

// W-06 確認ダイアログ。最初に選ばれているのは「キャンセル」（Enter の押し間違いで消さないため）
export function ConfirmDialog({ title, message, okText = '削除する', onAnswer }: Props) {
  return (
    <Modal width={420} labelledBy="confirm-title" onRequestClose={() => onAnswer(false)}>
      <div className={styles.body}>
        <h3 id="confirm-title" className={styles.title}>
          {title}
        </h3>
        <p className="muted">{message}</p>
      </div>
      <div className={styles.foot}>
        {/* autoFocus：開いたときに、このボタンにカーソルを置く */}
        <button type="button" className="btn" autoFocus onClick={() => onAnswer(false)}>
          キャンセル
        </button>
        <button type="button" className="btn btn-danger" onClick={() => onAnswer(true)}>
          {okText}
        </button>
      </div>
    </Modal>
  )
}
