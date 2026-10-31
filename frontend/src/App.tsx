import { BrowserRouter, Route, Routes } from 'react-router'
import { AuthProvider } from './features/auth/AuthProvider.tsx'
import { LoginPage } from './features/auth/LoginPage.tsx'
import { GuestOnly, RequireLogin } from './features/auth/RouteGuards.tsx'
import { SignupPage } from './features/auth/SignupPage.tsx'
import { BoardPage } from './features/board/BoardPage.tsx'
import { BoardProvider } from './features/board/BoardProvider.tsx'
import { NotFoundPage } from './shared/pages/NotFoundPage.tsx'

// URL と画面の結び付け（詳細設計書 4.2）
function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route
            path="/"
            element={
              <RequireLogin>
                <BoardProvider>
                  <BoardPage />
                </BoardProvider>
              </RequireLogin>
            }
          />
          <Route
            path="/login"
            element={
              <GuestOnly>
                <LoginPage />
              </GuestOnly>
            }
          />
          <Route
            path="/signup"
            element={
              <GuestOnly>
                <SignupPage />
              </GuestOnly>
            }
          />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  )
}

export default App
