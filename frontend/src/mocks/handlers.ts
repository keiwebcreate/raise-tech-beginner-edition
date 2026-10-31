// 偽の API の中身。「この URL が呼ばれたら、この JSON を返す」を並べる
// 本物の API（Spring Boot）につないだら、このフォルダ（src/mocks）ごと消す

import { http, HttpResponse } from 'msw/http'
import { createSampleBoard } from './sampleData.ts'

export const handlers = [
  // A-06 ボードの取得
  http.get('/api/board', () => HttpResponse.json(createSampleBoard())),
]
