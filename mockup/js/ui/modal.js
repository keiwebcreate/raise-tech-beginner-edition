// モーダルの土台と、確認ダイアログ（W-06）。
// モーダルは重ねて開ける（例：カード詳細の上にラベル管理、その上に確認ダイアログ）。
// Esc と背景のクリックは、一番上のモーダルにだけ効く。

import { escapeHtml } from '../utils.js';

const stack = []; // 開いているモーダル（後ろほど上）

document.addEventListener('keydown', (event) => {
  if (event.key !== 'Escape' || stack.length === 0) return;
  event.preventDefault();
  stack[stack.length - 1].requestClose();
});

// モーダルを開く。
// requestClose：Esc・背景クリック・×のときに呼ばれる（変更の確認などをしてから close を呼ぶ）
// onClose：どんな閉じ方でも、閉じたあとに1回呼ばれる（後片付け用）
// 戻り値の el にモーダルの中身を入れ、閉じるときは close() を呼ぶ
export function openModal({ width = 480, className = '', requestClose, onClose }) {
  const overlay = document.createElement('div');
  overlay.className = 'overlay';
  const modalEl = document.createElement('div');
  modalEl.className = `modal ${className}`;
  modalEl.style.width = `${width}px`;
  modalEl.setAttribute('role', 'dialog');
  modalEl.setAttribute('aria-modal', 'true');
  overlay.append(modalEl);
  document.getElementById('modal-root').append(overlay);

  // 背景のクリック。中で押して外で離した（文字を選んでいた）ときは閉じない
  let downOnOverlay = false;
  overlay.addEventListener('mousedown', (e) => {
    downOnOverlay = e.target === overlay;
  });
  overlay.addEventListener('click', (e) => {
    if (downOnOverlay && e.target === overlay) entry.requestClose();
  });

  const previousFocus = document.activeElement;
  let closed = false;
  const entry = {
    el: modalEl,
    requestClose: () => requestClose(),
    close() {
      if (closed) return;
      closed = true;
      overlay.remove();
      stack.splice(stack.indexOf(entry), 1);
      onClose?.();
      if (previousFocus?.isConnected) previousFocus.focus();
    },
  };
  stack.push(entry);
  return entry;
}

export function closeAllModals() {
  [...stack].reverse().forEach((entry) => entry.close());
}

// 中に [data-autofocus] があれば、そこに入力の位置を合わせる
export function focusAuto(container) {
  const target = container.querySelector('[data-autofocus]');
  if (!target) return;
  target.focus();
  if (typeof target.setSelectionRange === 'function' && target.type !== 'date' && target.type !== 'password') {
    const end = target.value.length;
    target.setSelectionRange(end, end);
  }
}

// 確認ダイアログ（W-06）。「削除する」なら true、キャンセルなら false で終わる Promise を返す
// 最初に選ばれているのは「キャンセル」（Enter の押し間違いで消さないため）
export function confirmDialog({ title, message, okText = '削除する' }) {
  return new Promise((resolve) => {
    const modal = openModal({
      width: 420,
      className: 'confirm',
      requestClose: () => finish(false),
    });
    modal.el.innerHTML = `
      <div class="modal-body confirm-body">
        <h3>${escapeHtml(title)}</h3>
        <p class="muted">${escapeHtml(message)}</p>
      </div>
      <div class="modal-foot confirm-foot">
        <span class="spacer"></span>
        <button class="btn" data-answer="no" data-autofocus>キャンセル</button>
        <button class="btn btn-danger" data-answer="yes">${escapeHtml(okText)}</button>
      </div>`;
    modal.el.addEventListener('click', (e) => {
      const button = e.target.closest('[data-answer]');
      if (button) finish(button.dataset.answer === 'yes');
    });
    focusAuto(modal.el);

    function finish(answer) {
      modal.close();
      resolve(answer);
    }
  });
}
