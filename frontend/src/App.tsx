import { BrowserRouter, Route, Routes } from 'react-router'
import { BoardPage } from './features/board/BoardPage.tsx'
import { BoardProvider } from './features/board/BoardProvider.tsx'
import { NotFoundPage } from './shared/pages/NotFoundPage.tsx'

// URL と画面の結び付け（詳細設計書 4.2）
// ログインしているかの確認と /login・/signup は、ログインを作るときに足す
function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/"
          element={
            <BoardProvider>
              <BoardPage />
            </BoardProvider>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
