import { useEffect, type RefObject } from 'react'

// active の間、ref の部品の外がクリックされたら onOutside を呼ぶ（メニューや一覧を閉じるのに使う）
export function useOutsideClick(ref: RefObject<HTMLElement | null>, active: boolean, onOutside: () => void): void {
  useEffect(() => {
    if (!active) return
    function handleMouseDown(event: MouseEvent) {
      if (!ref.current?.contains(event.target as Node)) onOutside()
    }
    document.addEventListener('mousedown', handleMouseDown)
    return () => document.removeEventListener('mousedown', handleMouseDown)
  }, [ref, active, onOutside])
}
