// W-08 退会の確認（モーダル）

import * as api from '../api.js';
import { MESSAGES } from '../constants.js';
import { fieldErrors, handleApiError } from '../errors.js';
import { navigate, setFlash } from '../router.js';
import { focusAuto, openModal } from '../ui/modal.js';
import { escapeHtml, isEnterKey } from '../utils.js';

export function openWithdrawModal() {
  const view = { password: '', error: null, busy: false };
  const modal = openModal({
    width: 400,
    className: 'confirm',
    requestClose: () => {
      if (!view.busy) modal.close();
    },
  });

  function render() {
    modal.el.innerHTML = `
      <div class="modal-body confirm-body">
        <h3>退会しますか？</h3>
        <p class="muted">アカウントと、すべてのリスト・カード・ラベルを削除します。この操作は取り消せません。</p>
        <div class="field">
          <label for="withdraw-password">パスワード</label>
          <input id="withdraw-password" class="input ${view.error ? 'is-error' : ''}" type="password" name="password"
            autocomplete="current-password" value="${escapeHtml(view.password)}" ${view.busy ? 'disabled' : ''}
            ${view.error ? 'data-autofocus' : ''}>
          ${view.error ? `<div class="error">${escapeHtml(view.error)}</div>` : ''}
        </div>
      </div>
      <div class="modal-foot confirm-foot">
        <span class="spacer"></span>
        <button class="btn" data-action="cancel" ${view.error ? '' : 'data-autofocus'}>キャンセル</button>
        <button class="btn btn-danger" data-action="withdraw" ${canSubmit() ? '' : 'disabled'}>退会する</button>
      </div>`;
    focusAuto(modal.el);
  }

  // パスワードが空のときは押せない（画面設計書 W-08）
  function canSubmit() {
    return view.password !== '' && !view.busy;
  }

  async function submit() {
    if (!canSubmit()) return;
    view.busy = true;
    view.error = null;
    render();
    try {
      await api.withdraw({ password: view.password });
      setFlash(MESSAGES.withdrawn);
      navigate('/login'); // 画面が切り替わるときに、このモーダルも閉じる
    } catch (error) {
      view.busy = false;
      view.password = '';
      if (error.code === 'VALIDATION_ERROR') view.error = fieldErrors(error).password;
      else handleApiError(error);
      render();
    }
  }

  modal.el.addEventListener('input', (e) => {
    if (e.target.name !== 'password') return;
    view.password = e.target.value;
    // 入力のたびに描き直すと入力欄の位置がずれるので、ボタンだけ切り替える
    modal.el.querySelector('[data-action="withdraw"]').disabled = !canSubmit();
  });

  modal.el.addEventListener('click', (e) => {
    const action = e.target.closest('[data-action]')?.dataset.action;
    if (action === 'cancel') modal.requestClose();
    if (action === 'withdraw') submit();
  });

  modal.el.addEventListener('keydown', (e) => {
    if (isEnterKey(e) && e.target.name === 'password') {
      e.preventDefault();
      submit();
    }
  });

  render();
}
