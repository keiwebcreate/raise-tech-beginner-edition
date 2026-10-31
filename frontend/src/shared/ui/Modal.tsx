import { useEffect, useRef, useState, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import styles from './Modal.module.css'

// 開いているモーダルの順番（後ろほど上）。Esc は一番上のモーダルにだけ効かせる
// （例：カード詳細の上に確認ダイアログが開いているときは、確認ダイアログだけが閉じる）
const openStack: number[] = []
let nextId = 1

type Props = {
  width?: number
  labelledBy?: string // 見出しの id（読み上げ用）
  // Esc・背景のクリックで呼ぶ。変更の確認などをしてから、呼んだ側が閉じる
  onRequestClose: () => void
  children: ReactNode
}

// モーダルの土台。画面全体を薄暗くして、その上に中身を出す
// 閉じたら、開く前にカーソルがあった場所（押したボタンなど）にカーソルを戻す
export function Modal({ width = 480, labelledBy, onRequestClose, children }: Props) {
  const [id] = useState(() => nextId++)
  // いちばん新しい onRequestClose を Esc の処理から呼べるように、覚えておく
  const requestCloseRef = useRef(onRequestClose)
  useEffect(() => {
    requestCloseRef.current = onRequestClose
  }, [onRequestClose])

  useEffect(() => {
    openStack.push(id)
    const previousFocus = document.activeElement as HTMLElement | null

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && openStack[openStack.length - 1] === id) {
        event.preventDefault()
        requestCloseRef.current()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      openStack.splice(openStack.indexOf(id), 1)
      if (previousFocus?.isConnected) previousFocus.focus()
    }
  }, [id])

  // 背景のクリックで閉じる。中で押して外で離した（文字を選んでいた）ときは閉じない
  const pressedOnOverlay = useRef(false)

  return createPortal(
    <div
      className={styles.overlay}
      onMouseDown={(e) => {
        pressedOnOverlay.current = e.target === e.currentTarget
      }}
      onClick={(e) => {
        if (pressedOnOverlay.current && e.target === e.currentTarget) onRequestClose()
      }}
    >
      <div className={styles.modal} style={{ width }} role="dialog" aria-modal="true" aria-labelledby={labelledBy}>
        {children}
      </div>
    </div>,
    document.body,
  )
}
