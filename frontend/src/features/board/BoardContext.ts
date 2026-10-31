// ボードのデータを、ボード画面の中のどの部品からでも使えるようにする入れ物（Context）
// 中身は BoardProvider が用意する

import { createContext, useContext, type Dispatch } from 'react'
import type { BoardAction, BoardState } from './boardReducer.ts'

type BoardContextValue = {
  board: BoardState
  dispatch: Dispatch<BoardAction> // boardReducer に action を送る関数
}

export const BoardContext = createContext<BoardContextValue | null>(null)

// 部品の中で const { board, dispatch } = useBoard() のように使う
export function useBoard(): BoardContextValue {
  const value = useContext(BoardContext)
  if (!value) {
    throw new Error('useBoard は BoardProvider の中で使ってください')
  }
  return value
}
