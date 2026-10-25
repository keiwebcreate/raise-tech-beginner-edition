// モック操作パネル（画面の左下）。本番にはない、確認用の道具。
// 本物のサーバーでしか起きないこと（通信の遅れ・失敗・セッション切れ）を、わざと起こせる。

import { expireSession, mockSettings, onMockSettingsChange, resetMockData } from './api.js';
import { confirmDialog } from './ui/modal.js';

export function setupMockPanel() {
  const root = document.getElementById('mock-root');
  root.innerHTML = `
    <details class="mock-panel">
      <summary>モック操作</summary>
      <div class="mock-body">
        <label class="mock-row">通信の待ち時間
          <select class="select" data-mock="delay">
            <option value="0">なし</option>
            <option value="300" selected>0.3秒</option>
            <option value="1500">1.5秒</option>
          </select>
        </label>
        <label class="mock-check"><input type="checkbox" data-mock="fail"> 次の通信を失敗させる</label>
        <button class="btn" data-mock="expire">セッション切れにする</button>
        <button class="btn" data-mock="reset">データを最初に戻す</button>
        <p class="mock-status" data-mock-status></p>
        <p class="mock-note">本番にはない、確認用のパネルです</p>
      </div>
    </details>`;

  const status = root.querySelector('[data-mock-status]');
  const failCheckbox = root.querySelector('[data-mock="fail"]');

  root.querySelector('[data-mock="delay"]').addEventListener('change', (e) => {
    mockSettings.delay = Number(e.target.value);
  });

  failCheckbox.addEventListener('change', (e) => {
    mockSettings.failNext = e.target.checked;
    status.textContent = e.target.checked ? '次の通信がサーバーエラーになります' : '';
  });
  // 失敗を1回起こしたら、チェックを外す
  onMockSettingsChange(() => {
    failCheckbox.checked = mockSettings.failNext;
    if (!mockSettings.failNext) status.textContent = '';
  });

  // 画面が切り替わったら、セッション切れのお知らせを消す
  window.addEventListener('hashchange', () => {
    if (!mockSettings.failNext) status.textContent = '';
  });

  root.querySelector('[data-mock="expire"]').addEventListener('click', () => {
    expireSession();
    status.textContent = 'セッションを切りました。次の操作でログイン画面に戻ります';
  });

  root.querySelector('[data-mock="reset"]').addEventListener('click', async () => {
    const ok = await confirmDialog({
      title: 'データを最初に戻しますか？',
      message: '登録したユーザー・ボードの中身をすべて消し、サンプルデータに戻します。',
      okText: '最初に戻す',
    });
    if (!ok) return;
    resetMockData();
    location.hash = '#/login';
    location.reload();
  });
}
