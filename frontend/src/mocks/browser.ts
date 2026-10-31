// ブラウザの中で通信を横取りする仕組み（MSW の Service Worker）を用意する
// 作業ファイル public/mockServiceWorker.js は `npx msw init public/` で作ったもの（手で直さない）

import { setupWorker } from 'msw/browser'
import { handlers } from './handlers.ts'

export const worker = setupWorker(...handlers)
