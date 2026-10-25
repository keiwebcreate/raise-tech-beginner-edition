// W-05 ラベル管理（モーダル）
// 追加・変更・削除は、ボタンを押したらすぐに API を呼ぶ（カード詳細のような「まとめて保存」ではない）

import * as api from '../api.js';
import { getBoard, subscribe, updateBoard } from '../board-store.js';
import { LABEL_COLORS, LIMITS, MAX_LENGTH, MESSAGES } from '../constants.js';
import { fieldErrors, handleApiError } from '../errors.js';
import { confirmDialog, focusAuto, openModal } from '../ui/modal.js';
import { escapeHtml, isEnterKey, labelChipHtml } from '../utils.js';
import { validateText } from '../validation.js';

export function openLabelModal() {
  const view = {
    editing: null, // 編集中の行 { id, name, color, error, saving }
    newName: '',
    newColor: LABEL_COLORS[0].code,
    newError: null,
    adding: false,
    focus: true, // 描いたあとに [data-autofocus] へ入力の位置を合わせるか
  };

  const modal = openModal({
    width: 560,
    className: 'label-modal',
    requestClose: () => modal.close(),
    onClose: () => unsubscribe(),
  });
  const unsubscribe = subscribe((board) => (board ? render() : modal.close()));

  // ===== 描く =====

  function render() {
    const board = getBoard();
    modal.el.innerHTML = `
      <div class="modal-head">
        <h3>ラベル管理 <span class="muted count-text">${board.labels.length} / ${LIMITS.labels}</span></h3>
        <button class="icon-btn" data-action="close" aria-label="閉じる">✕</button>
      </div>
      <div class="modal-body">
        ${
          board.labels.length
            ? board.labels.map(rowHtml).join('')
            : '<p class="muted">ラベルはまだありません。下から追加できます。</p>'
        }
        <div class="new-label">
          <label class="field-label" for="new-label-name">新しいラベル</label>
          <div class="new-label-row">
            <input id="new-label-name" class="input ${view.newError ? 'is-error' : ''}" name="newName"
              placeholder="ラベル名（${MAX_LENGTH.labelName}文字まで）" value="${escapeHtml(view.newName)}"
              ${view.editing ? '' : 'data-autofocus'}>
            <button class="btn btn-primary" data-action="add" ${view.adding ? 'disabled' : ''}>追加</button>
          </div>
          ${view.newError ? `<div class="error">${escapeHtml(view.newError)}</div>` : ''}
          ${swatchesHtml(view.newColor, 'new')}
        </div>
      </div>`;
    if (view.focus) {
      focusAuto(modal.el);
      view.focus = false;
    }
  }

  function rowHtml(label) {
    const edit = view.editing;
    if (edit?.id !== label.id) {
      return `
        <div class="label-row">
          ${labelChipHtml(label)}
          <div class="actions">
            <button class="btn btn-ghost" data-action="edit" data-label-id="${label.id}">編集</button>
            <button class="btn btn-text-danger" data-action="delete" data-label-id="${label.id}">削除</button>
          </div>
        </div>`;
    }
    return `
      <div class="label-row is-editing">
        <input class="input ${edit.error ? 'is-error' : ''}" name="editName" aria-label="ラベル名"
          value="${escapeHtml(edit.name)}" data-autofocus>
        ${edit.error ? `<div class="error">${escapeHtml(edit.error)}</div>` : ''}
        ${swatchesHtml(edit.color, 'edit')}
        <div class="row">
          <button class="btn btn-primary" data-action="save-edit" ${edit.saving ? 'disabled' : ''}>保存</button>
          <button class="btn" data-action="cancel-edit">キャンセル</button>
        </div>
      </div>`;
  }

  // 8色の選択肢（target は 'new' か 'edit'）
  function swatchesHtml(selected, target) {
    return `
      <div class="swatches" role="radiogroup" aria-label="色">
        ${LABEL_COLORS.map(
          (c) => `
          <button class="sw ${c.code === selected ? 'on' : ''}" style="--c:${c.code}" role="radio"
            aria-checked="${c.code === selected}" aria-label="${c.name}" title="${c.name}"
            data-action="pick-color" data-target="${target}" data-color="${c.code}"></button>`,
        ).join('')}
      </div>`;
  }

  // ===== 操作 =====

  // 画面側のチェック。問題があればメッセージを返す
  function check(name, selfId = null) {
    const error = validateText(name, 'ラベル名', MAX_LENGTH.labelName);
    if (error) return error;
    if (getBoard().labels.some((l) => l.name === name.trim() && l.id !== selfId)) return MESSAGES.labelDuplicated;
    return null;
  }

  async function add() {
    if (view.adding) return;
    view.newError = check(view.newName);
    if (!view.newError && getBoard().labels.length >= LIMITS.labels) view.newError = MESSAGES.labelLimit;
    if (view.newError) {
      view.focus = true;
      render();
      return;
    }
    view.adding = true;
    render();
    try {
      const label = await api.addLabel({ name: view.newName, color: view.newColor });
      view.newName = '';
      view.adding = false;
      view.focus = true;
      updateBoard((board) => board.labels.push(label)); // ここで描き直される
    } catch (error) {
      view.adding = false;
      if (error.errors) view.newError = fieldErrors(error).name;
      else if (error.code === 'LIMIT_EXCEEDED') view.newError = error.detail;
      else handleApiError(error);
      render();
    }
  }

  async function saveEdit() {
    const edit = view.editing;
    if (!edit || edit.saving) return;
    edit.error = check(edit.name, edit.id);
    if (edit.error) {
      view.focus = true;
      render();
      return;
    }
    edit.saving = true;
    render();
    try {
      const saved = await api.updateLabel(edit.id, { name: edit.name, color: edit.color });
      view.editing = null;
      updateBoard((board) => {
        Object.assign(
          board.labels.find((l) => l.id === saved.id),
          saved,
        );
      });
    } catch (error) {
      edit.saving = false;
      if (error.errors) edit.error = fieldErrors(error).name;
      else handleApiError(error);
      render();
    }
  }

  async function deleteLabel(labelId) {
    const board = getBoard();
    const label = board.labels.find((l) => l.id === labelId);
    const count = board.columns.flatMap((c) => c.cards).filter((card) => card.labelIds.includes(labelId)).length;
    const ok = await confirmDialog({
      title: `ラベル「${label.name}」を削除しますか？`,
      message: count > 0 ? `付いているカード${count}枚から外れます。` : 'このラベルはどのカードにも付いていません。',
    });
    if (!ok) return;
    try {
      await api.deleteLabel(labelId);
      if (view.editing?.id === labelId) view.editing = null;
      updateBoard((b) => {
        b.labels = b.labels.filter((l) => l.id !== labelId);
        b.columns.forEach((column) =>
          column.cards.forEach((card) => {
            card.labelIds = card.labelIds.filter((id) => id !== labelId);
          }),
        );
      });
    } catch (error) {
      handleApiError(error);
    }
  }

  // ===== イベント =====

  modal.el.addEventListener('input', (e) => {
    if (e.target.name === 'newName') view.newName = e.target.value;
    if (e.target.name === 'editName' && view.editing) view.editing.name = e.target.value;
  });

  modal.el.addEventListener('click', (e) => {
    const target = e.target.closest('[data-action]');
    if (!target) return;
    const labelId = Number(target.dataset.labelId);
    switch (target.dataset.action) {
      case 'close':
        modal.close();
        break;
      case 'edit': {
        const label = getBoard().labels.find((l) => l.id === labelId);
        view.editing = { id: label.id, name: label.name, color: label.color, error: null, saving: false };
        view.focus = true;
        render();
        break;
      }
      case 'cancel-edit':
        view.editing = null;
        render();
        break;
      case 'save-edit':
        saveEdit();
        break;
      case 'delete':
        deleteLabel(labelId);
        break;
      case 'add':
        add();
        break;
      case 'pick-color':
        if (target.dataset.target === 'new') view.newColor = target.dataset.color;
        else if (view.editing) view.editing.color = target.dataset.color;
        render();
        break;
    }
  });

  modal.el.addEventListener('keydown', (e) => {
    if (isEnterKey(e) && e.target.name === 'newName') {
      e.preventDefault();
      add();
    }
    if (isEnterKey(e) && e.target.name === 'editName') {
      e.preventDefault();
      saveEdit();
    }
    if (e.key === 'Escape' && view.editing) {
      e.stopPropagation(); // モーダルは閉じず、編集だけやめる
      view.editing = null;
      render();
    }
  });

  render();
}
