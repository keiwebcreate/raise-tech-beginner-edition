import { BrowserRouter, Route, Routes } from 'react-router'
import { AuthProvider } from './features/auth/AuthProvider.tsx'
import { LoginPage } from './features/auth/LoginPage.tsx'
import { GuestOnly, RequireLogin } from './features/auth/RouteGuards.tsx'
import { SignupPage } from './features/auth/SignupPage.tsx'
import { BoardListPage } from './features/board/BoardListPage.tsx'
import { BoardRoute } from './features/board/BoardRoute.tsx'
import { NotFoundPage } from './shared/pages/NotFoundPage.tsx'
import { ToastProvider } from './shared/ui/ToastProvider.tsx'

// URL と画面の結び付け（詳細設計書 4.2）
function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Routes>
            <Route
              path="/"
              element={
                <RequireLogin>
                  <BoardListPage />
                </RequireLogin>
              }
            />
            <Route
              path="/boards/:boardId"
              element={
                <RequireLogin>
                  <BoardRoute />
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
      </ToastProvider>
    </BrowserRouter>
  )
}

export default App
