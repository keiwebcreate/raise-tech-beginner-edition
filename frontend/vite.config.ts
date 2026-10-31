import react from '@vitejs/plugin-react'
// vitest/config の defineConfig は、Vite の設定に Vitest の設定（test）を足せる
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 開発中、画面から /api/... を呼ぶと Spring Boot（8080）へ転送する
    // 画面と API が同じ場所（localhost:5173）に見えるので、Cookie（セッション・CSRF）がそのまま使える
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    // src の中の xxx.test.ts を自動テストとして実行する
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
