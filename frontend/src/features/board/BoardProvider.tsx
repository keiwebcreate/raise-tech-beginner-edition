import { useReducer, type ReactNode } from 'react'
import { BoardContext } from './BoardContext.ts'
import { boardReducer } from './boardReducer.ts'

// useReducer でボードのデータを持ち、BoardContext で中の部品に配る
export function BoardProvider({ children }: { children: ReactNode }) {
  const [board, dispatch] = useReducer(boardReducer, null)
  return <BoardContext value={{ board, dispatch }}>{children}</BoardContext>
}
