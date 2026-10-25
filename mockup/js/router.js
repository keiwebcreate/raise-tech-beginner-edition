// 画面の切り替え（ルーティング）。
// モックアップは「#/login」のように URL の # の後ろで画面を分ける（サーバーの設定がいらないため）。
// 本番の React では React Router を使い、「/login」のような普通のURLにする。

import { closeAllModals } from './ui/modal.js';

let routes = {};
let cleanup = null; // 前の画面の後片付け
let renderCount = 0;
let flashMessage = null;

// 次の画面に1回だけ出すメッセージ（例：「退会しました」）
export function setFlash(message) {
  flashMessage = message;
}
export function takeFlash() {
  const message = flashMessage;
  flashMessage = null;
  return message;
}

export function navigate(path) {
  if (currentPath() === path) {
    render();
  } else {
    location.hash = `#${path}`;
  }
}

function currentPath() {
  return location.hash.slice(1) || '/';
}

async function render() {
  const myCount = ++renderCount;
  cleanup?.();
  cleanup = null;
  closeAllModals();
  window.scrollTo(0, 0);

  const screen = routes[currentPath()] ?? routes['*'];
  // 画面ごとに新しい入れ物を作る。前の画面に付けたイベントは、入れ物ごと捨てられる
  const container = document.createElement('div');
  container.className = 'screen-root';
  document.getElementById('app').replaceChildren(container);
  const result = await screen(container);
  // 画面を作っている間に別の画面へ移っていたら、作ったほうを片付ける
  if (myCount !== renderCount) {
    result?.();
    return;
  }
  cleanup = result ?? null;
}

// routes は { '/login': 画面を作る関数, ... }。'*' はどれにも当てはまらないとき（W-07）
// 画面を作る関数は、後片付けの関数を返してもよい
export function startRouter(routeTable) {
  routes = routeTable;
  window.addEventListener('hashchange', render);
  render();
}
