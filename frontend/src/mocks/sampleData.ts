// サンプルのボード。中身はモックアップ（mockup/js/sample-data.js）のゲスト用と同じ
// 期限は「今日」からの日数で作る（いつ開いても、期限切れ・もうすぐの色を確かめられるように）

import type { BoardResponse } from '../api/types.ts'
import { daysFromToday } from '../shared/lib/due.ts'

export function createSampleBoard(today: Date = new Date()): BoardResponse {
  return {
    id: 1,
    name: 'マイボード',
    labels: [
      { id: 1, name: 'サンプル', color: '#f76b15' },
      { id: 2, name: '仕事', color: '#3e63dd' },
      { id: 3, name: 'プライベート', color: '#30a46c' },
    ],
    columns: [
      {
        id: 1,
        name: '未着手',
        cards: [
          {
            id: 1,
            title: 'サンプル：右のリストへドラッグしてみよう',
            description: null,
            importance: 'MEDIUM',
            urgency: 'MEDIUM',
            dueDate: null,
            labelIds: [1],
          },
          {
            id: 2,
            title: '会議の資料を準備する',
            description: null,
            importance: 'HIGH',
            urgency: 'HIGH',
            dueDate: daysFromToday(0, today),
            labelIds: [2],
          },
          {
            id: 3,
            title: '旅行の計画を立てる',
            description: null,
            importance: 'LOW',
            urgency: 'MEDIUM',
            dueDate: daysFromToday(20, today),
            labelIds: [3],
          },
        ],
      },
      {
        id: 2,
        name: '作業中',
        cards: [
          {
            id: 4,
            title: 'カードをクリックして詳細を編集',
            description: 'タイトル・説明・重要度・緊急度・期限・ラベルを編集できます。\n詳しくは https://example.com',
            importance: 'HIGH',
            urgency: 'MEDIUM',
            dueDate: null,
            labelIds: [1],
          },
        ],
      },
      { id: 3, name: '完了', cards: [] },
    ],
  }
}
