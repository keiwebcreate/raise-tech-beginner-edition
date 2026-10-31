import { useEffect } from 'react'

// active の間、Esc が押されたら onEscape を呼ぶ（メニューや一覧を閉じるのに使う）
// ほかより先に受け取り（capture）、ほかには伝えない。モーダルの中で使っても、モーダルは閉じず、一覧だけが閉じる
export function useEscapeKey(active: boolean, onEscape: () => void): void {
  useEffect(() => {
    if (!active) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape') return
      event.preventDefault() // ブラウザの「Esc でモーダルを閉じる」も止める
      event.stopPropagation()
      onEscape()
    }
    document.addEventListener('keydown', handleKeyDown, true)
    return () => document.removeEventListener('keydown', handleKeyDown, true)
  }, [active, onEscape])
}
