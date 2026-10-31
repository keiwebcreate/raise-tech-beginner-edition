import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/variables.css'
import './styles/global.css'
import App from './App.tsx'

// 開発中（npm run dev）だけ、偽の API（MSW）を動かす。本番用（npm run build）には入らない
// ログインと本物の GET /api/board ができたら、この関数と src/mocks を消す
async function enableMocking() {
  if (!import.meta.env.DEV) return
  const { worker } = await import('./mocks/browser.ts')
  // 偽物を用意していない URL（画像など）は、そのまま本物へ通す
  await worker.start({ onUnhandledFrame: 'bypass' })
}

// 偽の API の準備ができてから画面を表示する（先に表示すると、最初の通信が横取りされない）
enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  )
})
