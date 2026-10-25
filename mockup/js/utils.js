// どの画面からも使う小さな関数

import { LABEL_COLORS } from './constants.js';

// 文字列をHTMLに埋め込めるように変換する（XSS対策）。
// ユーザーが入力した文字は、必ずこれを通してから innerHTML に入れる。
// React では自動で行われるので不要になる。
export function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

// 日本語入力の変換を確定するための Enter では反応しないようにする
export function isEnterKey(event) {
  return event.key === 'Enter' && !event.isComposing && event.keyCode !== 229;
}

// Date を "2026-10-25" の形にする（ブラウザの日付で。要件 5.1）
export function toDateString(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

// 今日から n 日後の "2026-10-25" 形式
export function daysFromToday(n) {
  const date = new Date();
  date.setDate(date.getDate() + n);
  return toDateString(date);
}

// 期限の表示。今年なら "10/30"、年が違うなら "2027/1/5"（要件 C-05）
export function formatDue(dueDate, today = new Date()) {
  const [y, m, d] = dueDate.split('-').map(Number);
  return y === today.getFullYear() ? `${m}/${d}` : `${y}/${m}/${d}`;
}

// 期限の状態。期限切れ＝overdue、今日か明日＝soon、それ以外＝normal（要件 C-05）
// "YYYY-MM-DD" の文字列はそのまま大小を比べられる
export function dueStatus(dueDate, today = new Date()) {
  const todayStr = toDateString(today);
  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);
  if (dueDate < todayStr) return 'overdue';
  if (dueDate <= toDateString(tomorrow)) return 'soon';
  return 'normal';
}

// 説明文をHTMLにする。改行はCSS（white-space: pre-wrap）で反映し、
// http:// と https:// で始まる部分だけをリンクにする（要件 C-02・5.2 XSS）
export function linkify(text) {
  return String(text)
    .split(/(https?:\/\/[^\s]+)/g)
    .map((part, i) => {
      // split に () を付けると、URLの部分は奇数番目に入る
      if (i % 2 === 1) {
        const url = escapeHtml(part);
        return `<a class="link" href="${url}" target="_blank" rel="noopener noreferrer">${url}</a>`;
      }
      return escapeHtml(part);
    })
    .join('');
}

// ラベル1つ分のHTML
export function labelChipHtml(label, extraClass = '') {
  const color = LABEL_COLORS.find((c) => c.code === label.color);
  const dark = color?.darkText ? ' dark-text' : '';
  return `<span class="lbl${dark} ${extraClass}" style="--c:${label.color}">${escapeHtml(label.name)}</span>`;
}

// ボードの中からカードを探す。見つかったらカードと所属リストを返す
export function findCard(board, cardId) {
  for (const column of board.columns) {
    const card = column.cards.find((c) => c.id === cardId);
    if (card) return { card, column };
  }
  return null;
}
