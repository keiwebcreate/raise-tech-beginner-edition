import { describe, expect, it } from 'vitest'
import { daysFromToday, dueStatus, formatDue } from './due.ts'

// 「今日」を 2026年10月31日 に決めてテストする（月は 0 から数えるので 9 が10月）
const today = new Date(2026, 9, 31)

describe('formatDue', () => {
  it('今年の期限は「月/日」で表示する', () => {
    expect(formatDue('2026-11-05', today)).toBe('11/5')
  })

  it('年が違う期限は「年/月/日」で表示する', () => {
    expect(formatDue('2027-01-05', today)).toBe('2027/1/5')
    expect(formatDue('2025-12-31', today)).toBe('2025/12/31')
  })
})

describe('dueStatus', () => {
  it('昨日までは期限切れ', () => {
    expect(dueStatus('2026-10-30', today)).toBe('overdue')
  })

  it('今日と明日はもうすぐ', () => {
    expect(dueStatus('2026-10-31', today)).toBe('soon')
    expect(dueStatus('2026-11-01', today)).toBe('soon') // 月をまたぐ
  })

  it('あさって以降はふつう', () => {
    expect(dueStatus('2026-11-02', today)).toBe('normal')
  })
})

describe('daysFromToday', () => {
  it('今日から n 日後の日付を返す（月・年をまたいでも正しい）', () => {
    expect(daysFromToday(0, today)).toBe('2026-10-31')
    expect(daysFromToday(1, today)).toBe('2026-11-01')
    expect(daysFromToday(62, today)).toBe('2027-01-01')
    expect(daysFromToday(-1, today)).toBe('2026-10-30')
  })
})
