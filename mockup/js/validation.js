// 入力チェック（要件 3章の入力ルール）。
// 本番では同じルールを React（画面側）と Spring Boot（サーバー側）の両方に書く（要件 G-02）。
// モックアップでは画面とニセのサーバー（api.js）の両方からこのファイルを使う。
// どの関数も、問題がなければ null、あればメッセージを返す。

import { MAX_LENGTH, MESSAGES } from './constants.js';

// 名前・タイトルなど。前後の空白を取り除いてから確かめる（API設計書 2.5）
export function validateText(value, fieldName, max) {
  const trimmed = String(value ?? '').trim();
  if (trimmed === '') return MESSAGES.required(fieldName);
  if (trimmed.length > max) return MESSAGES.tooLong(fieldName, max);
  return null;
}

export function validateEmail(value) {
  const email = String(value ?? '').trim();
  if (email === '') return MESSAGES.required('メールアドレス');
  if (email.length > MAX_LENGTH.email) return MESSAGES.tooLong('メールアドレス', MAX_LENGTH.email);
  // 「何か@何か.何か」の形か。厳密なチェックはサーバー側に任せる
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return MESSAGES.emailFormat;
  return null;
}

// 8〜64文字、半角の英数字と記号のみ（空白は含めない）
export function validatePassword(value) {
  const password = String(value ?? '');
  if (password === '') return MESSAGES.required('パスワード');
  if (!/^[\x21-\x7e]{8,64}$/.test(password)) return MESSAGES.passwordRule;
  return null;
}
