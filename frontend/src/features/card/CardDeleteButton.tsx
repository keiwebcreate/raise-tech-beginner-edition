import { useState } from 'react'
import type { CardResponse } from '../../api/types.ts'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { useBoard } from '../board/BoardContext.ts'
import * as cardApi from './cardApi.ts'
import styles from './CardDeleteButton.module.css'

type Props = {
  card: CardResponse
}

// カードの右上のゴミ箱（要件 C-03、画面設計書 W-03。イシュー #61）
// 見えるのは、マウスを乗せたときとキーボードでカードを選んだときだけ（CardItem.module.css）
// 押すとカード詳細と同じ確認ダイアログを出し、「削除する」で消す（A-14）
// タイトルのボタン（詳細を開く・ドラッグでつかむ）の上に重ねてあるので、押しても詳細は開かず、ドラッグも始まらない
export function CardDeleteButton({ card }: Props) {
  const { dispatch } = useBoard()
  const { showApiError } = useToast()
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)

  async function deleteCard() {
    setDeleting(true)
    try {
      await cardApi.deleteCard(card.id)
      // ここでカードが画面から消え、この部品もなくなる
      dispatch({ type: 'cardDeleted', cardId: card.id })
    } catch (e) {
      setDeleting(false)
      showApiError(e)
    }
  }

  return (
    <>
      <button
        type="button"
        className={styles.button}
        aria-haspopup="dialog"
        disabled={deleting}
        onClick={() => setConfirming(true)}
      >
        <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
          <path
            d="M6 2h4M2.5 4h11M4 4l.7 9.1a1 1 0 0 0 1 .9h4.6a1 1 0 0 0 1-.9L12 4M6.5 7v4M9.5 7v4"
            fill="none"
            stroke="currentColor"
            strokeWidth="1.4"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
        {/* 読み上げでは、どのカードを消すのかがわかる名前にする */}
        <span className="sr-only">「{card.title}」を削除</span>
      </button>
      {confirming && (
        <ConfirmDialog
          title="カードを削除しますか？"
          message={`「${card.title}」を削除します。この操作は取り消せません。`}
          onAnswer={(ok) => {
            setConfirming(false)
            if (ok) deleteCard()
          }}
        />
      )}
    </>
  )
}
