// W-03 ボード画面（ヘッダー・リスト・カード・ドラッグ＆ドロップ）
//
// 作り方は React と同じ考え方にしている。
// ・ボードのデータ（board-store.js）と、画面の状態（ui：どこを編集中か、どのメニューが開いているか）を持つ
// ・どちらかが変わったら render() で画面をまるごと描き直す
// ・ボタンなどには data-action="..." を付け、親の要素1か所でクリックを受け取って振り分ける

import * as api from '../api.js';
import { getBoard, setBoard, subscribe, updateBoard } from '../board-store.js';
import { LIMITS, MAX_LENGTH, MESSAGES } from '../constants.js';
import { fieldErrors, handleApiError } from '../errors.js';
import { openCardModal } from '../modals/card-modal.js';
import { openLabelModal } from '../modals/label-modal.js';
import { openWithdrawModal } from '../modals/withdraw-modal.js';
import { navigate } from '../router.js';
import { confirmDialog, focusAuto } from '../ui/modal.js';
import { showToast } from '../ui/toast.js';
import { dueStatus, escapeHtml, formatDue, isEnterKey, labelChipHtml, linkify } from '../utils.js';
import { validateText } from '../validation.js';

const IMPORTANCE_CLASS = { HIGH: 'p-high', MEDIUM: 'p-mid', LOW: 'p-low' };

export async function boardScreen(root) {
  // 読み込み中はスケルトンを出す（画面設計書 5章）
  root.innerHTML = headerOnlyHtml() + skeletonHtml();

  let user;
  try {
    user = await api.getMe();
  } catch {
    navigate('/login'); // ログインしていなければログイン画面へ
    return;
  }
  try {
    setBoard(await api.getBoard());
  } catch (error) {
    if (error.code === 'UNAUTHENTICATED') {
      handleApiError(error);
      return;
    }
    root.innerHTML = headerOnlyHtml() + loadErrorHtml(error);
    return;
  }

  // 画面の状態。null は「していない」
  const ui = {
    edit: null, // その場での名前変更 { kind: 'board' | 'column', id, value, error, saving }
    addCard: null, // カードの追加 { columnId, value, error, saving }
    addList: null, // リストの追加 { value, error, saving }
    menuColumnId: null, // 「…」メニューを開いているリスト
    userMenuOpen: false, // ヘッダーのメニュー
    expandedCardIds: new Set(), // 説明を全文表示しているカード（保存はしない）
  };
  let sortables = [];
  let rendering = false;

  // ===== 描く =====

  function render() {
    const board = getBoard();
    if (!board) return;
    rendering = true;
    sortables.forEach((s) => s.destroy());
    sortables = [];
    root.innerHTML = `
      ${headerHtml(board)}
      ${user.isGuest ? '<div class="guest-bar">ゲストモード：データはログアウトするか24時間たつと削除されます</div>' : ''}
      <main class="board-scroll">
        <div class="board">
          <div class="columns">${board.columns.map((column) => columnHtml(column, board)).join('')}</div>
          ${addListHtml()}
        </div>
      </main>`;
    setupSortable();
    updateDescriptionToggles();
    focusAuto(root);
    rendering = false;
  }

  function headerHtml(board) {
    const name =
      ui.edit?.kind === 'board'
        ? inlineInputHtml(ui.edit, 'ボード名', 'board-name-edit')
        : `<button class="board-name" data-action="edit-board-name" title="クリックして名前を変更">${escapeHtml(board.name)}</button>`;

    const userPart = user.isGuest
      ? `<span class="guest-badge">ゲスト</span>
         <button class="btn btn-ghost" data-action="logout">ログアウト</button>`
      : `<div class="menu-anchor">
           <button class="btn btn-ghost user ${ui.userMenuOpen ? 'is-open' : ''}" data-action="toggle-user-menu"
             aria-haspopup="menu" aria-expanded="${ui.userMenuOpen}">
             <span class="user-email">${escapeHtml(user.email)}</span> ▾
           </button>
           ${
             ui.userMenuOpen
               ? `<div class="menu user-menu" role="menu" data-menu>
                    <button role="menuitem" data-action="logout">ログアウト</button>
                    <button role="menuitem" class="danger" data-action="withdraw">退会する</button>
                  </div>`
               : ''
           }
         </div>`;

    return `
      <header class="app-header">
        <span class="app-name">TaskBoard</span>
        <span class="sep"></span>
        ${name}
        <div class="header-right">
          <button class="btn" data-action="open-labels">ラベル管理</button>
          ${userPart}
        </div>
      </header>`;
  }

  // その場で名前を変える入力欄（ボード名・リスト名）
  function inlineInputHtml(edit, label, className) {
    return `
      <span class="inline-edit ${className}">
        <input class="input ${edit.error ? 'is-error' : ''}" data-inline="edit" aria-label="${label}"
          value="${escapeHtml(edit.value)}" ${edit.saving ? 'disabled' : ''} data-autofocus>
        ${edit.error ? `<span class="error inline-error">${escapeHtml(edit.error)}</span>` : ''}
      </span>`;
  }

  function columnHtml(column, board) {
    const editing = ui.edit?.kind === 'column' && ui.edit.id === column.id;
    const name = editing
      ? inlineInputHtml(ui.edit, 'リスト名', 'list-name-edit')
      : `<button class="list-name" data-action="edit-column-name" data-column-id="${column.id}"
           title="クリックして名前を変更／ドラッグして並び替え">${escapeHtml(column.name)}</button>`;
    const menu =
      ui.menuColumnId === column.id
        ? `<div class="menu column-menu" role="menu" data-menu>
             <div class="menu-title">リストの操作</div>
             <button role="menuitem" class="danger" data-action="delete-column" data-column-id="${column.id}">リストを削除</button>
           </div>`
        : '';
    return `
      <section class="list" data-column-id="${column.id}">
        <div class="list-head">
          ${name}
          <span class="count">${column.cards.length}</span>
          <button class="icon-btn" data-action="toggle-column-menu" data-column-id="${column.id}"
            aria-label="リストのメニュー" aria-expanded="${ui.menuColumnId === column.id}">…</button>
        </div>
        ${menu}
        <div class="cards" data-column-id="${column.id}">
          ${column.cards.map((card) => cardHtml(card, board)).join('')}
        </div>
        ${addCardHtml(column)}
      </section>`;
  }

  function cardHtml(card, board) {
    const labels = board.labels.filter((label) => card.labelIds.includes(label.id));
    const meta = [];
    if (card.urgency === 'HIGH') meta.push('<span class="urgent">至急</span>');
    if (card.dueDate) {
      meta.push(`<span class="due ${dueStatus(card.dueDate)}" title="期限">${formatDue(card.dueDate)}</span>`);
    }
    return `
      <article class="card ${IMPORTANCE_CLASS[card.importance]}" data-action="open-card" data-card-id="${card.id}" tabindex="0">
        ${labels.length ? `<div class="labels">${labels.map((label) => labelChipHtml(label)).join('')}</div>` : ''}
        <p class="card-title">${escapeHtml(card.title)}</p>
        ${card.description ? cardDescriptionHtml(card) : ''}
        ${meta.length ? `<div class="card-meta">${meta.join('')}</div>` : ''}
      </article>`;
  }

  // 説明は3行まで表示し、長いときは「もっと見る」で開く。
  // 3行を超えるかどうかは、描いたあとに実際の高さで判断する（updateDescriptionToggles）
  function cardDescriptionHtml(card) {
    const expanded = ui.expandedCardIds.has(card.id);
    return `
      <div class="card-desc ${expanded ? 'is-expanded' : ''}">${linkify(card.description)}</div>
      <button class="desc-toggle" data-action="toggle-description" data-card-id="${card.id}"
        aria-expanded="${expanded}" hidden>${expanded ? '▲ 閉じる' : '▼ もっと見る'}</button>`;
  }

  // 3行に収まらず切れている説明にだけ「もっと見る」を出す
  function updateDescriptionToggles() {
    root.querySelectorAll('.card-desc').forEach((desc) => {
      const toggle = desc.nextElementSibling;
      const cut = desc.scrollHeight > desc.clientHeight + 1;
      toggle.hidden = !(desc.classList.contains('is-expanded') || cut);
    });
  }

  function addCardHtml(column) {
    const form = ui.addCard;
    if (form?.columnId !== column.id) {
      return `<button class="add-card" data-action="open-add-card" data-column-id="${column.id}">＋ カードを追加</button>`;
    }
    return `
      <div class="inline-form add-card-form">
        <input class="input ${form.error ? 'is-error' : ''}" data-inline="add-card" placeholder="カードのタイトルを入力"
          aria-label="カードのタイトル" value="${escapeHtml(form.value)}" ${form.saving ? 'disabled' : ''} data-autofocus>
        ${form.error ? `<div class="error">${escapeHtml(form.error)}</div>` : ''}
        <div class="row">
          <button class="btn btn-primary" data-action="submit-add-card" ${form.saving ? 'disabled' : ''}>追加</button>
          <button class="icon-btn" data-action="close-add-card" aria-label="閉じる">✕</button>
          <span class="help">Enter で追加 / Esc で閉じる</span>
        </div>
      </div>`;
  }

  function addListHtml() {
    const form = ui.addList;
    if (!form) return '<button class="add-list" data-action="open-add-list">＋ リストを追加</button>';
    return `
      <div class="add-list add-list-form">
        <div class="inline-form">
          <input class="input ${form.error ? 'is-error' : ''}" data-inline="add-list" placeholder="リスト名を入力"
            aria-label="リスト名" value="${escapeHtml(form.value)}" ${form.saving ? 'disabled' : ''} data-autofocus>
          ${form.error ? `<div class="error">${escapeHtml(form.error)}</div>` : ''}
          <div class="row">
            <button class="btn btn-primary" data-action="submit-add-list" ${form.saving ? 'disabled' : ''}>リストを追加</button>
            <button class="icon-btn" data-action="close-add-list" aria-label="閉じる">✕</button>
          </div>
        </div>
      </div>`;
  }

  // ===== ドラッグ＆ドロップ（SortableJS） =====
  // 本番の React では @hello-pangea/dnd を使う。動き（いつ・何をAPIに送るか）は同じ

  function setupSortable() {
    if (!window.Sortable) return;
    const common = {
      animation: 150,
      forceFallback: true, // ドラッグ中の見た目をCSSで変えられるようにする
      fallbackOnBody: true,
      fallbackTolerance: 5, // 5px以上動かしたらドラッグ（クリックと区別する）
    };
    sortables.push(
      window.Sortable.create(root.querySelector('.columns'), {
        ...common,
        draggable: '.list',
        handle: '.list-head', // リストは見出しをつかんで動かす（画面設計書 W-03）
        filter: 'input, .icon-btn',
        preventOnFilter: false,
        ghostClass: 'list-ghost',
        fallbackClass: 'list-dragging',
        onEnd: (e) => setTimeout(() => onColumnDrop(e)),
      }),
    );
    root.querySelectorAll('.cards').forEach((el) => {
      sortables.push(
        window.Sortable.create(el, {
          ...common,
          group: 'cards', // 同じ group のリストどうしでカードを移動できる
          draggable: '.card',
          ghostClass: 'card-ghost',
          fallbackClass: 'card-dragging',
          onEnd: (e) => setTimeout(() => onCardDrop(e)),
        }),
      );
    });
  }

  // ドロップしたら、先に画面の並びを変え、そのあとAPIを呼ぶ。失敗したら元に戻す（API設計書 6.2）
  async function onColumnDrop(e) {
    const from = e.oldDraggableIndex;
    const to = e.newDraggableIndex;
    if (from === to) return;
    const columnId = Number(e.item.dataset.columnId);
    const before = getBoard();
    updateBoard((board) => {
      const [column] = board.columns.splice(from, 1);
      board.columns.splice(to, 0, column);
    });
    try {
      await api.moveColumn(columnId, { position: to });
    } catch (error) {
      setBoard(before);
      handleApiError(error);
    }
  }

  async function onCardDrop(e) {
    const cardId = Number(e.item.dataset.cardId);
    const fromId = Number(e.from.dataset.columnId);
    const toId = Number(e.to.dataset.columnId);
    const position = e.newDraggableIndex;
    if (fromId === toId && e.oldDraggableIndex === position) return;

    const before = getBoard();
    const target = before.columns.find((c) => c.id === toId);
    if (fromId !== toId && target.cards.length >= LIMITS.cardsPerColumn) {
      render(); // SortableJS が動かした見た目を元に戻す
      showToast(MESSAGES.cardLimit);
      return;
    }
    updateBoard((board) => {
      const fromColumn = board.columns.find((c) => c.id === fromId);
      const card = fromColumn.cards.find((c) => c.id === cardId);
      fromColumn.cards = fromColumn.cards.filter((c) => c.id !== cardId);
      board.columns.find((c) => c.id === toId).cards.splice(position, 0, card);
    });
    try {
      await api.moveCard(cardId, { columnId: toId, position });
    } catch (error) {
      setBoard(before);
      handleApiError(error);
    }
  }

  // ===== その場での名前変更（ボード名・リスト名） =====

  // Enter：確定（エラーなら入力欄の下に出して編集を続ける）
  // 入力欄の外をクリック：確定（エラーなら元の名前に戻す）
  async function commitEdit(trigger) {
    const edit = ui.edit;
    if (!edit || edit.saving) return;
    const board = getBoard();
    const isBoard = edit.kind === 'board';
    const currentName = isBoard ? board.name : board.columns.find((c) => c.id === edit.id)?.name;
    const error = isBoard
      ? validateText(edit.value, 'ボード名', MAX_LENGTH.boardName)
      : validateText(edit.value, 'リスト名', MAX_LENGTH.columnName);

    if (error) {
      if (trigger === 'blur') ui.edit = null;
      else edit.error = error;
      render();
      return;
    }
    const name = edit.value.trim();
    if (name === currentName) {
      ui.edit = null;
      render();
      return;
    }

    edit.saving = true;
    render();
    try {
      if (isBoard) {
        const saved = await api.renameBoard({ name });
        updateBoard((b) => {
          b.name = saved.name;
        });
      } else {
        const saved = await api.renameColumn(edit.id, { name });
        updateBoard((b) => {
          b.columns.find((c) => c.id === saved.id).name = saved.name;
        });
      }
      if (ui.edit === edit) ui.edit = null; // 待っている間に別の編集を始めていたら、そちらは消さない
    } catch (err) {
      edit.saving = false;
      if (err.code === 'VALIDATION_ERROR') {
        edit.error = fieldErrors(err).name;
      } else {
        if (ui.edit === edit) ui.edit = null;
        handleApiError(err);
      }
    }
    render();
  }

  // ===== カード・リストの追加 =====

  async function submitAddCard() {
    const form = ui.addCard;
    if (!form || form.saving) return;
    const column = getBoard().columns.find((c) => c.id === form.columnId);
    form.error = validateText(form.value, 'タイトル', MAX_LENGTH.cardTitle);
    if (form.error) {
      render();
      return;
    }
    if (column.cards.length >= LIMITS.cardsPerColumn) {
      showToast(MESSAGES.cardLimit);
      return;
    }
    form.saving = true;
    render();
    try {
      const card = await api.addCard(column.id, { title: form.value });
      updateBoard((b) => {
        b.columns.find((c) => c.id === column.id).cards.push(card);
      });
      // 入力欄は開いたままにして、続けて入力できるようにする（画面設計書 W-03）
      if (ui.addCard === form) ui.addCard = { columnId: column.id, value: '', error: null, saving: false };
    } catch (error) {
      form.saving = false;
      if (error.code === 'VALIDATION_ERROR') form.error = fieldErrors(error).title;
      else handleApiError(error);
    }
    render();
  }

  async function submitAddList() {
    const form = ui.addList;
    if (!form || form.saving) return;
    form.error = validateText(form.value, 'リスト名', MAX_LENGTH.columnName);
    if (form.error) {
      render();
      return;
    }
    if (getBoard().columns.length >= LIMITS.columns) {
      showToast(MESSAGES.columnLimit);
      return;
    }
    form.saving = true;
    render();
    try {
      const column = await api.addColumn({ name: form.value });
      updateBoard((b) => {
        b.columns.push({ ...column, cards: [] });
      });
      if (ui.addList === form) ui.addList = { value: '', error: null, saving: false };
      // 追加したリストが見えるように右端までスクロールする
      requestAnimationFrame(() => {
        const scroller = root.querySelector('.board-scroll');
        if (scroller) scroller.scrollLeft = scroller.scrollWidth;
      });
    } catch (error) {
      form.saving = false;
      if (error.code === 'VALIDATION_ERROR') form.error = fieldErrors(error).name;
      else handleApiError(error);
    }
    render();
  }

  // ===== リストの削除・ログアウト =====

  async function deleteColumn(columnId) {
    const column = getBoard().columns.find((c) => c.id === columnId);
    const count = column.cards.length;
    const ok = await confirmDialog({
      title: 'リストを削除しますか？',
      message:
        count > 0
          ? `「${column.name}」と中のカード${count}件を削除します。この操作は取り消せません。`
          : `「${column.name}」を削除します。この操作は取り消せません。`,
    });
    if (!ok) return;
    try {
      await api.deleteColumn(columnId);
      updateBoard((b) => {
        b.columns = b.columns.filter((c) => c.id !== columnId);
      });
    } catch (error) {
      handleApiError(error);
    }
  }

  async function logout() {
    await api.logout();
    navigate('/login');
  }

  // 一度に開くのは、名前変更・カード追加・リスト追加のどれか1つだけ
  function resetInline() {
    ui.edit = null;
    ui.addCard = null;
    ui.addList = null;
  }

  // ===== イベント =====

  root.addEventListener('click', (e) => {
    if (e.target.closest('a')) return; // 説明文のリンクはそのまま開き、カード詳細は開かない
    const target = e.target.closest('[data-action]');
    const action = target?.dataset.action;
    const columnId = Number(target?.dataset.columnId);

    // メニューの外をクリックしたらメニューを閉じる
    const menuOpen = ui.menuColumnId !== null || ui.userMenuOpen;
    if (menuOpen && !e.target.closest('[data-menu]') && action !== 'toggle-column-menu' && action !== 'toggle-user-menu') {
      ui.menuColumnId = null;
      ui.userMenuOpen = false;
      render();
    }
    if (!action) return;

    switch (action) {
      case 'edit-board-name':
        resetInline();
        ui.edit = { kind: 'board', id: null, value: getBoard().name, error: null, saving: false };
        render();
        break;
      case 'edit-column-name':
        resetInline();
        ui.edit = {
          kind: 'column',
          id: columnId,
          value: getBoard().columns.find((c) => c.id === columnId).name,
          error: null,
          saving: false,
        };
        render();
        break;
      case 'toggle-column-menu':
        ui.menuColumnId = ui.menuColumnId === columnId ? null : columnId;
        ui.userMenuOpen = false;
        render();
        break;
      case 'delete-column':
        ui.menuColumnId = null;
        render();
        deleteColumn(columnId);
        break;
      case 'open-add-card':
        // 上限に達していたら、追加しようとした時点で知らせる（画面設計書 W-03）
        if (getBoard().columns.find((c) => c.id === columnId).cards.length >= LIMITS.cardsPerColumn) {
          showToast(MESSAGES.cardLimit);
          break;
        }
        resetInline();
        ui.addCard = { columnId, value: '', error: null, saving: false };
        render();
        break;
      case 'submit-add-card':
        submitAddCard();
        break;
      case 'close-add-card':
        ui.addCard = null;
        render();
        break;
      case 'open-add-list':
        if (getBoard().columns.length >= LIMITS.columns) {
          showToast(MESSAGES.columnLimit);
          break;
        }
        resetInline();
        ui.addList = { value: '', error: null, saving: false };
        render();
        break;
      case 'submit-add-list':
        submitAddList();
        break;
      case 'close-add-list':
        ui.addList = null;
        render();
        break;
      case 'open-card':
        openCardModal(Number(target.dataset.cardId));
        break;
      case 'toggle-description': {
        // ボタンはカードの中にあるが、いちばん近い data-action はこのボタンなので、カード詳細は開かない
        const cardId = Number(target.dataset.cardId);
        if (ui.expandedCardIds.has(cardId)) ui.expandedCardIds.delete(cardId);
        else ui.expandedCardIds.add(cardId);
        render();
        break;
      }
      case 'open-labels':
        openLabelModal();
        break;
      case 'toggle-user-menu':
        ui.userMenuOpen = !ui.userMenuOpen;
        ui.menuColumnId = null;
        render();
        break;
      case 'logout':
        logout();
        break;
      case 'withdraw':
        ui.userMenuOpen = false;
        render();
        openWithdrawModal();
        break;
    }
  });

  // 入力した文字を ui に覚えておく（描き直しても消えないように）
  root.addEventListener('input', (e) => {
    const kind = e.target.dataset.inline;
    if (kind === 'edit' && ui.edit) ui.edit.value = e.target.value;
    if (kind === 'add-card' && ui.addCard) ui.addCard.value = e.target.value;
    if (kind === 'add-list' && ui.addList) ui.addList.value = e.target.value;
  });

  root.addEventListener('keydown', (e) => {
    const kind = e.target.dataset?.inline;
    // カードは Tab で選んで Enter でも開けるようにする
    if (isEnterKey(e) && e.target.matches('.card')) {
      openCardModal(Number(e.target.dataset.cardId));
      return;
    }
    if (!kind) return;
    if (isEnterKey(e)) {
      e.preventDefault();
      if (kind === 'edit') commitEdit('enter');
      if (kind === 'add-card') submitAddCard();
      if (kind === 'add-list') submitAddList();
    } else if (e.key === 'Escape') {
      // Esc は取り消し（入力欄を閉じる）
      e.stopPropagation();
      resetInline();
      render();
    }
  });

  // 名前の入力欄から外へ出たら確定する（描き直しで入力欄が消えたときは除く）
  root.addEventListener('focusout', (e) => {
    if (!rendering && e.target.dataset?.inline === 'edit') commitEdit('blur');
  });

  // メニューは Esc でも閉じる（モーダルが開いているときはモーダルが優先）
  function onDocumentKeydown(e) {
    if (e.key !== 'Escape' || document.querySelector('#modal-root .overlay')) return;
    if (ui.menuColumnId !== null || ui.userMenuOpen) {
      ui.menuColumnId = null;
      ui.userMenuOpen = false;
      render();
    }
  }
  document.addEventListener('keydown', onDocumentKeydown);

  // ボードのデータが変わったら描き直す（カード詳細・ラベル管理で変えたときも）
  const unsubscribe = subscribe(render);
  render();

  // 別の画面へ移るときの後片付け
  return () => {
    unsubscribe();
    document.removeEventListener('keydown', onDocumentKeydown);
    sortables.forEach((s) => s.destroy());
    setBoard(null);
  };
}

function headerOnlyHtml() {
  return '<header class="app-header"><span class="app-name">TaskBoard</span></header>';
}

function skeletonHtml() {
  const list = (titleWidth, heights) => `
    <div class="list skeleton-list" aria-hidden="true">
      <div class="skel" style="height:16px; width:${titleWidth}; margin:6px 6px 12px"></div>
      ${heights.map((h) => `<div class="skel" style="height:${h}px; margin-bottom:8px"></div>`).join('')}
    </div>`;
  return `
    <main class="board-scroll" aria-busy="true" aria-label="読み込み中">
      <div class="board">${list('50%', [56, 40, 48])}${list('40%', [48, 64])}${list('30%', [40])}</div>
    </main>`;
}

function loadErrorHtml(error) {
  return `
    <div class="center-box">
      <h1 class="center-title">ボードを読み込めませんでした</h1>
      <p class="muted">${escapeHtml(error.detail ?? MESSAGES.network)}</p>
      <button class="btn btn-primary" onclick="location.reload()">再読み込み</button>
    </div>`;
}
