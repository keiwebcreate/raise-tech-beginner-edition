// サンプルデータ。形は API設計書 5.2 の GET /api/board のレスポンスと同じにしてある。
// React に移すときも、このままテスト用のデータとして使える。
// 期限は「今日」からの日数で作る（いつ開いても、期限切れ・もうすぐの色を確かめられるように）。
// newId は、重ならないIDを1つずつ返す関数（api.js から渡す）。

import { daysFromToday } from './utils.js';

function card(newId, title, fields = {}) {
  return {
    id: newId(),
    title,
    description: null,
    importance: 'MEDIUM',
    urgency: 'MEDIUM',
    dueDate: null,
    labelIds: [],
    ...fields,
  };
}

// 登録済みユーザー（taro@example.com）のボード。ワイヤーフレームの W-03 と同じ中身
export function createUserSampleBoard(newId) {
  const work = { id: newId(), name: '仕事', color: '#3e63dd' };
  const study = { id: newId(), name: '学習', color: '#8e4ec6' };
  const shopping = { id: newId(), name: '買い物', color: '#30a46c' };
  const later = { id: newId(), name: 'あとで', color: '#ffc53d' };

  return {
    id: newId(),
    name: 'マイボード',
    labels: [work, study, shopping, later],
    columns: [
      {
        id: newId(),
        name: '未着手',
        cards: [
          card(newId, '企画書のドラフトを作る', {
            importance: 'HIGH',
            dueDate: daysFromToday(-1),
            description: '来週の会議で使う。\n・目的\n・スケジュール\n・予算',
            labelIds: [work.id],
          }),
          card(newId, '歯医者の予約を取る', { urgency: 'HIGH', dueDate: daysFromToday(1) }),
          card(newId, '牛乳と卵を買う', { importance: 'LOW', labelIds: [shopping.id] }),
        ],
      },
      {
        id: newId(),
        name: '作業中',
        cards: [
          card(newId, 'ポートフォリオのREADMEを書く。スクリーンショットと技術構成図も入れる', {
            importance: 'HIGH',
            dueDate: daysFromToday(9),
            description:
              '見る人が最初に読む場所なので、わかりやすく書く。\n・アプリの概要とURL\n・使った技術（Spring Boot / React / MySQL / AWS）\n・参考：https://github.com/',
            labelIds: [study.id, work.id],
          }),
          card(newId, 'Spring Security の勉強', { dueDate: daysFromToday(72), labelIds: [study.id] }),
        ],
      },
      {
        id: newId(),
        name: '完了',
        cards: [
          card(newId, '要件定義書を作る', { labelIds: [study.id] }),
          card(newId, 'テーブル設計', { importance: 'LOW' }),
        ],
      },
    ],
  };
}

// ゲストのボード（要件 U-06）。使い方の説明をかねたサンプル
export function createGuestSampleBoard(newId) {
  const sample = { id: newId(), name: 'サンプル', color: '#f76b15' };
  const work = { id: newId(), name: '仕事', color: '#3e63dd' };
  const privateLabel = { id: newId(), name: 'プライベート', color: '#30a46c' };

  return {
    id: newId(),
    name: 'マイボード',
    labels: [sample, work, privateLabel],
    columns: [
      {
        id: newId(),
        name: '未着手',
        cards: [
          card(newId, 'サンプル：右のリストへドラッグしてみよう', { labelIds: [sample.id] }),
          card(newId, '会議の資料を準備する', {
            importance: 'HIGH',
            urgency: 'HIGH',
            dueDate: daysFromToday(0),
            labelIds: [work.id],
          }),
          card(newId, '旅行の計画を立てる', { importance: 'LOW', dueDate: daysFromToday(20), labelIds: [privateLabel.id] }),
        ],
      },
      {
        id: newId(),
        name: '作業中',
        cards: [
          card(newId, 'カードをクリックして詳細を編集', {
            importance: 'HIGH',
            description: 'タイトル・説明・重要度・緊急度・期限・ラベルを編集できます。\n詳しくは https://example.com',
            labelIds: [sample.id],
          }),
        ],
      },
      { id: newId(), name: '完了', cards: [] },
    ],
  };
}

// ユーザー登録したときの最初のボード（要件 U-02）
export function createInitialBoard(newId) {
  return {
    id: newId(),
    name: 'マイボード',
    labels: [],
    columns: [
      { id: newId(), name: '未着手', cards: [] },
      { id: newId(), name: '作業中', cards: [] },
      { id: newId(), name: '完了', cards: [] },
    ],
  };
}
