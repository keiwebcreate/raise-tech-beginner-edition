// W-02 ユーザー登録画面

import * as api from '../api.js';
import { MESSAGES } from '../constants.js';
import { fieldErrors } from '../errors.js';
import { navigate } from '../router.js';
import { escapeHtml } from '../utils.js';
import { validateEmail, validatePassword } from '../validation.js';

export async function signupScreen(root) {
  try {
    await api.getMe();
    navigate('/');
    return;
  } catch {
    // ログインしていない → このまま表示する
  }

  const state = { email: '', password: '', passwordConfirm: '', errors: {}, alert: null, busy: false };

  function fieldHtml({ name, label, type, autocomplete, help = '' }) {
    const error = state.errors[name];
    return `
      <div class="field">
        <label for="signup-${name}">${label}</label>
        <input id="signup-${name}" class="input ${error ? 'is-error' : ''}" name="${name}" type="${type}"
          autocomplete="${autocomplete}" value="${escapeHtml(state[name])}">
        ${error ? `<div class="error">${escapeHtml(error)}</div>` : ''}
        ${help ? `<div class="help">${help}</div>` : ''}
      </div>`;
  }

  function render() {
    root.innerHTML = `
      <div class="auth-wrap">
        <form class="auth-card" novalidate>
          <div class="auth-logo">TaskBoard</div>
          <p class="auth-sub">アカウントを作成</p>
          ${state.alert ? `<div class="alert" role="alert">${escapeHtml(state.alert)}</div>` : ''}
          ${fieldHtml({ name: 'email', label: 'メールアドレス', type: 'email', autocomplete: 'username' })}
          ${fieldHtml({ name: 'password', label: 'パスワード', type: 'password', autocomplete: 'new-password', help: '8〜64文字の半角英数字・記号' })}
          ${fieldHtml({ name: 'passwordConfirm', label: 'パスワード（確認）', type: 'password', autocomplete: 'new-password' })}
          <button class="btn btn-primary btn-block" type="submit" ${state.busy ? 'disabled' : ''}>登録する</button>
          <div class="auth-foot">アカウントをお持ちの方は <a href="#/login">ログインはこちら</a></div>
        </form>
      </div>`;
  }

  render();

  root.addEventListener('input', (e) => {
    if (e.target.name in state) state[e.target.name] = e.target.value;
  });

  root.addEventListener('submit', async (e) => {
    e.preventDefault();
    state.alert = null;
    state.errors = {};
    const emailError = validateEmail(state.email);
    const passwordError = validatePassword(state.password);
    if (emailError) state.errors.email = emailError;
    if (passwordError) state.errors.password = passwordError;
    // パスワード（確認）は画面側だけでチェックする（API設計書 A-01）
    if (state.passwordConfirm === '') state.errors.passwordConfirm = MESSAGES.required('パスワード（確認）');
    else if (state.passwordConfirm !== state.password) state.errors.passwordConfirm = MESSAGES.passwordMismatch;
    if (Object.keys(state.errors).length) {
      render();
      return;
    }

    state.busy = true;
    render();
    try {
      await api.signup({ email: state.email, password: state.password });
      navigate('/'); // 登録したら自動でログインしてボードへ（要件 U-01）
    } catch (error) {
      state.busy = false;
      if (error.code === 'EMAIL_DUPLICATED') state.errors.email = error.detail;
      else if (error.code === 'VALIDATION_ERROR') state.errors = fieldErrors(error);
      else state.alert = error.detail ?? MESSAGES.network;
      render();
    }
  });
}
