import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import type { ColumnWithCardsResponse } from '../../api/types.ts'
import { LIMITS, MAX_LENGTH, MESSAGES } from '../../shared/lib/constants.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { InlineForm } from '../../shared/ui/InlineForm.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { useBoard } from '../board/BoardContext.ts'
import * as cardApi from './cardApi.ts'
import styles from './AddCardForm.module.css'

type Props = {
  column: ColumnWithCardsResponse
  isOpen: boolean
  onOpen: () => void
  onClose: () => void
}

// リストの下の「＋ カードを追加」と、その入力欄（A-12。要件 C-01）
export function AddCardForm({ column, isOpen, onOpen, onClose }: Props) {
  const { dispatch } = useBoard()
  const { showToast, showApiError } = useToast()
  const isFull = column.cards.length >= LIMITS.cardsPerColumn

  function handleOpen() {
    // 上限に達していたら、押した時点で知らせる（画面設計書 W-03）
    if (isFull) {
      showToast(MESSAGES.cardLimit)
      return
    }
    onOpen()
  }

  async function handleSubmit(value: string): Promise<string | null> {
    const error = validateText(value, 'タイトル', MAX_LENGTH.cardTitle)
    if (error) return error
    if (isFull) {
      showToast(MESSAGES.cardLimit)
      return ''
    }
    try {
      // API が成功してから、返ってきたカードで画面を変える（API設計書 6.3）
      const card = await cardApi.addCard(column.id, value)
      dispatch({ type: 'cardAdded', columnId: column.id, card })
      return null
    } catch (e) {
      if (e instanceof ApiError && e.code === 'VALIDATION_ERROR') {
        return fieldErrorsOf(e).title ?? e.detail
      }
      showApiError(e) // 上限・リストが見つからない・通信の失敗などは通知で出す
      return ''
    }
  }

  if (!isOpen) {
    return (
      <button type="button" className={styles.open} onClick={handleOpen}>
        ＋ カードを追加
      </button>
    )
  }
  return (
    <InlineForm
      label="カードのタイトル"
      placeholder="カードのタイトルを入力"
      submitText="追加"
      help="Enter で追加 / Esc で閉じる"
      onSubmit={handleSubmit}
      onClose={onClose}
    />
  )
}
