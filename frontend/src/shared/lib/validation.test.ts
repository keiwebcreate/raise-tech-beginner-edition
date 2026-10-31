import { describe, expect, it } from 'vitest'
import { validateEmail, validatePassword, validatePasswordConfirm, validateText } from './validation.ts'

describe('validateText', () => {
  it('前後の空白を取り除いて、空なら「入力してください」', () => {
    expect(validateText('   ', 'タイトル', 100)).toBe('タイトルを入力してください')
  })

  it('上限を超えたら「〜文字以内」。空白を取り除いた長さで数える', () => {
    expect(validateText('あ'.repeat(101), 'タイトル', 100)).toBe('タイトルは100文字以内で入力してください')
    expect(validateText(` ${'あ'.repeat(100)} `, 'タイトル', 100)).toBeNull()
  })
})

describe('validateEmail', () => {
  it('正しい形なら null（前後の空白は取り除いて確かめる）', () => {
    expect(validateEmail('taro@example.com')).toBeNull()
    expect(validateEmail('  taro@example.com  ')).toBeNull()
  })

  it('空なら「入力してください」', () => {
    expect(validateEmail('')).toBe('メールアドレスを入力してください')
  })

  it('形が正しくなければエラー', () => {
    expect(validateEmail('taro')).toBe('メールアドレスの形式が正しくありません')
    expect(validateEmail('taro@example')).toBe('メールアドレスの形式が正しくありません')
  })

  it('254文字を超えたらエラー', () => {
    const email = `${'a'.repeat(243)}@example.com` // 255文字
    expect(validateEmail(email)).toBe('メールアドレスは254文字以内で入力してください')
  })
})

describe('validatePassword', () => {
  it('8〜64文字の半角英数字・記号なら null', () => {
    expect(validatePassword('password')).toBeNull()
    expect(validatePassword('P@ss-w0rd!')).toBeNull()
    expect(validatePassword('a'.repeat(64))).toBeNull()
  })

  it('空なら「入力してください」', () => {
    expect(validatePassword('')).toBe('パスワードを入力してください')
  })

  it('短すぎ・長すぎ・全角・空白入りはエラー', () => {
    const message = 'パスワードは8〜64文字の半角英数字・記号で入力してください'
    expect(validatePassword('pass123')).toBe(message) // 7文字
    expect(validatePassword('a'.repeat(65))).toBe(message)
    expect(validatePassword('ぱすわーど1234')).toBe(message)
    expect(validatePassword('pass word1')).toBe(message)
  })
})

describe('validatePasswordConfirm', () => {
  it('同じなら null、違えば「一致しません」、空なら「入力してください」', () => {
    expect(validatePasswordConfirm('password123', 'password123')).toBeNull()
    expect(validatePasswordConfirm('password123', 'password124')).toBe('パスワードが一致しません')
    expect(validatePasswordConfirm('password123', '')).toBe('パスワード（確認）を入力してください')
  })
})
