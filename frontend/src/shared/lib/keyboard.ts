import type { KeyboardEvent } from 'react'

// 送信してよい Enter か。日本語の変換を確定するための Enter では false（画面設計書 5章、詳細設計書 4.6）
// keyCode 229 は、一部のブラウザで変換中のキーに付く値
export function isSubmitEnter(event: KeyboardEvent): boolean {
  return event.key === 'Enter' && !event.nativeEvent.isComposing && event.keyCode !== 229
}
