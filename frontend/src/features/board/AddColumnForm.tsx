import { useRef } from 'react'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import { LIMITS, MAX_LENGTH, MESSAGES } from '../../shared/lib/constants.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { InlineForm } from '../../shared/ui/InlineForm.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import styles from './AddColumnForm.module.css'
import { useBoard } from './BoardContext.ts'
import * as columnApi from './columnApi.ts'

type Props = {
  columnCount: number
  isOpen: boolean
  onOpen: () => void
  onClose: () => void
}

// ボードの右端の「＋ リストを追加」と、その入力欄（A-08。要件 L-02）
export function AddColumnForm({ columnCount, isOpen, onOpen, onClose }: Props) {
  const { dispatch } = useBoard()
  const { showToast, showApiError } = useToast()
  const ref = useRef<HTMLDivElement>(null)
  const isFull = columnCount >= LIMITS.columns

  function handleOpen() {
    if (isFull) {
      showToast(MESSAGES.columnLimit)
      return
    }
    onOpen()
  }

  async function handleSubmit(value: string): Promise<string | null> {
    const error = validateText(value, 'リスト名', MAX_LENGTH.columnName)
    if (error) return error
    if (isFull) {
      showToast(MESSAGES.columnLimit)
      return ''
    }
    try {
      const column = await columnApi.addColumn(value)
      dispatch({ type: 'columnAdded', column })
      // 足したリストの右にこの入力欄が来るので、見えるところまで横にスクロールする
      requestAnimationFrame(() => ref.current?.scrollIntoView({ inline: 'end', block: 'nearest' }))
      return null
    } catch (e) {
      if (e instanceof ApiError && e.code === 'VALIDATION_ERROR') {
        return fieldErrorsOf(e).name ?? e.detail
      }
      showApiError(e)
      return ''
    }
  }

  if (!isOpen) {
    return (
      <button type="button" className={styles.open} onClick={handleOpen}>
        ＋ リストを追加
      </button>
    )
  }
  return (
    <div ref={ref} className={styles.formBox}>
      <InlineForm
        label="リスト名"
        placeholder="リスト名を入力"
        submitText="リストを追加"
        onSubmit={handleSubmit}
        onClose={onClose}
      />
    </div>
  )
}
