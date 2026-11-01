import react from '@vitejs/plugin-react'
import type { Plugin } from 'vite'
// vitest/config の defineConfig は、Vite の設定に Vitest の設定（test）を足せる
import { defineConfig } from 'vitest/config'

// 画面の CSP（読み込んでよいものの決まり。要件 NS-10、詳細設計書 4.9）
// - スクリプト・通信・画像などは、自分のサイト（'self'）のものだけ
// - 見た目（style）だけは書き込みも許す（'unsafe-inline'）。ドラッグ＆ドロップのライブラリが <style> を足すため
// - frame-ancestors（ほかのサイトへの埋め込みの禁止）は <meta> では効かないので、AWS で配信する側のヘッダーで付ける
const CONTENT_SECURITY_POLICY = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ')

// 本番用に作る HTML（npm run build）にだけ、CSP の <meta> を入れる。
// 開発中の Vite は、画面を書き換えるためのスクリプトを HTML に埋め込むので、入れると動かなくなる
function contentSecurityPolicy(): Plugin {
  return {
    name: 'content-security-policy',
    apply: 'build',
    transformIndexHtml() {
      return [
        {
          tag: 'meta',
          attrs: { 'http-equiv': 'Content-Security-Policy', content: CONTENT_SECURITY_POLICY },
          injectTo: 'head-prepend',
        },
      ]
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), contentSecurityPolicy()],
  server: {
    // 画面のポートは 5173 に決めている（CLAUDE.md）。使われていたら、5174 などに勝手にずらさずエラーで止める
    port: 5173,
    strictPort: true,
    // 開発中、画面から /api/... を呼ぶと Spring Boot（8080）へ転送する
    // 画面と API が同じ場所（localhost:5173）に見えるので、Cookie（セッション・CSRF）がそのまま使える
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  // npm run preview（本番用に作った画面を手元で動かす）も、開発中と同じ 5173 にする。/api の転送は server.proxy と同じものを使う
  preview: {
    port: 5173,
    strictPort: true,
  },
  test: {
    // src の中の xxx.test.ts を自動テストとして実行する
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
