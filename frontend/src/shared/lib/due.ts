// 期限（dueDate）まわりの関数（要件 C-05）
// 期限は "2026-10-30" の形の文字列。日付はブラウザの日付で考える（要件 5.1）
// テストで「今日」を決められるように、today を引数で渡せるようにしている

// Date を "2026-10-30" の形にする
export function toDateString(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

// 今日から n 日後を "2026-10-30" の形で返す
export function daysFromToday(n: number, today: Date = new Date()): string {
  const date = new Date(today)
  date.setDate(date.getDate() + n)
  return toDateString(date)
}

// 期限の表示。今年なら "10/30"、年が違うなら "2027/1/5"
export function formatDue(dueDate: string, today: Date = new Date()): string {
  const [y, m, d] = dueDate.split('-').map(Number)
  return y === today.getFullYear() ? `${m}/${d}` : `${y}/${m}/${d}`
}

export type DueStatus = 'overdue' | 'soon' | 'normal'

// 期限の状態。期限切れ＝overdue（赤）、今日か明日＝soon（黄）、それ以外＝normal（グレー）
// "YYYY-MM-DD" の文字列は、そのまま大小を比べられる
export function dueStatus(dueDate: string, today: Date = new Date()): DueStatus {
  if (dueDate < toDateString(today)) return 'overdue'
  if (dueDate <= daysFromToday(1, today)) return 'soon'
  return 'normal'
}
