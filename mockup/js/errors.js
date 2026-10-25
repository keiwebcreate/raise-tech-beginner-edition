// APIのエラーの共通の扱い（API設計書 4.3 の「React の動き」）。
// 入力チェックのエラー（VALIDATION_ERROR など）は、それぞれの画面で入力欄の下に出す。
// それ以外はここで、通知を出すかログイン画面へ戻す。

import { MESSAGES } from './constants.js';
import { navigate, setFlash } from './router.js';
import { showToast } from './ui/toast.js';

export function handleApiError(error) {
  if (error.code === 'UNAUTHENTICATED') {
    // ログイン中にセッションが切れた（要件 U-09）
    setFlash(MESSAGES.sessionExpired);
    navigate('/login');
    return;
  }
  showToast(error.detail ?? MESSAGES.network);
}

// API の errors（[{ field, message }]）を { field: message } の形にする
export function fieldErrors(error) {
  const result = {};
  (error.errors ?? []).forEach(({ field, message }) => {
    result[field] ??= message;
  });
  return result;
}
