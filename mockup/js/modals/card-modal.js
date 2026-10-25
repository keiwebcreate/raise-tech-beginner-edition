// W-04 カード詳細（モーダル）
//
// 開いたときにカードの値を draft（下書き）にコピーし、画面の操作は draft だけを変える。
// 「保存」で draft をまとめて API に送る（A-13）。保存せずに閉じるときは、変更があれば確認する。

import * as api from '../api.js';
import { getBoard, subscribe, updateBoard } from '../board-store.js';
import { LEVELS, MAX_LENGTH, MESSAGES } from '../constants.js';
import { fieldErrors, handleApiError } from '../errors.js';
import { confirmDialog, focusAuto, openModal } from '../ui/modal.js';
import { escapeHtml, findCard, labelChipHtml, linkify } from '../utils.js';
import { validateText } from '../validation.js';
import { openLabelModal } from './label-modal.js';

export function openCardModal(cardId) {
  const found = findCard(getBoard(), cardId);
  if (!found) return;

  // API の形（null あり）を、入力欄で扱いやすい形（空文字）にする
  const toForm = (card) => ({
    title: card.title,
    description: card.description ?? '',
    importance: card.importance,
    urgency: card.urgency,
    dueDate: card.dueDate ?? '',
    labelIds: [...card.labelIds],
  });
  const original = toForm(found.card);
  const draft = toForm(found.card);
  const view = {
    editingDescription: draft.description === '', // 説明が空なら最初から入力欄を出す
    popoverOpen: false,
    errors: {},
    saving: false,
  };

  const modal = openModal({
    width: 640,
    className: 'card-modal',
    requestClose,
    onClose: () => unsubscribe(),
  });

  // ラベル管理（上に重ねて開く）でラベルが消えたら、下書きからも外す
  const unsubscribe = subscribe((board) => {
    const exists = board && findCard(board, cardId);
    if (!exists) {
      modal.close();
      return;
    }
    const labelIds = board.labels.map((l) => l.id);
    draft.labelIds = draft.labelIds.filter((id) => labelIds.includes(id));
    original.labelIds = original.labelIds.filter((id) => labelIds.includes(id));
    render();
  });

  function isDirty() {
    // ラベルは付けた順番が違っても同じとみなす
    const normalize = (form) => JSON.stringify({ ...form, labelIds: [...form.labelIds].sort() });
    return normalize(draft) !== normalize(original);
  }

  // ===== 描く =====

  function render() {
    const board = getBoard();
    const current = findCard(board, cardId);
    if (!current) return;
    const { errors } = view;
    const selectedLabels = board.labels.filter((l) => draft.labelIds.includes(l.id));

    modal.el.innerHTML = `
      <div class="modal-head">
        <span class="muted small">リスト：${escapeHtml(current.column.name)}</span>
        <button class="icon-btn" data-action="close" aria-label="閉じる">✕</button>
      </div>
      <div class="modal-body">
        <div class="field">
          <label for="card-title">タイトル</label>
          <input id="card-title" class="input title-input ${errors.title ? 'is-error' : ''}" name="title"
            value="${escapeHtml(draft.title)}" data-autofocus>
          ${errorHtml(errors.title)}
        </div>

        <div class="field label-field">
          <span class="field-label">ラベル</span>
          <div class="labels align-center">
            ${selectedLabels.map((l) => labelChipHtml(l)).join('')}
            <button class="add-chip" data-action="toggle-popover" aria-label="ラベルを選ぶ" aria-expanded="${view.popoverOpen}">＋</button>
          </div>
          ${view.popoverOpen ? popoverHtml(board) : ''}
        </div>

        <div class="grid-3">
          <div class="field">
            <label for="card-importance">重要度</label>
            <select id="card-importance" class="select" name="importance">${levelOptions(draft.importance)}</select>
          </div>
          <div class="field">
            <label for="card-urgency">緊急度</label>
            <select id="card-urgency" class="select" name="urgency">${levelOptions(draft.urgency)}</select>
          </div>
          <div class="field">
            <label for="card-due">期限</label>
            <input id="card-due" class="input" type="date" name="dueDate" value="${escapeHtml(draft.dueDate)}">
            <div class="help">${draft.dueDate ? '<button class="link-btn" data-action="clear-due">期限をクリア</button>' : '&nbsp;'}</div>
          </div>
        </div>

        <div class="field">
          <label for="card-description">説明</label>
          ${descriptionHtml()}
          ${errorHtml(errors.description)}
        </div>
      </div>
      <div class="modal-foot">
        <button class="btn btn-text-danger" data-action="delete" ${view.saving ? 'disabled' : ''}>カードを削除</button>
        <span class="spacer"></span>
        <button class="btn" data-action="close">キャンセル</button>
        <button class="btn btn-primary" data-action="save" ${view.saving ? 'disabled' : ''}>${view.saving ? '保存中…' : '保存'}</button>
      </div>`;
  }

  function popoverHtml(board) {
    const options = board.labels.length
      ? board.labels
          .map((label) => {
            const on = draft.labelIds.includes(label.id);
            return `
              <button class="opt" data-action="toggle-label" data-label-id="${label.id}" role="menuitemcheckbox" aria-checked="${on}">
                <span class="check ${on ? 'on' : ''}">${on ? '✓' : ''}</span>${labelChipHtml(label)}
              </button>`;
          })
          .join('')
      : '<p class="muted small">ラベルがありません。「ラベルを管理」から作れます。</p>';
    return `
      <div class="popover label-popover" data-popover role="menu">
        <div class="popover-title">ラベルを選ぶ</div>
        ${options}
        <div class="popover-foot"><button class="link-btn" data-action="manage-labels">ラベルを管理</button></div>
      </div>`;
  }

  function descriptionHtml() {
    if (view.editingDescription) {
      return `
        <textarea id="card-description" class="textarea ${view.errors.description ? 'is-error' : ''}" name="description"
          placeholder="説明を入力（URLはリンクになります）">${escapeHtml(draft.description)}</textarea>
        <div class="counter" data-counter>${counterText()}</div>`;
    }
    return `
      <div class="desc-view" data-action="edit-description" tabindex="0" role="button" aria-label="説明を編集">${linkify(draft.description)}</div>
      <div class="help">クリックすると編集できます</div>`;
  }

  function counterText() {
    const remaining = MAX_LENGTH.description - draft.description.length;
    return remaining >= 0 ? `残り ${remaining} 文字` : `<span class="error-text">${-remaining} 文字オーバー</span>`;
  }

  // ===== 操作 =====

  async function requestClose() {
    if (view.saving) return;
    if (isDirty()) {
      const ok = await confirmDialog({
        title: '変更を破棄しますか？',
        message: '保存していない変更は失われます。',
        okText: '破棄する',
      });
      if (!ok) return;
    }
    modal.close();
  }

  async function save() {
    view.errors = {};
    const titleError = validateText(draft.title, 'タイトル', MAX_LENGTH.cardTitle);
    if (titleError) view.errors.title = titleError;
    if (draft.description.length > MAX_LENGTH.description) {
      view.errors.description = MESSAGES.tooLong('説明', MAX_LENGTH.description);
    }
    if (Object.keys(view.errors).length) {
      render();
      return;
    }

    view.saving = true;
    view.popoverOpen = false;
    render();
    try {
      const saved = await api.saveCard(cardId, { ...draft, dueDate: draft.dueDate || null });
      modal.close();
      updateBoard((board) => {
        Object.assign(findCard(board, cardId).card, saved);
      });
    } catch (error) {
      view.saving = false;
      if (error.code === 'VALIDATION_ERROR') view.errors = fieldErrors(error);
      else handleApiError(error);
      render();
    }
  }

  async function deleteCard() {
    const ok = await confirmDialog({
      title: 'カードを削除しますか？',
      message: `「${original.title}」を削除します。この操作は取り消せません。`,
    });
    if (!ok) return;
    try {
      await api.deleteCard(cardId);
      modal.close();
      updateBoard((board) => {
        const { column } = findCard(board, cardId);
        column.cards = column.cards.filter((c) => c.id !== cardId);
      });
    } catch (error) {
      handleApiError(error);
    }
  }

  // ===== イベント =====

  modal.el.addEventListener('input', (e) => {
    const { name, value } = e.target;
    if (!(name in draft)) return;
    draft[name] = value;
    if (name === 'description') {
      modal.el.querySelector('[data-counter]').innerHTML = counterText();
    }
  });

  modal.el.addEventListener('click', (e) => {
    if (e.target.closest('a')) return; // 説明文のリンクは、そのまま開く
    const target = e.target.closest('[data-action]');
    const action = target?.dataset.action;

    // ラベルの一覧の外をクリックしたら閉じる
    if (view.popoverOpen && !e.target.closest('[data-popover]') && action !== 'toggle-popover') {
      view.popoverOpen = false;
      render();
    }

    switch (action) {
      case 'close':
        requestClose();
        break;
      case 'toggle-popover':
        view.popoverOpen = !view.popoverOpen;
        render();
        break;
      case 'toggle-label': {
        const id = Number(target.dataset.labelId);
        draft.labelIds = draft.labelIds.includes(id) ? draft.labelIds.filter((x) => x !== id) : [...draft.labelIds, id];
        render();
        break;
      }
      case 'manage-labels':
        view.popoverOpen = false;
        render();
        openLabelModal();
        break;
      case 'clear-due':
        draft.dueDate = '';
        render();
        break;
      case 'edit-description':
        view.editingDescription = true;
        render();
        startDescriptionEditing();
        break;
      case 'save':
        save();
        break;
      case 'delete':
        deleteCard();
        break;
    }
  });

  modal.el.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && view.popoverOpen) {
      e.stopPropagation(); // モーダルは閉じず、ラベルの一覧だけ閉じる
      view.popoverOpen = false;
      render();
    }
    if (e.key === 'Enter' && e.target.matches('.desc-view')) {
      e.preventDefault();
      view.editingDescription = true;
      render();
      startDescriptionEditing();
    }
  });

  function startDescriptionEditing() {
    const textarea = modal.el.querySelector('textarea');
    textarea.focus();
    textarea.setSelectionRange(textarea.value.length, textarea.value.length);
  }

  render();
  focusAuto(modal.el);
}

function levelOptions(selected) {
  return LEVELS.map(
    (level) => `<option value="${level.value}" ${level.value === selected ? 'selected' : ''}>${level.text}</option>`,
  ).join('');
}

function errorHtml(message) {
  return message ? `<div class="error">${escapeHtml(message)}</div>` : '';
}
