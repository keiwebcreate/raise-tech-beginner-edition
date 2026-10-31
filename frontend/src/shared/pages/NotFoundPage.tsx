import { Link } from 'react-router'
import styles from './NotFoundPage.module.css'

// W-07 ページが見つかりません
export function NotFoundPage() {
  return (
    <div className={styles.centerBox}>
      <div className={styles.bigCode}>404</div>
      <h1 className={styles.title}>ページが見つかりません</h1>
      <p className="muted">URLが間違っているか、ページが削除された可能性があります。</p>
      <Link className="btn btn-primary" to="/">
        トップへ戻る
      </Link>
    </div>
  )
}
