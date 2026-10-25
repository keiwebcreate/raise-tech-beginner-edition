// 画面の右下に出す赤い通知（画面設計書 5章）。5秒で消える

import { escapeHtml } from '../utils.js';

export function showToast(message) {
  const root = document.getElementById('toast-root');
  // 「通信に失敗しました。時間をおいて…」を、最初の「。」で見出しと説明に分ける
  const [title, ...rest] = message.split('。');
  const detail = rest.join('。');

  const toast = document.createElement('div');
  toast.className = 'toast';
  toast.setAttribute('role', 'alert');
  toast.innerHTML = `
    <span class="toast-icon">!</span>
    <div>
      <strong>${escapeHtml(title)}</strong>
      ${detail ? `<span class="muted">${escapeHtml(detail)}</span>` : ''}
    </div>`;
  root.append(toast);
  setTimeout(() => toast.remove(), 5000);
}
