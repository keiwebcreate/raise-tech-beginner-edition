import { useLayoutEffect, useRef, useState } from 'react'
import { LinkifiedText } from '../../shared/ui/LinkifiedText.tsx'
import styles from './CardDescription.module.css'

// カードの説明（要件 C-09）。3行まで表示し、長いときは「もっと見る」で全文を開く
// 開いているかどうかは画面だけの状態なので、この部品の useState で持つ（サーバーには送らない）
export function CardDescription({ text }: { text: string }) {
  const [expanded, setExpanded] = useState(false)
  const [cut, setCut] = useState(false) // 3行に収まらず、切れているか
  const ref = useRef<HTMLDivElement>(null)

  // 3行を超えるかどうかは、画面に描いたあとの実際の高さで判断する
  useLayoutEffect(() => {
    const el = ref.current
    if (el && !expanded) {
      setCut(el.scrollHeight > el.clientHeight + 1)
    }
  }, [text, expanded])

  return (
    <>
      <div ref={ref} className={`${styles.desc} ${expanded ? styles.expanded : ''}`}>
        <LinkifiedText text={text} linkClassName={styles.link} />
      </div>
      {(expanded || cut) && (
        <button
          type="button"
          className={styles.toggle}
          aria-expanded={expanded}
          onClick={(e) => {
            e.stopPropagation() // カード（の詳細を開く操作）まで反応しないようにする
            setExpanded(!expanded)
          }}
        >
          {expanded ? '▲ 閉じる' : '▼ もっと見る'}
        </button>
      )}
    </>
  )
}
