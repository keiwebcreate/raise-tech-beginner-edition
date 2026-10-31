// アプリ全体で使う決まった値（要件定義書 3.7、画面設計書 6章・7章）
// モックアップの constants.js から移した

// データ量の上限（要件 3.7）
export const LIMITS = {
  columns: 20,
  cardsPerColumn: 100,
  labels: 30,
}

// 入力の文字数の上限（要件 3章）
export const MAX_LENGTH = {
  boardName: 50,
  columnName: 50,
  cardTitle: 100,
  description: 2000,
  labelName: 20,
  email: 254,
}

// ラベルの8色（画面設計書 6章）。darkText が true の色は文字を黒にする
export const LABEL_COLORS = [
  { name: '赤', code: '#e5484d', darkText: false },
  { name: 'オレンジ', code: '#f76b15', darkText: false },
  { name: '黄', code: '#ffc53d', darkText: true },
  { name: '緑', code: '#30a46c', darkText: false },
  { name: '水色', code: '#0090ff', darkText: false },
  { name: '青', code: '#3e63dd', darkText: false },
  { name: '紫', code: '#8e4ec6', darkText: false },
  { name: 'グレー', code: '#8b8d98', darkText: false },
]

// 画面に出すメッセージ（画面設計書 7章）
export const MESSAGES = {
  required: (field: string) => `${field}を入力してください`,
  tooLong: (field: string, max: number) => `${field}は${max}文字以内で入力してください`,
  emailFormat: 'メールアドレスの形式が正しくありません',
  passwordRule: 'パスワードは8〜64文字の半角英数字・記号で入力してください',
  passwordMismatch: 'パスワードが一致しません',
  emailDuplicated: 'このメールアドレスは登録済みです',
  loginFailed: 'メールアドレスまたはパスワードが正しくありません',
  rateLimited: 'しばらくしてから再度お試しください',
  sessionExpired: 'ログインの有効期限が切れました。もう一度ログインしてください',
  labelDuplicated: '同じ名前のラベルがあります',
  columnLimit: `リストは${LIMITS.columns}個までです`,
  cardLimit: `このリストのカードは${LIMITS.cardsPerColumn}枚までです`,
  labelLimit: `ラベルは${LIMITS.labels}個までです`,
  network: '通信に失敗しました。時間をおいて再度お試しください',
  notFound: '対象のデータが見つかりません。画面を再読み込みしてください',
  wrongPassword: 'パスワードが正しくありません',
  withdrawn: '退会しました。ご利用ありがとうございました',
  validation: '入力内容に誤りがあります',
  invalidPosition: '移動先の位置が正しくありません',
  guestNotAllowed: 'ゲストは退会できません',
  unauthenticated: 'ログインしてください',
}
