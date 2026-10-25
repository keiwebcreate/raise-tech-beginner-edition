// 画面に表示しているボードのデータを1か所で持つ。
// データが変わったら、登録しておいた関数（画面を描き直す関数など）を呼ぶ。
// ボード画面・カード詳細・ラベル管理が同じデータを見るので、どこで変えても全部に反映される。
// React では useState と Context（または状態管理ライブラリ）がこの役目をする。

let board = null;
const listeners = new Set();

export function getBoard() {
  return board;
}

export function setBoard(next) {
  board = next;
  listeners.forEach((listener) => listener(board));
}

// 今のデータのコピーを渡して書き換えてもらい、それを新しいデータにする。
// 元のデータを直接書き換えないのは、React と同じ考え方（失敗したときに元へ戻しやすい）
export function updateBoard(change) {
  const copy = structuredClone(board);
  change(copy);
  setBoard(copy);
}

// 変わったときに呼んでほしい関数を登録する。戻り値の関数を呼ぶと登録を解除する
export function subscribe(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
