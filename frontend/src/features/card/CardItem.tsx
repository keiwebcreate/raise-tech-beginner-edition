import type { CardResponse, LabelResponse, Level } from '../../api/types.ts'
import { dueStatus, formatDue } from '../../shared/lib/due.ts'
import { LabelChip } from '../label/LabelChip.tsx'
import { CardDescription } from './CardDescription.tsx'
import styles from './CardItem.module.css'

// 左端の色の帯（重要度。要件 C-06）
const IMPORTANCE_CLASS: Record<Level, string> = {
  HIGH: styles.high,
  MEDIUM: styles.mid,
  LOW: styles.low,
}

type Props = {
  card: CardResponse
  labels: LabelResponse[] // ボードのラベル全部（作成順）
  onOpen: () => void // カード詳細を開く
}

// リストの中のカード1枚。クリック（または Tab で選んで Enter）でカード詳細を開く
export function CardItem({ card, labels, onOpen }: Props) {
  // ボードのラベルの順番のまま、このカードに付いているものだけを選ぶ
  const cardLabels = labels.filter((label) => card.labelIds.includes(label.id))
  const showMeta = card.urgency === 'HIGH' || card.dueDate !== null

  return (
    <article
      className={`${styles.card} ${IMPORTANCE_CLASS[card.importance]}`}
      tabIndex={0}
      aria-label={`${card.title}（クリックで詳細を開く）`}
      onClick={onOpen}
      onKeyDown={(e) => {
        // カードの中のボタン（もっと見る）やリンクで押した Enter は、それぞれの動きに任せる
        if (e.key === 'Enter' && e.target === e.currentTarget) onOpen()
      }}
    >
      {cardLabels.length > 0 && (
        <div className={styles.labels}>
          {cardLabels.map((label) => (
            <LabelChip key={label.id} label={label} />
          ))}
        </div>
      )}
      <p className={styles.title}>{card.title}</p>
      {card.description && <CardDescription text={card.description} />}
      {showMeta && (
        <div className={styles.meta}>
          {card.urgency === 'HIGH' && <span className={styles.urgent}>至急</span>}
          {card.dueDate && (
            // 色を変えるのは overdue・soon だけ（normal のクラスはないので空にする）
            <span className={`${styles.due} ${styles[dueStatus(card.dueDate)] ?? ''}`} title="期限">
              {formatDue(card.dueDate)}
            </span>
          )}
        </div>
      )}
    </article>
  )
}
