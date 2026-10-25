// W-07 ページが見つかりません
// 「トップへ戻る」は #/ へ。ログインしていなければ、ボード画面がログイン画面へ移す

export function notFoundScreen(root) {
  root.innerHTML = `
    <div class="center-box">
      <div class="big-code">404</div>
      <h1 class="center-title">ページが見つかりません</h1>
      <p class="muted">URLが間違っているか、ページが削除された可能性があります。</p>
      <a class="btn btn-primary" href="#/">トップへ戻る</a>
    </div>`;
}
