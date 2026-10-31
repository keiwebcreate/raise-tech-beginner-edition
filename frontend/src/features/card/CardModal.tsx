import { useEffect, useState, type FormEvent } from 'react'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import type { CardResponse, Level } from '../../api/types.ts'
import { LEVELS, MAX_LENGTH, MESSAGES } from '../../shared/lib/constants.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog.tsx'
import { LinkifiedText } from '../../shared/ui/LinkifiedText.tsx'
import { Modal } from '../../shared/ui/Modal.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { useBoard } from '../board/BoardContext.ts'
import { findCard } from '../board/findCard.ts'
import { LabelModal } from '../label/LabelModal.tsx'
import { LabelPicker } from '../label/LabelPicker.tsx'
import * as cardApi from './cardApi.ts'
import styles from './CardModal.module.css'

// 入力欄で扱いやすい形（null は空文字にする）
type Form = {
  title: string
  description: string
  importance: Level
  urgency: Level
  dueDate: string
  labelIds: number[]
}
type Errors = Partial<Record<keyof Form, string>>

function toForm(card: CardResponse): Form {
  return {
    title: card.title,
    description: card.description ?? '',
    importance: card.importance,
    urgency: card.urgency,
    dueDate: card.dueDate ?? '',
    labelIds: [...card.labelIds],
  }
}

// 変更があるか。ラベルは付けた順番が違っても同じとみなす
function isSame(a: Form, b: Form): boolean {
  const normalize = (form: Form) => JSON.stringify({ ...form, labelIds: [...form.labelIds].sort() })
  return normalize(a) === normalize(b)
}

type Props = {
  cardId: number
  onClose: () => void
}

// W-04 カード詳細（モーダル。要件 C-02・C-03）
// 開いたときのカードの値を「下書き（draft）」にコピーし、入力は下書きだけを変える。「保存」でまとめて送る（A-13）
// 「カードを削除」は、確認してから削除する（A-14）
export function CardModal({ cardId, onClose }: Props) {
  const { board, dispatch } = useBoard()
  const { showApiError } = useToast()
  const found = board ? findCard(board, cardId) : null

  const [original] = useState<Form | null>(() => (found ? toForm(found.card) : null))
  const [draft, setDraft] = useState<Form | null>(original)
  // 説明が空なら、最初から入力欄を出す（画面設計書 W-04）
  const [editingDescription, setEditingDescription] = useState(original?.description === '')
  const [errors, setErrors] = useState<Errors>({})
  const [saving, setSaving] = useState(false)
  // 開いている確認ダイアログ：変更の破棄 か カードの削除
  const [confirming, setConfirming] = useState<'discard' | 'delete' | null>(null)
  // 上に重ねて開いたラベル管理（W-05）
  const [managingLabels, setManagingLabels] = useState(false)

  // カードが見つからなければ（開いている間に消えたなど）閉じる
  useEffect(() => {
    if (!found) onClose()
  }, [found, onClose])
  if (!found || !board || !original || !draft) return null

  // 下書きの1項目を変える
  function update<K extends keyof Form>(key: K, value: Form[K]) {
    setDraft((current) => (current ? { ...current, [key]: value } : current))
  }

  // ラベルを付ける・外す。続けて押されても取りこぼさないよう、いちばん新しい下書きを元に変える
  function toggleLabel(labelId: number) {
    setDraft((current) => {
      if (!current) return current
      const labelIds = current.labelIds.includes(labelId)
        ? current.labelIds.filter((id) => id !== labelId)
        : [...current.labelIds, labelId]
      return { ...current, labelIds }
    })
  }

  // ×・キャンセル・Esc・背景のクリック。変更があれば確認してから閉じる
  function requestClose() {
    if (saving) return
    if (original && draft && !isSame(original, draft)) {
      setConfirming('discard')
      return
    }
    onClose()
  }

  // A-14 カードの削除（確認ダイアログで「削除する」を押したあと。要件 C-03）
  async function deleteCard() {
    setSaving(true)
    try {
      await cardApi.deleteCard(cardId)
      dispatch({ type: 'cardDeleted', cardId })
      onClose()
    } catch (e) {
      setSaving(false)
      showApiError(e)
    }
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!draft) return

    const nextErrors: Errors = {}
    const titleError = validateText(draft.title, 'タイトル', MAX_LENGTH.cardTitle)
    if (titleError) nextErrors.title = titleError
    if (draft.description.length > MAX_LENGTH.description) {
      nextErrors.description = MESSAGES.tooLong('説明', MAX_LENGTH.description)
    }
    setErrors(nextErrors)
    if (Object.keys(nextErrors).length > 0) return

    setSaving(true)
    try {
      // 画面と同じく、タイトルは前後の空白を除いた値を送る（サーバーは受け取った値で文字数を数えるため）
      const saved = await cardApi.saveCard(cardId, {
        ...draft,
        title: draft.title.trim(),
        // ラベル管理（上に重ねて開いたもの）で削除したラベルは、下書きからも外して送る
        labelIds: draft.labelIds.filter((id) => board?.labels.some((label) => label.id === id)),
        dueDate: draft.dueDate || null,
      })
      dispatch({ type: 'cardSaved', card: saved })
      onClose()
    } catch (e) {
      setSaving(false)
      if (e instanceof ApiError && e.code === 'VALIDATION_ERROR') {
        setErrors(fieldErrorsOf(e))
      } else {
        showApiError(e)
      }
    }
  }

  const remaining = MAX_LENGTH.description - draft.description.length

  return (
    <Modal width={640} labelledBy="card-modal-title" onRequestClose={requestClose}>
      <form onSubmit={handleSave} noValidate>
        <div className={styles.head}>
          <span id="card-modal-title" className={`muted ${styles.small}`}>
            リスト：{found.column.name}
          </span>
          <button type="button" className="icon-btn" aria-label="閉じる" onClick={requestClose}>
            ✕
          </button>
        </div>

        <div className={styles.body}>
          <div className="field">
            <label htmlFor="card-title">タイトル</label>
            <input
              id="card-title"
              className={`input ${styles.titleInput} ${errors.title ? 'is-error' : ''}`}
              value={draft.title}
              onChange={(e) => update('title', e.target.value)}
              data-autofocus // 開いたときに、ここにカーソルを置く（Modal が探して置く）
            />
            {errors.title && <div className="error">{errors.title}</div>}
          </div>

          <div className="field">
            <span className={styles.fieldLabel}>ラベル</span>
            <LabelPicker
              labels={board.labels}
              selectedIds={draft.labelIds}
              onToggle={toggleLabel}
              onManage={() => setManagingLabels(true)}
            />
          </div>

          <div className={styles.grid3}>
            <div className="field">
              <label htmlFor="card-importance">重要度</label>
              <select
                id="card-importance"
                className="select"
                value={draft.importance}
                onChange={(e) => update('importance', e.target.value as Level)}
              >
                {LEVELS.map((level) => (
                  <option key={level.value} value={level.value}>
                    {level.text}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="card-urgency">緊急度</label>
              <select
                id="card-urgency"
                className="select"
                value={draft.urgency}
                onChange={(e) => update('urgency', e.target.value as Level)}
              >
                {LEVELS.map((level) => (
                  <option key={level.value} value={level.value}>
                    {level.text}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="card-due">期限</label>
              <input
                id="card-due"
                className="input"
                type="date"
                value={draft.dueDate}
                onChange={(e) => update('dueDate', e.target.value)}
              />
              <div className="help">
                {draft.dueDate ? (
                  <button type="button" className="link-btn" onClick={() => update('dueDate', '')}>
                    期限をクリア
                  </button>
                ) : (
                  ' '
                )}
              </div>
            </div>
          </div>

          <div className="field">
            <label htmlFor="card-description">説明</label>
            {editingDescription ? (
              <>
                <textarea
                  id="card-description"
                  className={`textarea ${errors.description ? 'is-error' : ''}`}
                  placeholder="説明を入力（URLはリンクになります）"
                  value={draft.description}
                  onChange={(e) => update('description', e.target.value)}
                />
                <div className={styles.counter}>
                  {remaining >= 0 ? `残り ${remaining} 文字` : <span className="error">{-remaining} 文字オーバー</span>}
                </div>
              </>
            ) : (
              <>
                {/* 保存した説明は、改行を反映して URL をリンクにして見せる。クリックすると入力欄に戻る */}
                {/* 中にリンクがあるので全体をボタンにはできない。見えないボタンを全体に重ね、リンクはその上に出す */}
                <div className={styles.descView}>
                  <button
                    type="button"
                    className={styles.descEdit}
                    aria-label="説明を編集"
                    onClick={() => setEditingDescription(true)}
                  />
                  <LinkifiedText text={draft.description} linkClassName={styles.link} />
                </div>
                <div className="help">クリックすると編集できます</div>
              </>
            )}
            {errors.description && <div className="error">{errors.description}</div>}
          </div>
        </div>

        <div className={styles.foot}>
          <button
            type="button"
            className="btn btn-text-danger"
            disabled={saving}
            onClick={() => setConfirming('delete')}
          >
            カードを削除
          </button>
          <span className={styles.spacer} />
          <button type="button" className="btn" onClick={requestClose}>
            キャンセル
          </button>
          <button type="submit" className="btn btn-primary" disabled={saving}>
            {saving ? '保存中…' : '保存'}
          </button>
        </div>
      </form>

      {managingLabels && <LabelModal onClose={() => setManagingLabels(false)} />}
      {confirming === 'discard' && (
        <ConfirmDialog
          title="変更を破棄しますか？"
          message="保存していない変更は失われます。"
          okText="破棄する"
          onAnswer={(ok) => {
            setConfirming(null)
            if (ok) onClose()
          }}
        />
      )}
      {confirming === 'delete' && (
        <ConfirmDialog
          title="カードを削除しますか？"
          message={`「${original.title}」を削除します。この操作は取り消せません。`}
          onAnswer={(ok) => {
            setConfirming(null)
            if (ok) deleteCard()
          }}
        />
      )}
    </Modal>
  )
}
