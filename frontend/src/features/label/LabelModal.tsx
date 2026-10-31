import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { ApiError, fieldErrorsOf } from '../../api/client.ts'
import type { LabelResponse } from '../../api/types.ts'
import { LABEL_COLORS, LIMITS, MAX_LENGTH, MESSAGES } from '../../shared/lib/constants.ts'
import { isSubmitEnter } from '../../shared/lib/keyboard.ts'
import { useEscapeKey } from '../../shared/lib/useEscapeKey.ts'
import { validateText } from '../../shared/lib/validation.ts'
import { ConfirmDialog } from '../../shared/ui/ConfirmDialog.tsx'
import { Modal } from '../../shared/ui/Modal.tsx'
import { useToast } from '../../shared/ui/ToastContext.ts'
import { useBoard } from '../board/BoardContext.ts'
import { ColorSwatches } from './ColorSwatches.tsx'
import { LabelChip } from './LabelChip.tsx'
import * as labelApi from './labelApi.ts'
import styles from './LabelModal.module.css'

// 編集中の行
type Editing = { id: number; name: string; color: string; error: string | null; saving: boolean }

// API のエラーのうち、名前の入力欄の下に出すもののメッセージ。それ以外（通信の失敗など）は null
function inlineErrorOf(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null
  if (error.code === 'VALIDATION_ERROR') {
    const fields = fieldErrorsOf(error)
    return fields.name ?? fields.color ?? error.detail
  }
  if (error.code === 'LABEL_NAME_DUPLICATED' || error.code === 'LIMIT_EXCEEDED') return error.detail
  return null
}

// W-05 ラベル管理（モーダル。要件 T-01・T-03）
// 追加・変更・削除は、ボタンを押したらすぐに API を呼ぶ（カード詳細のような「まとめて保存」ではない）
export function LabelModal({ onClose }: { onClose: () => void }) {
  const { board, dispatch } = useBoard()
  const { showApiError } = useToast()
  const labels = board?.labels ?? []

  const [editing, setEditing] = useState<Editing | null>(null)
  const [newName, setNewName] = useState('')
  const [newColor, setNewColor] = useState(LABEL_COLORS[0].code)
  const [newError, setNewError] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  const [deleting, setDeleting] = useState<LabelResponse | null>(null) // 削除の確認ダイアログを出しているラベル
  const newNameRef = useRef<HTMLInputElement>(null)
  const editButtons = useRef(new Map<number, HTMLButtonElement>()) // 行ごとの「編集」ボタン

  // 編集を終える。入力欄が消えるので、その行の「編集」ボタンにカーソルを戻す（描き直したあとで）
  const endEdit = useCallback((labelId: number) => {
    setEditing(null)
    requestAnimationFrame(() => editButtons.current.get(labelId)?.focus())
  }, [])

  // 編集中に Esc を押したら、モーダルは閉じず、編集だけをやめる（確認ダイアログが出ているときは、そちらに任せる）
  const editingId = editing?.id ?? null
  const cancelEdit = useCallback(() => {
    if (editingId !== null) endEdit(editingId)
  }, [editingId, endEdit])
  useEscapeKey(editingId !== null && deleting === null, cancelEdit)

  // 画面側のチェック（要件 T-01）。問題があればメッセージを返す
  function check(name: string, selfId: number | null): string | null {
    const error = validateText(name, 'ラベル名', MAX_LENGTH.labelName)
    if (error) return error
    if (labels.some((label) => label.name === name.trim() && label.id !== selfId)) return MESSAGES.labelDuplicated
    return null
  }

  // A-16 ラベルの追加
  async function add() {
    if (adding) return
    let error = check(newName, null)
    if (!error && labels.length >= LIMITS.labels) error = MESSAGES.labelLimit
    setNewError(error)
    if (error) {
      newNameRef.current?.focus()
      return
    }
    setAdding(true)
    try {
      const label = await labelApi.addLabel({ name: newName.trim(), color: newColor })
      dispatch({ type: 'labelAdded', label })
      setNewName('') // 色はそのまま残して、続けて追加できるようにする
    } catch (e) {
      const message = inlineErrorOf(e)
      if (message) setNewError(message)
      else showApiError(e)
    } finally {
      setAdding(false)
      newNameRef.current?.focus()
    }
  }

  // A-17 ラベルの変更
  async function saveEdit() {
    if (!editing || editing.saving) return
    const error = check(editing.name, editing.id)
    if (error) {
      setEditing({ ...editing, error })
      return
    }
    setEditing({ ...editing, error: null, saving: true })
    try {
      const label = await labelApi.updateLabel(editing.id, { name: editing.name.trim(), color: editing.color })
      dispatch({ type: 'labelUpdated', label })
      endEdit(label.id)
    } catch (e) {
      const message = inlineErrorOf(e)
      setEditing((current) => (current ? { ...current, saving: false, error: message } : current))
      if (!message) showApiError(e)
    }
  }

  // A-18 ラベルの削除（確認ダイアログで「削除する」を押したあと）
  async function deleteLabel(labelId: number) {
    try {
      await labelApi.deleteLabel(labelId)
      dispatch({ type: 'labelDeleted', labelId })
      setEditing((current) => (current?.id === labelId ? null : current))
      newNameRef.current?.focus() // 押した「削除」ボタンは消えたので、新しいラベルの入力欄にカーソルを置く
    } catch (e) {
      showApiError(e)
    }
  }

  // 付いているカードの枚数は、手元のデータから数える（API設計書 A-18）
  function cardCount(labelId: number): number {
    return (board?.columns ?? []).flatMap((column) => column.cards).filter((card) => card.labelIds.includes(labelId))
      .length
  }

  function handleNewKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (isSubmitEnter(event)) {
      event.preventDefault()
      add()
    }
  }

  return (
    <Modal width={560} labelledBy="label-modal-title" onRequestClose={onClose}>
      <div className={styles.head}>
        <h3 id="label-modal-title" className={styles.title}>
          ラベル管理{' '}
          <span className={`muted ${styles.count}`}>
            {labels.length} / {LIMITS.labels}
          </span>
        </h3>
        <button type="button" className="icon-btn" aria-label="閉じる" onClick={onClose}>
          ✕
        </button>
      </div>

      <div className={styles.body}>
        {labels.length === 0 && <p className="muted">ラベルはまだありません。下から追加できます。</p>}
        <ul className={styles.list}>
          {labels.map((label) =>
            editing?.id === label.id ? (
              <EditRow
                key={label.id}
                editing={editing}
                onChange={(changes) => setEditing({ ...editing, ...changes })}
                onSave={saveEdit}
                onCancel={cancelEdit}
              />
            ) : (
              <li key={label.id} className={styles.row}>
                <LabelChip label={label} />
                <div className={styles.actions}>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    ref={(el) => {
                      if (el) editButtons.current.set(label.id, el)
                      return () => {
                        editButtons.current.delete(label.id)
                      }
                    }}
                    onClick={() =>
                      setEditing({ id: label.id, name: label.name, color: label.color, error: null, saving: false })
                    }
                  >
                    編集
                  </button>
                  <button type="button" className="btn btn-text-danger" onClick={() => setDeleting(label)}>
                    削除
                  </button>
                </div>
              </li>
            ),
          )}
        </ul>

        <div className={styles.newLabel}>
          <label className={styles.fieldLabel} htmlFor="new-label-name">
            新しいラベル
          </label>
          <div className={styles.newRow}>
            <input
              id="new-label-name"
              ref={newNameRef}
              className={`input ${newError ? 'is-error' : ''}`}
              placeholder={`ラベル名（${MAX_LENGTH.labelName}文字まで）`}
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={handleNewKeyDown}
              data-autofocus // 開いたときに、ここにカーソルを置く（Modal が探して置く）
            />
            <button type="button" className="btn btn-primary" disabled={adding} onClick={add}>
              追加
            </button>
          </div>
          {newError && <div className={`error ${styles.error}`}>{newError}</div>}
          <ColorSwatches name="new-label-color" value={newColor} onChange={setNewColor} />
        </div>
      </div>

      {deleting && (
        <ConfirmDialog
          title={`ラベル「${deleting.name}」を削除しますか？`}
          message={
            cardCount(deleting.id) > 0
              ? `付いているカード${cardCount(deleting.id)}枚から外れます。`
              : 'このラベルはどのカードにも付いていません。'
          }
          onAnswer={(ok) => {
            setDeleting(null)
            if (ok) deleteLabel(deleting.id)
          }}
        />
      )}
    </Modal>
  )
}

type EditRowProps = {
  editing: Editing
  onChange: (changes: Partial<Editing>) => void
  onSave: () => void
  onCancel: () => void
}

// 編集中の行。名前の入力欄と色の選択肢に変わる（画面設計書 W-05）。Enter で保存、Esc で取り消し
function EditRow({ editing, onChange, onSave, onCancel }: EditRowProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  // 編集を始めたときに、名前の入力欄にカーソルを置く
  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  return (
    <li className={`${styles.row} ${styles.editing}`}>
      <input
        ref={inputRef}
        className={`input ${editing.error ? 'is-error' : ''}`}
        aria-label="ラベル名"
        value={editing.name}
        onChange={(e) => onChange({ name: e.target.value })}
        onKeyDown={(e) => {
          if (isSubmitEnter(e)) {
            e.preventDefault()
            onSave()
          }
        }}
      />
      {editing.error && <div className="error">{editing.error}</div>}
      <ColorSwatches
        name={`label-color-${editing.id}`}
        value={editing.color}
        onChange={(color) => onChange({ color })}
      />
      <div className={styles.editActions}>
        <button type="button" className="btn btn-primary" disabled={editing.saving} onClick={onSave}>
          保存
        </button>
        <button type="button" className="btn" onClick={onCancel}>
          キャンセル
        </button>
      </div>
    </li>
  )
}
