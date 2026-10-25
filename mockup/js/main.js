// 入口。URL と画面を結び付けて、画面の切り替えを始める（画面設計書 2章）

import { setupMockPanel } from './mock-panel.js';
import { startRouter } from './router.js';
import { boardScreen } from './screens/board.js';
import { loginScreen } from './screens/login.js';
import { notFoundScreen } from './screens/not-found.js';
import { signupScreen } from './screens/signup.js';

startRouter({
  '/': boardScreen, // W-03
  '/login': loginScreen, // W-01
  '/signup': signupScreen, // W-02
  '*': notFoundScreen, // W-07
});

setupMockPanel();
