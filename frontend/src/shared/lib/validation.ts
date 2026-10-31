// 入力チェック（要件 3章の入力ルール）。モックアップの validation.js から移した
// サーバー側（Spring Boot）でも同じルールで確かめる（要件 G-02）
// どの関数も、問題がなければ null、あればメッセージを返す

import { MAX_LENGTH, MESSAGES } from './constants.ts'

// 名前・タイトルなど。前後の空白を取り除いてから確かめる（API設計書 2.5）
export function validateText(value: string, fieldName: string, max: number): string | null {
  const trimmed = value.trim()
  if (trimmed === '') return MESSAGES.required(fieldName)
  if (trimmed.length > max) return MESSAGES.tooLong(fieldName, max)
  return null
}

export function validateEmail(value: string): string | null {
  const email = value.trim()
  if (email === '') return MESSAGES.required('メールアドレス')
  if (email.length > MAX_LENGTH.email) return MESSAGES.tooLong('メールアドレス', MAX_LENGTH.email)
  // 「何か@何か.何か」の形か。厳密なチェックはサーバー側に任せる
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return MESSAGES.emailFormat
  return null
}

// 8〜64文字、半角の英数字と記号のみ（空白は含めない）
export function validatePassword(value: string): string | null {
  if (value === '') return MESSAGES.required('パスワード')
  if (!/^[\x21-\x7e]{8,64}$/.test(value)) return MESSAGES.passwordRule
  return null
}

// パスワード（確認）。画面側だけでチェックする（API設計書 A-01）
export function validatePasswordConfirm(password: string, confirm: string): string | null {
  if (confirm === '') return MESSAGES.required('パスワード（確認）')
  if (confirm !== password) return MESSAGES.passwordMismatch
  return null
}
