import { useEffect, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import styles from './Modal.module.css'

type Props = {
  width?: number
  labelledBy?: string // 見出しの id（読み上げ用）
  // Esc・背景のクリックで呼ぶ。変更の確認などをしてから、呼んだ側が閉じる
  onRequestClose: () => void
  children: ReactNode
}

// モーダルの土台。HTML の <dialog> を showModal() で開く（品質チェック #31）
// ブラウザが、いちばん上に出す・後ろの画面を操作できなくする（Tab でも後ろに移らない）・背景を暗くする（::backdrop）
// 開いたら、中の data-autofocus の付いた部品（なければ最初の部品）にカーソルを置く
// 閉じたら、開く前にカーソルがあった場所（押したボタンなど）にカーソルを戻す
export function Modal({ width = 480, labelledBy, onRequestClose, children }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  // いちばん新しい onRequestClose を、ブラウザのイベントの処理から呼べるように覚えておく
  const requestCloseRef = useRef(onRequestClose)
  useEffect(() => {
    requestCloseRef.current = onRequestClose
  }, [onRequestClose])

  useEffect(() => {
    const dialog = dialogRef.current
    if (!dialog) return
    const previousFocus = document.activeElement as HTMLElement | null
    let closing = false

    dialog.showModal()
    dialog.querySelector<HTMLElement>('[data-autofocus]')?.focus()

    // Esc が押された。ブラウザが閉じるのを止めて、閉じるかどうかは onRequestClose で呼んだ側が決める
    // cancel はカーソルがどこにあっても、いちばん上のモーダルに届く（中の部品が消えてカーソルが外れたときも）
    // 中の部品（ラベルの一覧など）が先に Esc を使ったとき（keydown を preventDefault 済み）は、cancel は起きない
    function handleCancel(event: Event) {
      event.preventDefault()
      requestCloseRef.current()
    }
    // それでもブラウザが閉じてしまったとき（Esc の連打など）は、開き直して画面の状態とそろえる
    function handleClose() {
      if (!closing) dialog?.showModal()
    }
    // 背景（::backdrop）のクリックで閉じる。背景を押すと、クリックの対象は <dialog> そのものになる
    // 中で押して外で離した（文字を選んでいた）ときは閉じない
    let pressedOnBackdrop = false
    function handleMouseDown(event: MouseEvent) {
      pressedOnBackdrop = event.target === dialog
    }
    function handleClick(event: MouseEvent) {
      if (pressedOnBackdrop && event.target === dialog) requestCloseRef.current()
    }

    dialog.addEventListener('cancel', handleCancel)
    dialog.addEventListener('close', handleClose)
    dialog.addEventListener('mousedown', handleMouseDown)
    dialog.addEventListener('click', handleClick)
    return () => {
      closing = true
      dialog.removeEventListener('cancel', handleCancel)
      dialog.removeEventListener('close', handleClose)
      dialog.removeEventListener('mousedown', handleMouseDown)
      dialog.removeEventListener('click', handleClick)
      dialog.close()
      if (previousFocus?.isConnected) previousFocus.focus()
    }
  }, [])

  // キーはカーソルのあるモーダル（いちばん上）にだけ届く。Esc は上の cancel で扱う
  function handleKeyDown(event: KeyboardEvent<HTMLDialogElement>) {
    // Tab でモーダルの外に出ないように、最後の部品の次は最初へ、最初の部品の前は最後へ戻す
    // （<dialog> だけだと、最後の部品の次でブラウザのアドレスバーなどに移ってしまう）
    if (event.key === 'Tab') {
      const items = focusableItems(event.currentTarget)
      if (items.length === 0) return
      const first = items[0]
      const last = items[items.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
  }

  // body の直下に出す（置いた場所の CSS を受け継がないように）
  return createPortal(
    <dialog
      ref={dialogRef}
      className={styles.modal}
      style={{ width }}
      aria-labelledby={labelledBy}
      onKeyDown={handleKeyDown}
    >
      {children}
    </dialog>,
    document.body,
  )
}

// モーダルの中で、Tab で移れる部品（押せない状態のもの・見えないものは除く）
const FOCUSABLE = 'a[href], button, input, select, textarea, [tabindex]:not([tabindex="-1"])'

function focusableItems(dialog: HTMLElement): HTMLElement[] {
  return [...dialog.querySelectorAll<HTMLElement>(FOCUSABLE)].filter(
    (el) => !el.matches(':disabled') && el.getClientRects().length > 0,
  )
}
