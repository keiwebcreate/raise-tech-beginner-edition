// W-01 ログイン画面

import * as api from '../api.js';
import { MESSAGES } from '../constants.js';
import { navigate, takeFlash } from '../router.js';
import { escapeHtml } from '../utils.js';

export async function loginScreen(root) {
  // ログイン済みならボードへ（画面設計書 3章「画面の出し分けのルール」）
  try {
    await api.getMe();
    navigate('/');
    return;
  } catch {
    // ログインしていない → このまま表示する
  }

  const state = {
    email: '',
    password: '',
    errors: {}, // 入力欄ごとのエラー
    alert: null, // 上の赤い枠のメッセージ
    notice: takeFlash(), // 前の画面からのお知らせ（セッション切れ・退会など）
    busy: false,
  };

  function render() {
    root.innerHTML = `
      <div class="auth-wrap">
        <form class="auth-card" novalidate>
          <div class="auth-logo">TaskBoard</div>
          <p class="auth-sub">ログインしてボードを開く</p>
          ${state.notice ? `<div class="notice">${escapeHtml(state.notice)}</div>` : ''}
          ${state.alert ? `<div class="alert" role="alert">${escapeHtml(state.alert)}</div>` : ''}
          <div class="field">
            <label for="login-email">メールアドレス</label>
            <input id="login-email" class="input ${state.errors.email ? 'is-error' : ''}" name="email" type="email"
              autocomplete="username" placeholder="you@example.com" value="${escapeHtml(state.email)}">
            ${state.errors.email ? `<div class="error">${escapeHtml(state.errors.email)}</div>` : ''}
          </div>
          <div class="field">
            <label for="login-password">パスワード</label>
            <input id="login-password" class="input ${state.errors.password ? 'is-error' : ''}" name="password" type="password"
              autocomplete="current-password" value="${escapeHtml(state.password)}">
            ${state.errors.password ? `<div class="error">${escapeHtml(state.errors.password)}</div>` : ''}
          </div>
          <button class="btn btn-primary btn-block" type="submit" ${state.busy ? 'disabled' : ''}>ログイン</button>
          <div class="divider">または</div>
          <button class="btn btn-block" type="button" data-action="guest" ${state.busy ? 'disabled' : ''}>ゲストとして試す</button>
          <p class="help center">登録なしで、サンプルデータ入りのボードを試せます</p>
          <div class="auth-foot">アカウントをお持ちでない方は <a href="#/signup">新規登録はこちら</a></div>
          <p class="mock-hint">モックアップ用のアカウント：taro@example.com ／ password123</p>
        </form>
      </div>`;
  }

  render();

  // 入力した値を state に覚えておく（描き直しても消えないように）
  root.addEventListener('input', (e) => {
    if (e.target.name in state) state[e.target.name] = e.target.value;
  });

  root.addEventListener('submit', async (e) => {
    e.preventDefault();
    // 画面側のチェックは「空でないか」だけ（API設計書 A-02）
    state.errors = {};
    if (state.email.trim() === '') state.errors.email = MESSAGES.required('メールアドレス');
    if (state.password === '') state.errors.password = MESSAGES.required('パスワード');
    state.alert = null;
    if (Object.keys(state.errors).length) {
      render();
      return;
    }
    await run(() => api.login({ email: state.email, password: state.password }));
  });

  root.addEventListener('click', async (e) => {
    if (e.target.closest('[data-action="guest"]')) {
      state.errors = {};
      await run(() => api.loginAsGuest());
    }
  });

  // ボタンを押せない状態にして API を呼び、成功したらボードへ
  async function run(request) {
    state.busy = true;
    state.notice = null;
    render();
    try {
      await request();
      navigate('/');
    } catch (error) {
      state.busy = false;
      state.password = '';
      state.alert = error.detail ?? MESSAGES.network;
      render();
    }
  }
}
