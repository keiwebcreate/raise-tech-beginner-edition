// ニセのサーバー。API設計書（api-design.md）の A-01〜A-19 を、ブラウザの中だけで再現する。
//
// ・関数の名前・受け取る値・返す値・エラーの形は API設計書と同じにしてある
// ・DB の代わりに localStorage に保存する
// ・本物の通信のように、わざと少し待ってから答える（待ち時間はモック操作パネルで変えられる）
//
// React に移すときは、このファイルを「fetch で /api/... を呼ぶ関数」に置き換える。
// 画面のコードは、同じ名前の関数を呼んでいるので、ほとんど変えずに済む。

import { LABEL_COLORS, LEVELS, LIMITS, MAX_LENGTH, MESSAGES } from './constants.js';
import { createGuestSampleBoard, createInitialBoard, createUserSampleBoard } from './sample-data.js';
import { findCard } from './utils.js';
import { validateEmail, validatePassword, validateText } from './validation.js';

const STORAGE_KEY = 'taskboard-mockup-v1';

// ===== モック専用の設定（モック操作パネルから変える） =====
export const mockSettings = {
  delay: 300, // 答えるまでの待ち時間（ミリ秒）
  failNext: false, // true なら、次の通信を「サーバーエラー」にする
};
const settingListeners = new Set();
export function onMockSettingsChange(listener) {
  settingListeners.add(listener);
}
function notifySettings() {
  settingListeners.forEach((fn) => fn());
}

// ===== エラー（Problem Details の形。API設計書 4.2） =====
export class ApiError extends Error {
  constructor(status, code, detail, errors = undefined) {
    super(detail);
    this.status = status;
    this.code = code;
    this.detail = detail;
    this.errors = errors; // 入力チェックのときだけ [{ field, message }]
  }
}

function validationError(errors) {
  return new ApiError(400, 'VALIDATION_ERROR', MESSAGES.validation, errors);
}
function notFound() {
  return new ApiError(404, 'NOT_FOUND', MESSAGES.notFound);
}
function limitExceeded(message) {
  return new ApiError(409, 'LIMIT_EXCEEDED', message);
}

// ===== DB（localStorage） =====
function createInitialDb() {
  const db = {
    nextId: 1,
    accounts: [], // 登録済みのユーザー
    guests: [], // ゲストユーザー
    session: null, // ログイン中の人 { userId }
    loginFailures: [], // ログイン失敗の時刻（回数制限用）
    guestCreations: [], // ゲスト作成の時刻（回数制限用）
  };
  const newId = () => db.nextId++;
  // 最初から1人登録しておく（モックアップ用。パスワードをそのまま保存しているのはモックだけ）
  db.accounts.push({
    id: newId(),
    email: 'taro@example.com',
    password: 'password123',
    board: createUserSampleBoard(newId),
  });
  return db;
}

function loadDb() {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved) return JSON.parse(saved);
  } catch {
    // 読めないときは初期データで動かす
  }
  return createInitialDb();
}

function saveDb(db) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
  } catch {
    // 保存できなくても画面は動かす
  }
}

// すべてのAPIはこの関数を通す。待つ → （失敗の指示があれば）失敗する → 処理する → 保存する
async function handle(fn, { canFail = true } = {}) {
  await new Promise((resolve) => setTimeout(resolve, mockSettings.delay));
  if (canFail && mockSettings.failNext) {
    mockSettings.failNext = false;
    notifySettings();
    throw new ApiError(500, 'INTERNAL_ERROR', MESSAGES.network);
  }
  const db = loadDb();
  try {
    const result = fn(db);
    // 画面側で書き換えてもDBに影響しないように、コピーを返す
    return result === undefined ? undefined : structuredClone(result);
  } finally {
    saveDb(db);
  }
}

// ログイン中のユーザー。いなければ 401（API設計書 2.2）
function currentUser(db) {
  const userId = db.session?.userId;
  const user = db.accounts.find((u) => u.id === userId) ?? db.guests.find((u) => u.id === userId);
  if (!user) throw new ApiError(401, 'UNAUTHENTICATED', MESSAGES.unauthenticated);
  return user;
}

function userInfo(user) {
  return { id: user.id, email: user.email, isGuest: Boolean(user.isGuest) };
}

function newIdFor(db) {
  return () => db.nextId++;
}

// 「今から ms ミリ秒以内」の時刻だけ残す
function recent(times, ms) {
  const now = Date.now();
  return times.filter((t) => now - t < ms);
}

// 他人のデータ・存在しないデータは 404（API設計書 2.4）。
// モックでは、ログイン中のユーザーのボードの中だけを探すので、他人のものは見つからない
function findColumn(board, id) {
  const column = board.columns.find((c) => c.id === id);
  if (!column) throw notFound();
  return column;
}
function findLabel(board, id) {
  const label = board.labels.find((l) => l.id === id);
  if (!label) throw notFound();
  return label;
}

function checkText(value, field, fieldName, max) {
  const message = validateText(value, fieldName, max);
  if (message) throw validationError([{ field, message }]);
  return value.trim();
}

function columnInfo(column) {
  return { id: column.id, name: column.name };
}

// ===== 5.1 ログイン関係 =====

// A-01 ユーザー登録
export function signup({ email, password }) {
  return handle((db) => {
    const errors = [];
    const emailError = validateEmail(email);
    if (emailError) errors.push({ field: 'email', message: emailError });
    const passwordError = validatePassword(password);
    if (passwordError) errors.push({ field: 'password', message: passwordError });
    if (errors.length) throw validationError(errors);

    const normalized = email.trim().toLowerCase();
    if (db.accounts.some((u) => u.email === normalized)) {
      throw new ApiError(409, 'EMAIL_DUPLICATED', MESSAGES.emailDuplicated);
    }
    const newId = newIdFor(db);
    const user = { id: newId(), email: normalized, password, board: createInitialBoard(newId) };
    db.accounts.push(user);
    db.session = { userId: user.id };
    return userInfo(user);
  });
}

// A-02 ログイン
export function login({ email, password }) {
  return handle((db) => {
    db.loginFailures = recent(db.loginFailures, 15 * 60 * 1000);
    if (db.loginFailures.length >= 10) {
      throw new ApiError(429, 'RATE_LIMITED', MESSAGES.rateLimited);
    }
    const normalized = String(email ?? '').trim().toLowerCase();
    const user = db.accounts.find((u) => u.email === normalized && u.password === password);
    if (!user) {
      db.loginFailures.push(Date.now());
      throw new ApiError(401, 'LOGIN_FAILED', MESSAGES.loginFailed);
    }
    db.session = { userId: user.id };
    return userInfo(user);
  });
}

// A-03 ゲストログイン
export function loginAsGuest() {
  return handle((db) => {
    db.guestCreations = recent(db.guestCreations, 60 * 60 * 1000);
    if (db.guestCreations.length >= 10) {
      throw new ApiError(429, 'RATE_LIMITED', MESSAGES.rateLimited);
    }
    db.guestCreations.push(Date.now());
    const newId = newIdFor(db);
    const id = newId();
    const guest = { id, email: `guest-${id}@guest.invalid`, isGuest: true, board: createGuestSampleBoard(newId) };
    db.guests.push(guest);
    db.session = { userId: guest.id };
    return userInfo(guest);
  });
}

// A-04 ログアウト。ゲストならデータを削除する（要件 U-08）
export function logout() {
  return handle(
    (db) => {
      const userId = db.session?.userId;
      db.guests = db.guests.filter((g) => g.id !== userId);
      db.session = null;
    },
    { canFail: false },
  );
}

// A-05 ログイン中のユーザー
export function getMe() {
  return handle((db) => userInfo(currentUser(db)), { canFail: false });
}

// A-19 退会
export function withdraw({ password }) {
  return handle((db) => {
    const user = currentUser(db);
    if (user.isGuest) throw new ApiError(403, 'GUEST_NOT_ALLOWED', MESSAGES.guestNotAllowed);
    if (!password || password !== user.password) {
      throw validationError([{ field: 'password', message: MESSAGES.wrongPassword }]);
    }
    db.accounts = db.accounts.filter((u) => u.id !== user.id);
    db.session = null;
  });
}

// ===== 5.2 ボード =====

// A-06 ボードの取得
export function getBoard() {
  return handle((db) => currentUser(db).board);
}

// A-07 ボード名の変更
export function renameBoard({ name }) {
  return handle((db) => {
    const board = currentUser(db).board;
    board.name = checkText(name, 'name', 'ボード名', MAX_LENGTH.boardName);
    return { id: board.id, name: board.name };
  });
}

// ===== 5.3 リスト =====

// A-08 リストの追加（右端）
export function addColumn({ name }) {
  return handle((db) => {
    const board = currentUser(db).board;
    const trimmed = checkText(name, 'name', 'リスト名', MAX_LENGTH.columnName);
    if (board.columns.length >= LIMITS.columns) throw limitExceeded(MESSAGES.columnLimit);
    const column = { id: db.nextId++, name: trimmed, cards: [] };
    board.columns.push(column);
    return columnInfo(column);
  });
}

// A-09 リスト名の変更
export function renameColumn(columnId, { name }) {
  return handle((db) => {
    const column = findColumn(currentUser(db).board, columnId);
    column.name = checkText(name, 'name', 'リスト名', MAX_LENGTH.columnName);
    return columnInfo(column);
  });
}

// A-10 リストの削除（中のカードも消える）
export function deleteColumn(columnId) {
  return handle((db) => {
    const board = currentUser(db).board;
    findColumn(board, columnId);
    board.columns = board.columns.filter((c) => c.id !== columnId);
  });
}

// A-11 リストの並び替え
export function moveColumn(columnId, { position }) {
  return handle((db) => {
    const board = currentUser(db).board;
    const column = findColumn(board, columnId);
    if (!Number.isInteger(position) || position < 0 || position > board.columns.length - 1) {
      throw new ApiError(400, 'INVALID_POSITION', MESSAGES.invalidPosition);
    }
    board.columns = board.columns.filter((c) => c.id !== columnId);
    board.columns.splice(position, 0, column);
  });
}

// ===== 5.4 カード =====

// A-12 カードの追加（一番下）
export function addCard(columnId, { title }) {
  return handle((db) => {
    const column = findColumn(currentUser(db).board, columnId);
    const trimmed = checkText(title, 'title', 'タイトル', MAX_LENGTH.cardTitle);
    if (column.cards.length >= LIMITS.cardsPerColumn) throw limitExceeded(MESSAGES.cardLimit);
    const card = {
      id: db.nextId++,
      title: trimmed,
      description: null,
      importance: 'MEDIUM',
      urgency: 'MEDIUM',
      dueDate: null,
      labelIds: [],
    };
    column.cards.push(card);
    return card;
  });
}

// A-13 カードの保存（全項目をまとめて）
export function saveCard(cardId, body) {
  return handle((db) => {
    const board = currentUser(db).board;
    const found = findCard(board, cardId);
    if (!found) throw notFound();

    const errors = [];
    const titleError = validateText(body.title, 'タイトル', MAX_LENGTH.cardTitle);
    if (titleError) errors.push({ field: 'title', message: titleError });
    const description = body.description ?? '';
    if (description.length > MAX_LENGTH.description) {
      errors.push({ field: 'description', message: MESSAGES.tooLong('説明', MAX_LENGTH.description) });
    }
    const levels = LEVELS.map((l) => l.value);
    if (!levels.includes(body.importance)) errors.push({ field: 'importance', message: '重要度を選んでください' });
    if (!levels.includes(body.urgency)) errors.push({ field: 'urgency', message: '緊急度を選んでください' });
    if (errors.length) throw validationError(errors);

    const labelIds = [...new Set(body.labelIds)]; // 重複は1つにする
    labelIds.forEach((id) => findLabel(board, id)); // 他人・存在しないラベルは 404

    Object.assign(found.card, {
      title: body.title.trim(),
      description: description === '' ? null : description,
      importance: body.importance,
      urgency: body.urgency,
      dueDate: body.dueDate || null,
      labelIds,
    });
    return found.card;
  });
}

// A-14 カードの削除
export function deleteCard(cardId) {
  return handle((db) => {
    const found = findCard(currentUser(db).board, cardId);
    if (!found) throw notFound();
    found.column.cards = found.column.cards.filter((c) => c.id !== cardId);
  });
}

// A-15 カードの並び替え・別のリストへの移動
export function moveCard(cardId, { columnId, position }) {
  return handle((db) => {
    const board = currentUser(db).board;
    const found = findCard(board, cardId);
    if (!found) throw notFound();
    const target = findColumn(board, columnId);
    const sameColumn = target.id === found.column.id;
    const max = sameColumn ? target.cards.length - 1 : target.cards.length;
    if (!Number.isInteger(position) || position < 0 || position > max) {
      throw new ApiError(400, 'INVALID_POSITION', MESSAGES.invalidPosition);
    }
    if (!sameColumn && target.cards.length >= LIMITS.cardsPerColumn) throw limitExceeded(MESSAGES.cardLimit);

    found.column.cards = found.column.cards.filter((c) => c.id !== cardId);
    target.cards.splice(position, 0, found.card);
  });
}

// ===== 5.5 ラベル =====

function checkLabel(board, { name, color }, selfId = null) {
  const errors = [];
  const nameError = validateText(name, 'ラベル名', MAX_LENGTH.labelName);
  if (nameError) errors.push({ field: 'name', message: nameError });
  if (!LABEL_COLORS.some((c) => c.code === color)) errors.push({ field: 'color', message: '色を選んでください' });
  if (errors.length) throw validationError(errors);

  const trimmed = name.trim();
  // 自分自身の今の名前は重複とみなさない（A-17）
  if (board.labels.some((l) => l.name === trimmed && l.id !== selfId)) {
    throw new ApiError(409, 'LABEL_NAME_DUPLICATED', MESSAGES.labelDuplicated, [
      { field: 'name', message: MESSAGES.labelDuplicated },
    ]);
  }
  return trimmed;
}

// A-16 ラベルの追加
export function addLabel(body) {
  return handle((db) => {
    const board = currentUser(db).board;
    const name = checkLabel(board, body);
    if (board.labels.length >= LIMITS.labels) throw limitExceeded(MESSAGES.labelLimit);
    const label = { id: db.nextId++, name, color: body.color };
    board.labels.push(label);
    return label;
  });
}

// A-17 ラベルの変更
export function updateLabel(labelId, body) {
  return handle((db) => {
    const board = currentUser(db).board;
    const label = findLabel(board, labelId);
    label.name = checkLabel(board, body, labelId);
    label.color = body.color;
    return label;
  });
}

// A-18 ラベルの削除（付いていたカードからも外れる。要件 T-03）
export function deleteLabel(labelId) {
  return handle((db) => {
    const board = currentUser(db).board;
    findLabel(board, labelId);
    board.labels = board.labels.filter((l) => l.id !== labelId);
    board.columns.forEach((column) =>
      column.cards.forEach((card) => {
        card.labelIds = card.labelIds.filter((id) => id !== labelId);
      }),
    );
  });
}

// ===== モック専用の操作 =====

// セッション切れを起こす（要件 U-09 の確認用）
export function expireSession() {
  const db = loadDb();
  db.session = null;
  saveDb(db);
}

// すべてのデータを最初の状態に戻す
export function resetMockData() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    // 消せなくても続ける
  }
}
