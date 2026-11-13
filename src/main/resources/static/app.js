'use strict';
/*
 * Интерфейс модуля «Справочники» (НСИ). Работает поверх /api/v1/references
 * без сборки: один файл, обычный DOM. Роль передаётся демо-заголовком X-Demo-Role.
 */

const API = '/api/v1/references';

const ROLES = [
  { id: 'TECHNICIAN', label: 'Техник' },
  { id: 'REFERENCE_EDITOR', label: 'Редактор НСИ' },
  { id: 'SUPERVISOR', label: 'Супервайзер' },
];
const LABELS = {
  ACTIVE: 'Активен', INACTIVE: 'Неактивен',
  DRAFT: 'Черновик', REVIEW: 'На согласовании', APPROVED: 'Утверждена', ARCHIVED: 'В архиве',
  CREATE: 'Создание', UPDATE: 'Изменение', SUBMIT: 'На согласование', APPROVE: 'Утверждение',
  REJECT: 'Отклонение', ARCHIVE: 'Архивация', DELETE: 'Удаление',
  AIRPORT: 'Аэропорт', PARKING_STAND: 'Стоянка', AIRCRAFT_TYPE: 'Тип ВС', AIRCRAFT: 'Борт',
  OPERATION_TYPE: 'Операция', OPERATION_NORM: 'Норматив', REFERENCE_VERSION: 'Версия шаблонов',
  DIRECTORY_RECORD: 'Шаблон процедуры', PROCEDURE_STAGE: 'Этап процедуры',
  TECHNICIAN: 'Техник', REFERENCE_EDITOR: 'Редактор НСИ', SUPERVISOR: 'Супервайзер',
};
const label = (code) => LABELS[code] || code;

const store = {
  get(key) { try { return localStorage.getItem(key); } catch { return null; } },
  set(key, value) { try { localStorage.setItem(key, value); } catch { /* приватный режим */ } },
};

const state = {
  role: ROLES.some((r) => r.id === store.get('nsi.role')) ? store.get('nsi.role') : 'SUPERVISOR',
  versionId: null, // выбранная версия шаблонов процедур
};
const can = {
  edit: () => state.role !== 'TECHNICIAN',
  supervise: () => state.role === 'SUPERVISOR',
};

/* ---------- DOM и форматирование ---------- */

function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value == null || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key === 'html') el.innerHTML = value;
    else if (key.startsWith('on')) el.addEventListener(key.slice(2), value);
    else el.setAttribute(key, value === true ? '' : value);
  }
  el.append(...kids.flat(Infinity).filter((k) => k != null && k !== false));
  return el;
}

function plural(n, forms) {
  const a = Math.abs(n) % 100, b = a % 10;
  if (a > 10 && a < 20) return forms[2];
  if (b > 1 && b < 5) return forms[1];
  return b === 1 ? forms[0] : forms[2];
}

function fmtDur(seconds) {
  const s = Number(seconds) || 0;
  const parts = [];
  if (s >= 3600) parts.push(Math.floor(s / 3600) + ' ч');
  if (Math.floor((s % 3600) / 60)) parts.push(Math.floor((s % 3600) / 60) + ' мин');
  if (s % 60 || !parts.length) parts.push((s % 60) + ' с');
  return parts.join(' ');
}

function fmtDate(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
}

const badge = (code) => h('span', { class: 'badge ' + code }, label(code));
const codeTag = (text) => h('span', { class: 'code' }, text);
const shortId = (id) => (id ? String(id).slice(0, 8) : '—');

function toast(message, kind) {
  const el = h('div', { class: 'toast' + (kind ? ' ' + kind : '') }, message);
  document.getElementById('toasts').append(el);
  setTimeout(() => el.remove(), kind === 'error' ? 6000 : 3200);
}

/* ---------- API ---------- */

class ApiError extends Error {
  constructor(status, message) { super(message); this.status = status; }
}

function newKey() {
  if (window.crypto && crypto.randomUUID) return crypto.randomUUID();
  return 'ui-' + Date.now() + '-' + Math.random().toString(16).slice(2);
}

async function api(method, path, payload) {
  const url = new URL(API + path, location.origin);
  const init = { method, headers: { Accept: 'application/json', 'X-Demo-Role': state.role } };
  if (method === 'GET') {
    for (const [key, value] of Object.entries(payload || {})) {
      if (value != null && value !== '') url.searchParams.set(key, value);
    }
  } else {
    // Каждое действие пользователя — новый ключ; повтор того же запроса сервер не выполнит дважды.
    init.headers['Idempotency-Key'] = newKey();
    if (payload !== undefined) {
      init.headers['Content-Type'] = 'application/json';
      init.body = JSON.stringify(payload);
    }
  }
  let res;
  try {
    res = await fetch(url, init);
  } catch {
    throw new ApiError(0, 'Сервис недоступен. Проверьте, что backend запущен.');
  }
  const text = await res.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { /* не JSON */ }
  if (!res.ok) throw new ApiError(res.status, (body && (body.detail || body.title)) || 'Ошибка ' + res.status);
  return body;
}

const list = (path, params) => api('GET', path, { limit: 200, ...params });

/* ---------- диалоги ---------- */

function buildField(field, initial) {
  const value = initial ?? field.value ?? '';
  let input;
  if (field.type === 'select') {
    input = h('select', { class: 'select input', required: field.required },
      field.required ? null : h('option', { value: '' }, '—'),
      field.options.map((o) => h('option', { value: o.value }, o.label)));
    if (value !== '') input.value = value; // иначе остаётся первый вариант
  } else if (field.type === 'textarea') {
    input = h('textarea', { class: 'input', required: field.required, placeholder: field.placeholder });
    input.value = value;
  } else if (field.type === 'checkbox') {
    input = h('input', { type: 'checkbox' });
    input.checked = initial ?? field.value ?? false;
    return { input, node: h('label', { class: 'check' }, input, field.label) };
  } else {
    input = h('input', {
      class: 'input' + (field.upper ? ' upper' : ''), type: field.type || 'text', required: field.required,
      placeholder: field.placeholder, min: field.min, maxlength: field.maxlength, pattern: field.pattern,
      autocomplete: 'off', spellcheck: field.upper ? 'false' : null,
    });
    input.value = value;
  }
  const hint = field.hint ? h('small', {}, field.hint) : null;
  return { input, hint, node: h('label', { class: 'field' }, h('span', {}, field.label), input, hint) };
}

function readField(field, input) {
  if (field.type === 'checkbox') return input.checked;
  if (field.type === 'number') return input.value === '' ? null : Number(input.value);
  const text = input.value.trim();
  return field.upper ? text.toUpperCase() : text;
}

/** Модальная форма. onSubmit получает значения полей; его ошибка показывается в форме. */
function formDialog({ title, lead, fields = [], values = {}, submitLabel = 'Сохранить', submitClass = 'primary', onSubmit, setup }) {
  return new Promise((resolve) => {
    const built = {};
    for (const f of fields) built[f.key] = buildField(f, values[f.key]);
    const error = h('p', { class: 'form-error', role: 'alert', hidden: true });
    const submit = h('button', { class: 'btn ' + submitClass, type: 'submit' }, submitLabel);
    const dlg = h('dialog', { class: 'modal' });
    const form = h('form', {},
      h('div', { class: 'dlg-head' }, h('div', {}, h('h2', {}, title), lead ? h('p', {}, lead) : null)),
      h('div', { class: 'dlg-body' }, fields.map((f) => built[f.key].node), error),
      h('div', { class: 'dlg-foot' },
        h('button', { class: 'btn ghost', type: 'button', onclick: () => dlg.close() }, 'Отмена'), submit));
    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      const data = {};
      for (const f of fields) data[f.key] = readField(f, built[f.key].input);
      submit.disabled = true;
      error.hidden = true;
      try {
        const result = await onSubmit(data);
        resolve(result === undefined ? true : result);
        dlg.close();
      } catch (ex) {
        error.textContent = ex.message;
        error.hidden = false;
        submit.disabled = false;
      }
    });
    dlg.addEventListener('close', () => { dlg.remove(); resolve(null); });
    dlg.append(form);
    document.body.append(dlg);
    if (setup) setup(built);
    dlg.showModal();
  });
}

/** Поля, которые пользователь действительно изменил, — тело для PATCH. */
function changed(original, data) {
  const patch = {};
  for (const [key, value] of Object.entries(data)) {
    const before = original[key] ?? (typeof value === 'boolean' ? false : typeof value === 'number' ? null : '');
    if (value !== before) patch[key] = value;
  }
  return patch;
}

async function patchForm({ title, lead, fields, values, path, mapPatch }) {
  return formDialog({
    title, lead, fields, values,
    onSubmit: async (data) => {
      const patch = changed(values, data);
      if (!Object.keys(patch).length) return false;
      await api('PATCH', path, mapPatch ? mapPatch(patch, data) : patch);
      toast('Изменения сохранены');
    },
  });
}

/** Боковая панель с подробностями. render(close, repaint) возвращает узлы. */
function drawer(render, onClose) {
  const dlg = h('dialog', { class: 'drawer' });
  const close = () => dlg.close();
  const paint = async () => {
    try {
      dlg.replaceChildren(...await render(close, paint));
    } catch (ex) {
      dlg.replaceChildren(h('div', { class: 'empty' }, h('strong', {}, 'Не удалось загрузить'), ex.message));
    }
  };
  dlg.addEventListener('click', (event) => { if (event.target === dlg) close(); });
  dlg.addEventListener('close', () => { dlg.remove(); if (onClose) onClose(); });
  dlg.append(skeleton());
  document.body.append(dlg);
  dlg.showModal();
  paint();
}

const skeleton = () => h('div', { class: 'skeleton', 'aria-label': 'Загрузка' }, h('i'), h('i'), h('i'));

function empty(title, text, action) {
  return h('div', { class: 'empty' }, h('strong', {}, title), text, action ? h('div', {}, action) : null);
}

function pageHead({ eyebrow, title, lead, actions }) {
  return h('div', { class: 'page-head' },
    h('div', {}, h('p', { class: 'eyebrow' }, eyebrow), h('h1', {}, title), lead ? h('p', { class: 'lead' }, lead) : null),
    h('div', { class: 'head-actions' }, actions));
}

function table(columns, rows, onRow) {
  return h('div', { class: 'table-wrap' }, h('table', { class: 'grid' },
    h('thead', {}, h('tr', {}, columns.map((c) => h('th', { class: c.cls }, c.label)))),
    h('tbody', {}, rows.map((row) => h('tr', { class: onRow ? 'click' : null, onclick: onRow ? () => onRow(row) : null },
      columns.map((c) => h('td', { class: c.cls }, c.cell(row))))))));
}

const editButton = (onClick, text = 'Изменить') => h('button', {
  class: 'btn sm', type: 'button',
  onclick: (event) => { event.stopPropagation(); onClick(); },
}, text);

const STATUS_FIELD = {
  key: 'status', label: 'Статус', type: 'select', required: true,
  options: [{ value: 'ACTIVE', label: 'Активен' }, { value: 'INACTIVE', label: 'Неактивен' }],
};
const DESCRIPTION_FIELD = { key: 'description', label: 'Описание', type: 'textarea' };

/**
 * Каркас страницы каталога: заголовок, фильтры и область списка.
 * cfg.load(query) возвращает страницу API, cfg.render(items, refresh) — содержимое.
 */
function catalogPage(root, cfg) {
  const query = {};
  const host = h('div', {});
  const count = h('span', { class: 'count' });
  let timer, seq = 0;

  async function refresh() {
    const mine = ++seq;
    try {
      const page = await cfg.load(query);
      if (mine !== seq) return; // пришёл ответ на устаревший запрос
      count.textContent = page.total > page.items.length
        ? `Показано ${page.items.length} из ${page.total}` : `${page.total} ${plural(page.total, cfg.noun)}`;
      host.replaceChildren(page.items.length ? cfg.render(page.items, refresh)
        : h('div', { class: 'card' }, empty('Ничего не найдено',
          Object.values(query).some(Boolean) ? 'Измените условия поиска.' : cfg.emptyText)));
    } catch (ex) {
      if (mine === seq) host.replaceChildren(h('div', { class: 'card' }, empty('Не удалось загрузить', ex.message)));
    }
  }

  const filters = (cfg.filters || []).map((f) => {
    if (f.type === 'select') {
      const select = h('select', { class: 'select input', 'aria-label': f.label, onchange: () => { query[f.key] = select.value; refresh(); } },
        h('option', { value: '' }, f.label), f.options.map((o) => h('option', { value: o.value }, o.label)));
      return select;
    }
    const input = h('input', {
      class: 'input', type: 'search', placeholder: f.label, 'aria-label': f.label,
      oninput: () => { clearTimeout(timer); timer = setTimeout(() => { query[f.key] = input.value.trim(); refresh(); }, 250); },
    });
    return input;
  });

  root.replaceChildren(
    pageHead({ ...cfg.head, actions: cfg.actions ? cfg.actions(refresh) : null }),
    h('div', { class: 'toolbar' }, filters, count),
    host);
  host.append(h('div', { class: 'card' }, skeleton()));
  return refresh();
}

/* ---------- сводка ---------- */

async function overviewPage(root) {
  const total = (path) => api('GET', path, { limit: 1 }).then((p) => p.total);
  const [types, planes, ops, airports, templates] = await Promise.all([
    total('/aircraft-types'), total('/aircraft'), total('/operation-types'), total('/airports'),
    list('/procedure-templates'),
  ]);
  const activeVersionId = templates.items[0] && templates.items[0].reference_version_id;
  const [snapshot, versions, audit] = await Promise.all([
    activeVersionId ? api('GET', '/procedure-templates/versions/' + activeVersionId) : null,
    can.edit() ? list('/procedure-templates/versions') : null,
    can.supervise() ? api('GET', '/audit', { limit: 6 }) : null,
  ]);

  const tile = (id, name, value, tone) => h('a', { class: 'tile', href: '#/' + id },
    h('span', { class: 'tile-icon', style: `--tone:var(--${tone})`, html: icon(id) }),
    h('div', {}, h('strong', {}, value), h('span', {}, name)));
  const pending = versions && versions.items.find((v) => v.status === 'DRAFT' || v.status === 'REVIEW');

  const active = h('section', { class: 'card' },
    h('div', { class: 'card-head' }, h('h2', {}, 'Действующие шаблоны процедур'),
      snapshot ? h('span', { class: 'chip' }, 'версия', h('b', {}, 'v' + snapshot.version_number)) : null),
    snapshot && snapshot.records.length
      ? h('div', { class: 'card-body stack' },
        snapshot.records.map((r) => h('div', {},
          h('div', { class: 'rec-head', style: 'padding:0 0 10px' }, h('div', {},
            h('h3', {}, r.name), h('div', { class: 'meta' }, codeTag(r.code),
              h('span', { class: 'chip' }, 'тип ВС', h('b', {}, r.data.aircraft_type_code))))),
          timeline(r.stages))),
        h('dl', { class: 'kv' },
          h('dt', {}, 'Опубликована'), h('dd', {}, fmtDate(snapshot.published_at)),
          h('dt', {}, 'SHA-256 снимка'), h('dd', { class: 'mono' }, snapshot.checksum)))
      : empty('Нет утверждённой версии', 'Техники не получат шаблон, пока версия не утверждена.'));

  const workflow = versions && h('section', { class: 'card' },
    h('div', { class: 'card-head' }, h('h2', {}, 'Согласование')),
    h('div', { class: 'card-body stack' },
      pending
        ? [h('div', { class: 'ver-top' }, h('b', { class: 'mono' }, 'v' + pending.version_number), badge(pending.status)),
          h('p', { class: 'dim' }, pending.change_reason || 'Причина изменения не указана'),
          h('a', { class: 'btn dark', href: '#/procedures', onclick: () => { state.versionId = pending.id; } },
            pending.status === 'REVIEW' && can.supervise() ? 'Рассмотреть версию' : 'Открыть версию')]
        : [h('p', { class: 'dim' }, 'Открытых черновиков нет. Все изменения опубликованы.'),
          h('a', { class: 'btn', href: '#/procedures' }, 'К версиям шаблонов')]));

  const feed = audit && h('section', { class: 'card' },
    h('div', { class: 'card-head' }, h('h2', {}, 'Последние изменения'), h('a', { href: '#/audit' }, 'Весь журнал')),
    audit.items.length
      ? h('ul', { class: 'feed' }, audit.items.map((a) => h('li', {},
        badge(a.operation), h('span', {}, label(a.entity_type), ' · ', h('span', { class: 'dim' }, a.actor_id)),
        h('time', {}, fmtDate(a.created_at)))))
      : empty('Журнал пуст', 'Изменения появятся здесь после первого действия.'));

  const side = [workflow, feed].filter(Boolean);
  root.replaceChildren(
    h('section', { class: 'hero' },
      h('span', { class: 'plane', html: `<svg viewBox="0 0 24 24" fill="#fff" aria-hidden="true"><path d="${ICONS.types}"/></svg>` }),
      h('p', { class: 'kicker' }, 'Модуль НСИ'),
      h('h1', {}, 'Справочники подготовки ВС к вылету'),
      h('p', { class: 'lead' }, 'Единый источник типов ВС, бортов, операций и шаблонов процедур для цифрового чек-листа.')),
    h('div', { class: 'tiles' },
      tile('types', 'Типы ВС', types, 't1'), tile('aircraft', 'Борты', planes, 't2'),
      tile('operations', 'Виды операций', ops, 't3'), tile('airports', 'Аэропорты', airports, 't4')),
    side.length ? h('div', { class: 'cols' }, active, h('div', { class: 'stack' }, side)) : active);
}

/** Линия этапов: ширина сегмента пропорциональна нормативу. */
function timeline(stages, changes) {
  if (!stages.length) return h('p', { class: 'note' }, 'Этапов пока нет — версию с пустым шаблоном нельзя отправить на согласование.');
  const total = stages.reduce((sum, s) => sum + s.expected_duration_seconds, 0);
  return h('div', {},
    h('div', { class: 'timeline', role: 'img', 'aria-label': `Этапы процедуры, всего ${fmtDur(total)}` },
      stages.map((s) => h('div', {
        class: 'seg' + (changes && changes.get(s.id) ? ' changed' : '') + (s.mandatory ? '' : ' optional'),
        style: `flex-grow:${s.expected_duration_seconds}`,
        title: `${s.sequence_number}. ${s.operation_code} — ${fmtDur(s.expected_duration_seconds)}${s.mandatory ? '' : ' (необязательный)'}`,
      }, h('b', {}, s.operation_code), h('span', {}, fmtDur(s.expected_duration_seconds))))),
    h('div', { class: 'timeline-foot' },
      h('span', {}, `${stages.length} ${plural(stages.length, ['этап', 'этапа', 'этапов'])} · ширина по нормативу`),
      h('span', {}, 'Всего ', h('b', {}, fmtDur(total)))));
}

/* ---------- типы ВС ---------- */

async function typesPage(root) {
  const all = await list('/aircraft-types');
  const categories = [...new Set(all.items.map((t) => t.category))].sort();
  const fields = [
    { key: 'code', label: 'Код типа', required: true, upper: true, maxlength: 20, placeholder: 'Например, A321', hint: 'Латиница, цифры, дефис; 2–20 символов' },
    { key: 'name', label: 'Название', required: true },
    { key: 'category', label: 'Категория', required: true, upper: true, placeholder: 'Например, NARROW_BODY' },
    DESCRIPTION_FIELD,
  ];
  return catalogPage(root, {
    head: { eyebrow: 'Каталог', title: 'Типы воздушных судов', lead: 'К типу привязываются борты и базовые нормативы операций.' },
    noun: ['тип', 'типа', 'типов'], emptyText: 'Типы ВС ещё не заведены.',
    filters: [
      { key: 'search', label: 'Поиск по коду или названию' },
      { key: 'category', type: 'select', label: 'Все категории', options: categories.map((c) => ({ value: c, label: c })) },
    ],
    load: (query) => list('/aircraft-types', query),
    actions: (refresh) => can.supervise() && h('button', {
      class: 'btn primary', type: 'button',
      onclick: async () => {
        if (await formDialog({
          title: 'Новый тип ВС', fields, submitLabel: 'Создать',
          onSubmit: (data) => api('POST', '/aircraft-types', data).then(() => toast('Тип ВС создан')),
        })) refresh();
      },
    }, 'Добавить тип'),
    render: (items, refresh) => h('div', { class: 'card' }, table([
      { label: 'Код', cell: (t) => codeTag(t.code) },
      { label: 'Название', cell: (t) => [h('div', {}, t.name), t.description ? h('div', { class: 'sub' }, t.description) : null] },
      { label: 'Категория', cell: (t) => h('span', { class: 'mono' }, t.category) },
      { label: 'Статус', cell: (t) => badge(t.status) },
      { label: '', cls: 'act', cell: (t) => h('span', { class: 'more' }, 'Нормативы и борты →') },
    ], items, (type) => typeDrawer(type.id, fields, refresh))),
  });
}

function typeDrawer(typeId, fields, onClose) {
  drawer(async (close, repaint) => {
    const types = await list('/aircraft-types');
    const type = types.items.find((t) => t.id === typeId);
    const [norms, planes, ops] = await Promise.all([
      list(`/aircraft-types/${typeId}/operation-norms`),
      list('/aircraft', { aircraft_type_code: type.code }),
      can.edit() ? list('/operation-types', { status: 'ACTIVE' }) : null,
    ]);
    const free = ops ? ops.items.filter((o) => !norms.items.some((n) => n.operation_type_id === o.id)) : [];
    const secondsField = { key: 'duration_seconds', label: 'Базовое время, секунд', type: 'number', min: 1, required: true, hint: '1200 с = 20 мин' };

    const addNorm = async () => {
      if (await formDialog({
        title: 'Привязать операцию', lead: `Норматив для типа ${type.code}`, submitLabel: 'Привязать',
        fields: [
          { key: 'operation_type_id', label: 'Операция', type: 'select', required: true, options: free.map((o) => ({ value: o.id, label: `${o.code} — ${o.name}` })) },
          secondsField, DESCRIPTION_FIELD,
        ],
        onSubmit: (data) => api('POST', `/aircraft-types/${typeId}/operation-norms`, data).then(() => toast('Норматив добавлен')),
      })) repaint();
    };
    const editNorm = async (norm) => {
      if (await patchForm({
        title: `Норматив ${norm.operation_code}`, lead: 'Опубликованные этапы процедур сохранят прежние значения.',
        fields: [secondsField, DESCRIPTION_FIELD], values: norm, path: '/operation-norms/' + norm.id,
      })) repaint();
    };
    const editType = async () => {
      if (await patchForm({
        title: `Тип ${type.code}`, fields: [...fields.filter((f) => f.key !== 'code'), STATUS_FIELD],
        values: type, path: '/aircraft-types/' + typeId,
      })) repaint();
    };

    return [
      h('div', { class: 'dlg-head' },
        h('div', {}, h('p', { class: 'eyebrow' }, 'Тип ВС'), h('h2', {}, codeTag(type.code), ' ', type.name),
          type.description ? h('p', {}, type.description) : null),
        h('button', { class: 'btn ghost sm', type: 'button', onclick: close }, 'Закрыть')),
      h('div', { class: 'dlg-body' },
        h('dl', { class: 'kv' },
          h('dt', {}, 'Категория'), h('dd', { class: 'mono' }, type.category),
          h('dt', {}, 'Статус'), h('dd', {}, badge(type.status))),
        can.edit() ? h('div', {}, editButton(editType, 'Изменить тип')) : null),
      h('div', { class: 'section', style: 'margin-top:12px' },
        h('div', { class: 'section-head' }, h('h3', {}, 'Базовые нормативы операций'),
          can.edit() && free.length ? editButton(addNorm, 'Привязать операцию') : null),
        norms.items.length ? table([
          { label: 'Операция', cell: (n) => [codeTag(n.operation_code), ' ', n.operation_name] },
          { label: 'Время', cls: 'r num', cell: (n) => fmtDur(n.duration_seconds) },
          can.edit() ? { label: '', cls: 'act', cell: (n) => editButton(() => editNorm(n)) } : null,
        ].filter(Boolean), norms.items)
          : h('p', { class: 'dim' }, 'Операции не привязаны. В шаблон процедуры можно включить только привязанные операции.')),
      h('div', { class: 'section' },
        h('div', { class: 'section-head' }, h('h3', {}, 'Борты этого типа')),
        planes.items.length ? table([
          { label: 'Бортовой номер', cell: (a) => codeTag(a.tail_number) },
          { label: 'Статус', cls: 'r', cell: (a) => badge(a.status) },
        ], planes.items) : h('p', { class: 'dim' }, 'Бортов этого типа нет.')),
    ];
  }, onClose);
}

/* ---------- борты ---------- */

async function aircraftPage(root) {
  const types = (await list('/aircraft-types')).items;
  const typeOptions = (onlyActive) => types.filter((t) => !onlyActive || t.status === 'ACTIVE')
    .map((t) => ({ value: t.id, label: `${t.code} — ${t.name}` }));
  const tail = { key: 'tail_number', label: 'Бортовой номер', required: true, upper: true, maxlength: 30, placeholder: 'Например, RA-73001' };
  const typeField = (onlyActive) => ({ key: 'aircraft_type_id', label: 'Тип ВС', type: 'select', required: true, options: typeOptions(onlyActive) });

  return catalogPage(root, {
    head: { eyebrow: 'Каталог', title: 'Борты', lead: 'Конкретные воздушные суда; каждый борт относится к одному типу.' },
    noun: ['борт', 'борта', 'бортов'], emptyText: 'Борты ещё не заведены.',
    filters: [
      { key: 'tail_number', label: 'Поиск по бортовому номеру' },
      { key: 'aircraft_type_code', type: 'select', label: 'Все типы ВС', options: types.map((t) => ({ value: t.code, label: t.code })) },
    ],
    load: (query) => list('/aircraft', query),
    actions: (refresh) => can.edit() && h('button', {
      class: 'btn primary', type: 'button',
      onclick: async () => {
        if (await formDialog({
          title: 'Новый борт', fields: [tail, typeField(true), DESCRIPTION_FIELD], submitLabel: 'Создать',
          onSubmit: (data) => api('POST', '/aircraft', data).then(() => toast('Борт создан')),
        })) refresh();
      },
    }, 'Добавить борт'),
    render: (items, refresh) => h('div', { class: 'card' }, table([
      { label: 'Бортовой номер', cell: (a) => codeTag(a.tail_number) },
      { label: 'Тип ВС', cell: (a) => [h('span', { class: 'mono' }, a.aircraft_type_code), ' ', h('span', { class: 'dim' }, a.aircraft_type_name)] },
      { label: 'Описание', cell: (a) => a.description || h('span', { class: 'dim' }, '—') },
      { label: 'Статус', cell: (a) => badge(a.status) },
      can.edit() ? {
        label: '', cls: 'act', cell: (a) => editButton(async () => {
          if (await patchForm({
            title: `Борт ${a.tail_number}`, fields: [tail, typeField(false), DESCRIPTION_FIELD, STATUS_FIELD],
            values: a, path: '/aircraft/' + a.id,
          })) refresh();
        }),
      } : null,
    ].filter(Boolean), items)),
  });
}

/* ---------- операции ---------- */

function operationsPage(root) {
  const name = { key: 'name', label: 'Название', required: true };
  return catalogPage(root, {
    head: { eyebrow: 'Каталог', title: 'Виды операций', lead: 'Из операций собираются этапы шаблонов; время задаётся нормативом для типа ВС.' },
    noun: ['операция', 'операции', 'операций'], emptyText: 'Операции ещё не заведены.',
    filters: [
      { key: 'search', label: 'Поиск по коду или названию' },
      { key: 'status', type: 'select', label: 'Любой статус', options: STATUS_FIELD.options },
    ],
    load: (query) => list('/operation-types', query),
    actions: (refresh) => can.supervise() && h('button', {
      class: 'btn primary', type: 'button',
      onclick: async () => {
        if (await formDialog({
          title: 'Новая операция', submitLabel: 'Создать',
          fields: [{ key: 'code', label: 'Код', required: true, upper: true, maxlength: 30, placeholder: 'Например, DEICING' }, name, DESCRIPTION_FIELD],
          onSubmit: (data) => api('POST', '/operation-types', data).then(() => toast('Операция создана')),
        })) refresh();
      },
    }, 'Добавить операцию'),
    render: (items, refresh) => h('div', { class: 'card' }, table([
      { label: 'Код', cell: (o) => codeTag(o.code) },
      { label: 'Название', cell: (o) => o.name },
      { label: 'Описание', cell: (o) => o.description || h('span', { class: 'dim' }, '—') },
      { label: 'Статус', cell: (o) => badge(o.status) },
      can.edit() ? {
        label: '', cls: 'act', cell: (o) => editButton(async () => {
          if (await patchForm({
            title: `Операция ${o.code}`, fields: [name, DESCRIPTION_FIELD, STATUS_FIELD],
            values: o, path: '/operation-types/' + o.id,
          })) refresh();
        }),
      } : null,
    ].filter(Boolean), items)),
  });
}

/* ---------- аэропорты и стоянки ---------- */

function airportsPage(root) {
  const base = [
    { key: 'name', label: 'Название', required: true },
    { key: 'city', label: 'Город', required: true },
    { key: 'country', label: 'Страна', required: true },
    DESCRIPTION_FIELD,
  ];
  return catalogPage(root, {
    head: { eyebrow: 'Каталог', title: 'Аэропорты', lead: 'Аэропорт и его стоянки. Откройте карточку, чтобы увидеть стоянки.' },
    noun: ['аэропорт', 'аэропорта', 'аэропортов'], emptyText: 'Аэропорты ещё не заведены.',
    filters: [{ key: 'search', label: 'Поиск по коду, названию или городу' }],
    load: (query) => list('/airports', query),
    actions: (refresh) => can.supervise() && h('button', {
      class: 'btn primary', type: 'button',
      onclick: async () => {
        if (await formDialog({
          title: 'Новый аэропорт', submitLabel: 'Создать',
          fields: [{ key: 'iata_code', label: 'Код IATA', required: true, upper: true, maxlength: 3, pattern: '[A-Za-z]{3}', placeholder: 'Три буквы, например LED' }, ...base],
          onSubmit: (data) => api('POST', '/airports', data).then(() => toast('Аэропорт создан')),
        })) refresh();
      },
    }, 'Добавить аэропорт'),
    render: (items, refresh) => h('div', { class: 'apt-grid' }, items.map((a) => h('button', {
      class: 'apt ' + a.status, type: 'button', onclick: () => airportDrawer(a.id, base, refresh),
    },
      h('div', { class: 'apt-top' }, h('span', { class: 'apt-code' }, a.iata_code), badge(a.status)),
      h('div', {}, h('div', { class: 'apt-name' }, a.name), h('div', { class: 'sub' }, `${a.city}, ${a.country}`))))),
  });
}

function airportDrawer(airportId, base, onClose) {
  drawer(async (close, repaint) => {
    const [airports, stands] = await Promise.all([list('/airports'), list(`/airports/${airportId}/parking-stands`)]);
    const airport = airports.items.find((a) => a.id === airportId);
    const code = { key: 'code', label: 'Код стоянки', required: true, upper: true, maxlength: 30, placeholder: 'Например, A12' };

    const addStand = async () => {
      if (await formDialog({
        title: 'Новая стоянка', lead: `Аэропорт ${airport.iata_code}`, submitLabel: 'Добавить', fields: [code, DESCRIPTION_FIELD],
        onSubmit: (data) => api('POST', `/airports/${airportId}/parking-stands`, data).then(() => toast('Стоянка добавлена')),
      })) repaint();
    };
    const editStand = async (stand) => {
      if (await patchForm({
        title: `Стоянка ${stand.code}`, fields: [code, DESCRIPTION_FIELD, STATUS_FIELD], values: stand, path: '/parking-stands/' + stand.id,
      })) repaint();
    };
    const editAirport = async () => {
      if (await patchForm({
        title: `Аэропорт ${airport.iata_code}`, fields: [...base, STATUS_FIELD], values: airport, path: '/airports/' + airportId,
      })) repaint();
    };

    return [
      h('div', { class: 'dlg-head' },
        h('div', {}, h('p', { class: 'eyebrow' }, 'Аэропорт'), h('h2', {}, codeTag(airport.iata_code), ' ', airport.name),
          h('p', {}, `${airport.city}, ${airport.country}`)),
        h('button', { class: 'btn ghost sm', type: 'button', onclick: close }, 'Закрыть')),
      h('div', { class: 'dlg-body' },
        h('dl', { class: 'kv' },
          h('dt', {}, 'Статус'), h('dd', {}, badge(airport.status)),
          airport.description ? [h('dt', {}, 'Описание'), h('dd', {}, airport.description)] : null),
        can.edit() ? h('div', {}, editButton(editAirport, 'Изменить аэропорт')) : null),
      h('div', { class: 'section', style: 'margin-top:12px' },
        h('div', { class: 'section-head' }, h('h3', {}, `Стоянки · ${stands.total}`),
          can.edit() && airport.status === 'ACTIVE' ? editButton(addStand, 'Добавить стоянку') : null),
        stands.items.length ? table([
          { label: 'Код', cell: (s) => codeTag(s.code) },
          { label: 'Описание', cell: (s) => s.description || h('span', { class: 'dim' }, '—') },
          { label: 'Статус', cell: (s) => badge(s.status) },
          can.edit() ? { label: '', cls: 'act', cell: (s) => editButton(() => editStand(s)) } : null,
        ].filter(Boolean), stands.items) : h('p', { class: 'dim' }, 'Стоянок пока нет.')),
    ];
  }, onClose);
}

/* ---------- шаблоны процедур: версии и согласование ---------- */

const STAGE_KEYS = ['sequence_number', 'operation_code', 'description', 'expected_duration_seconds', 'required_role', 'mandatory'];

/** Чем этапы версии отличаются от базовой: id этапа → 'new' либо прежний этап. */
function stageChanges(records, baseRecords) {
  const before = new Map();
  for (const r of baseRecords) for (const s of r.stages) before.set(s.logical_id, s);
  const changes = new Map();
  for (const r of records) {
    for (const s of r.stages) {
      const old = before.get(s.logical_id);
      if (!old) changes.set(s.id, 'new');
      else if (STAGE_KEYS.some((k) => old[k] !== s[k])) changes.set(s.id, old);
    }
  }
  return changes;
}

async function proceduresPage(root) {
  const head = (actions) => pageHead({
    eyebrow: 'Процедуры', title: 'Шаблоны процедур',
    lead: 'Шаблон и его этапы версионируются вместе. Техник видит только утверждённую версию.', actions,
  });

  // Техник не имеет доступа к истории версий — показываем опубликованный снимок.
  if (!can.edit()) {
    const published = await list('/procedure-templates');
    const versionId = published.items[0] && published.items[0].reference_version_id;
    if (!versionId) return root.replaceChildren(head(), h('div', { class: 'card' }, empty('Нет утверждённой версии', 'Шаблоны появятся после утверждения супервайзером.')));
    const snap = await api('GET', '/procedure-templates/versions/' + versionId);
    return root.replaceChildren(head(), h('div', { class: 'stack' },
      h('div', { class: 'card ver-head' }, h('div', { class: 'ver-title' },
        h('h2', {}, 'Версия v' + snap.version_number), badge(snap.status),
        h('span', { class: 'dim' }, 'опубликована ' + fmtDate(snap.published_at)))),
      snap.records.map((r) => recordCard(r, {}))));
  }

  const [versions, ops, types] = await Promise.all([
    list('/procedure-templates/versions'), list('/operation-types'), list('/aircraft-types'),
  ]);
  const open = versions.items.find((v) => v.status === 'DRAFT' || v.status === 'REVIEW');
  const current = versions.items.find((v) => v.id === state.versionId)
    || open || versions.items.find((v) => v.status === 'APPROVED') || versions.items[0];
  const reload = () => proceduresPage(root).catch((ex) => toast(ex.message, 'error'));

  const newDraft = !open && h('button', {
    class: 'btn primary', type: 'button',
    onclick: async () => {
      const created = await formDialog({
        title: 'Новый черновик', lead: 'Копия утверждённой версии. Пока черновик не утверждён, техники его не видят.',
        submitLabel: 'Создать черновик',
        fields: [{ key: 'changeReason', label: 'Причина изменения', type: 'textarea', required: true, placeholder: 'Что и почему меняется' }],
        onSubmit: (data) => api('POST', '/procedure-templates/versions', data),
      });
      if (created) { state.versionId = created.id; toast('Черновик v' + created.version_number + ' создан'); reload(); }
    },
  }, 'Новый черновик');

  if (!current) return root.replaceChildren(head(newDraft), h('div', { class: 'card' }, empty('Версий пока нет', 'Создайте первый черновик шаблонов.')));
  state.versionId = current.id;

  const inProgress = current.status === 'DRAFT' || current.status === 'REVIEW';
  const [records, base] = await Promise.all([
    list(`/procedure-templates/versions/${current.id}/records`),
    inProgress && current.base_version_id ? list(`/procedure-templates/versions/${current.base_version_id}/records`) : null,
  ]);
  const changes = base ? stageChanges(records.items, base.items) : new Map();
  const editable = current.status === 'DRAFT';
  const ctx = { version: current, editable, changes, reload, types: types.items, opNames: new Map(ops.items.map((o) => [o.code, o.name])) };

  const act = async (verb, done, form) => {
    const ok = await formDialog({
      ...form, onSubmit: (data) => api('POST', `/procedure-templates/versions/${current.id}/${verb}`, form.fields ? data : undefined),
    });
    if (ok) { toast(done); reload(); }
  };
  const actions = [];
  if (current.status === 'DRAFT') {
    actions.push(h('button', {
      class: 'btn primary', type: 'button',
      onclick: () => act('submit', 'Версия отправлена на согласование', {
        title: 'Отправить на согласование?', submitLabel: 'Отправить',
        lead: 'Черновик станет недоступен для правок, пока супервайзер не примет решение.',
      }),
    }, 'Отправить на согласование'));
  }
  if (current.status === 'REVIEW' && can.supervise()) {
    actions.push(
      h('button', {
        class: 'btn danger', type: 'button',
        onclick: () => act('reject', 'Версия возвращена в черновик', {
          title: 'Отклонить версию', submitLabel: 'Отклонить', submitClass: 'dark',
          lead: 'Версия вернётся в черновик, причина попадёт в журнал аудита.',
          fields: [{ key: 'reason', label: 'Причина', type: 'textarea', required: true }],
        }),
      }, 'Отклонить'),
      h('button', {
        class: 'btn primary', type: 'button',
        onclick: () => act('approve', 'Версия утверждена и опубликована', {
          title: `Утвердить v${current.version_number}?`, submitLabel: 'Утвердить',
          lead: 'Версия станет действующей для новых чек-листов, предыдущая уйдёт в архив.',
        }),
      }, 'Утвердить'));
  }

  const order = ['DRAFT', 'REVIEW', 'APPROVED', 'ARCHIVED'];
  const at = order.indexOf(current.status);
  const changedCount = changes.size;

  const detail = h('div', { class: 'stack' },
    h('div', { class: 'card ver-head' },
      h('div', { class: 'ver-title' }, h('h2', {}, 'Версия v' + current.version_number), badge(current.status),
        h('div', { class: 'head-actions' }, actions)),
      h('ol', { class: 'steps' }, order.map((s, i) => h('li', { class: i < at ? 'done' : i === at ? 'now' : null }, label(s)))),
      h('dl', { class: 'kv' },
        current.change_reason ? [h('dt', {}, 'Причина изменения'), h('dd', {}, current.change_reason)] : null,
        h('dt', {}, 'Автор'), h('dd', { class: 'mono' }, current.created_by || '—'),
        current.approved_by ? [h('dt', {}, 'Утвердил'), h('dd', {}, h('span', { class: 'mono' }, current.approved_by), ' · ', fmtDate(current.published_at))] : null),
      current.status === 'REVIEW' && !can.supervise() ? h('p', { class: 'note' }, 'Ожидает решения супервайзера. Правки недоступны до утверждения или отклонения.') : null,
      inProgress && base ? h('p', { class: changedCount ? 'note warn' : 'note' }, changedCount
        ? `${changedCount} ${plural(changedCount, ['этап отличается', 'этапа отличаются', 'этапов отличаются'])} от действующей версии — отмечено ниже.`
        : 'Этапы пока совпадают с действующей версией.') : null),
    records.items.map((r) => recordCard(r, ctx)),
    !records.items.length ? h('div', { class: 'card' }, empty('В версии нет шаблонов', editable ? 'Добавьте первый шаблон процедуры.' : '')) : null,
    editable ? h('div', {}, h('button', { class: 'btn', type: 'button', onclick: () => recordForm(null, ctx) }, 'Добавить шаблон процедуры')) : null);

  const side = h('div', { class: 'versions' }, versions.items.map((v) => h('button', {
    class: 'ver', type: 'button', 'aria-current': v.id === current.id ? 'true' : null,
    onclick: () => { state.versionId = v.id; reload(); },
  },
    h('div', { class: 'ver-top' }, h('b', {}, 'v' + v.version_number), badge(v.status)),
    v.change_reason ? h('p', {}, v.change_reason) : null,
    h('p', { class: 'dim' }, (v.created_by || '—') + ' · ' + fmtDate(v.published_at || v.created_at)))));

  root.replaceChildren(head(newDraft), h('div', { class: 'split' }, side, detail));
}

function recordCard(record, ctx) {
  const changes = ctx.changes || new Map();
  const opName = (code) => (ctx.opNames && ctx.opNames.get(code)) || '';
  const columns = [
    { label: '№', cls: 'num', cell: (s) => s.sequence_number },
    { label: 'Операция', cls: 'nowrap', cell: (s) => [codeTag(s.operation_code), ' ', opName(s.operation_code)] },
    { label: 'Описание', cell: (s) => s.description || h('span', { class: 'dim' }, '—') },
    {
      label: 'Норматив', cls: 'num', cell: (s) => {
        const was = changes.get(s.id);
        const mark = was === 'new' ? h('span', { class: 'mark' }, 'новый')
          : was && was.expected_duration_seconds !== s.expected_duration_seconds ? h('span', { class: 'mark' }, 'было ' + fmtDur(was.expected_duration_seconds))
            : was ? h('span', { class: 'mark' }, 'изменён') : null;
        return [fmtDur(s.expected_duration_seconds), mark ? ' ' : null, mark];
      },
    },
    { label: 'Роль', cell: (s) => label(s.required_role) },
    { label: 'Обязательный', cell: (s) => (s.mandatory ? 'Да' : h('span', { class: 'dim' }, 'Нет')) },
    ctx.editable ? { label: '', cls: 'act', cell: (s) => editButton(() => stageForm(record, s, ctx)) } : null,
  ].filter(Boolean);

  return h('section', { class: 'card' },
    h('div', { class: 'rec-head' },
      h('div', {}, h('h3', {}, record.name),
        h('div', { class: 'meta' }, codeTag(record.code),
          h('span', { class: 'chip' }, 'тип ВС', h('b', {}, record.data.aircraft_type_code)),
          h('span', { class: 'chip' }, 'процедура', h('b', {}, record.data.procedure_code)))),
      ctx.editable ? h('div', { class: 'head-actions' },
        editButton(() => recordForm(record, ctx)), editButton(() => stageForm(record, null, ctx), 'Добавить этап')) : null),
    h('div', { class: 'rec-line' }, timeline(record.stages, changes)),
    record.stages.length ? table(columns, record.stages) : null);
}

function recordForm(record, ctx) {
  const data = (record && record.data) || {};
  const values = record ? { ...record, aircraft_type_code: data.aircraft_type_code, procedure_code: data.procedure_code } : { procedure_code: 'PRE_DEPARTURE' };
  const base = `/procedure-templates/versions/${ctx.version.id}/records`;
  return formDialog({
    title: record ? 'Шаблон ' + record.code : 'Новый шаблон процедуры', values, submitLabel: record ? 'Сохранить' : 'Создать',
    fields: [
      { key: 'code', label: 'Код шаблона', required: true, upper: true, placeholder: 'Например, PRE_DEPARTURE_B738' },
      { key: 'name', label: 'Название', required: true },
      { key: 'aircraft_type_code', label: 'Тип ВС', type: 'select', required: true, options: ctx.types.filter((t) => t.status === 'ACTIVE' || t.code === data.aircraft_type_code).map((t) => ({ value: t.code, label: `${t.code} — ${t.name}` })) },
      { key: 'procedure_code', label: 'Код процедуры', required: true, upper: true },
      DESCRIPTION_FIELD,
    ],
    onSubmit: async (form) => {
      const body = {
        code: form.code, name: form.name, description: form.description,
        data: { ...data, aircraft_type_code: form.aircraft_type_code, procedure_code: form.procedure_code },
      };
      if (record) await api('PATCH', `${base}/${record.id}`, body); else await api('POST', base, body);
      toast(record ? 'Шаблон сохранён' : 'Шаблон создан');
    },
  }).then((ok) => { if (ok) ctx.reload(); });
}

async function stageForm(record, stage, ctx) {
  // В шаблон можно включить только операции, привязанные нормативом к типу ВС.
  const type = ctx.types.find((t) => t.code === record.data.aircraft_type_code);
  const norms = type ? (await list(`/aircraft-types/${type.id}/operation-norms`)).items : [];
  const options = norms.map((n) => ({ value: n.operation_code, label: `${n.operation_code} — ${n.operation_name} · базовое ${fmtDur(n.duration_seconds)}` }));
  // Сначала операции, которых в шаблоне ещё нет, — новый этап не дублирует существующий.
  const used = new Set(record.stages.map((s) => s.operation_code));
  options.sort((a, b) => used.has(a.value) - used.has(b.value));
  if (stage && !options.some((o) => o.value === stage.operation_code)) options.unshift({ value: stage.operation_code, label: stage.operation_code });
  const next = record.stages.reduce((max, s) => Math.max(max, s.sequence_number), 0) + 1;
  const values = stage ? {
    sequenceNumber: stage.sequence_number, operationCode: stage.operation_code, description: stage.description,
    expectedDurationSeconds: stage.expected_duration_seconds, requiredRole: stage.required_role, mandatory: stage.mandatory,
  } : { sequenceNumber: next, requiredRole: 'TECHNICIAN', mandatory: true };
  const base = `/procedure-templates/versions/${ctx.version.id}/records/${record.id}/stages`;

  const ok = await formDialog({
    title: stage ? `Этап ${stage.sequence_number} · ${stage.operation_code}` : 'Новый этап', lead: record.name, values,
    submitLabel: stage ? 'Сохранить' : 'Добавить',
    fields: [
      { key: 'sequenceNumber', label: 'Порядковый номер', type: 'number', min: 1, required: true },
      { key: 'operationCode', label: 'Операция', type: 'select', required: true, options, hint: options.length ? null : `К типу ${record.data.aircraft_type_code} не привязано ни одной операции — добавьте норматив в разделе «Типы ВС».` },
      { key: 'expectedDurationSeconds', label: 'Норматив, секунд', type: 'number', min: 1, required: true, hint: ' ' },
      DESCRIPTION_FIELD,
      { key: 'requiredRole', label: 'Кто выполняет', type: 'select', required: true, options: ROLES.map((r) => ({ value: r.id, label: r.label })) },
      { key: 'mandatory', label: 'Обязательный этап', type: 'checkbox' },
    ],
    setup: (built) => {
      const seconds = built.expectedDurationSeconds, operation = built.operationCode.input;
      const show = () => { seconds.hint.textContent = seconds.input.value ? '= ' + fmtDur(seconds.input.value) : 'Подставится базовое время операции'; };
      const fill = () => {
        const norm = norms.find((n) => n.operation_code === operation.value);
        if (norm && !stage) seconds.input.value = norm.duration_seconds;
        show();
      };
      operation.addEventListener('change', fill);
      seconds.input.addEventListener('input', show);
      if (!stage) fill(); else show();
    },
    onSubmit: async (form) => {
      if (stage) {
        const patch = changed(values, form);
        if (!Object.keys(patch).length) return false;
        await api('PATCH', `${base}/${stage.id}`, patch);
      } else {
        await api('POST', base, form);
      }
      toast(stage ? 'Этап сохранён' : 'Этап добавлен');
    },
  });
  if (ok) ctx.reload();
}

/* ---------- аудит ---------- */

function auditPage(root) {
  const show = (value) => (value == null ? '—' : typeof value === 'object' ? JSON.stringify(value) : String(value));
  const detail = (a) => {
    const before = a.old_value || {}, after = a.new_value || {};
    const keys = [...new Set([...Object.keys(before), ...Object.keys(after)])]
      .filter((k) => k !== 'note' && JSON.stringify(before[k]) !== JSON.stringify(after[k]));
    return h('div', { class: 'stack' },
      h('dl', { class: 'kv' },
        h('dt', {}, 'Объект'), h('dd', { class: 'mono' }, a.entity_id),
        a.reason ? [h('dt', {}, 'Причина'), h('dd', {}, a.reason)] : null,
        h('dt', {}, 'Запрос'), h('dd', { class: 'mono' }, a.request_id || '—')),
      keys.length ? h('table', { class: 'diff' },
        h('thead', {}, h('tr', {}, h('th', {}, 'Поле'), h('th', {}, 'Было'), h('th', {}, 'Стало'))),
        h('tbody', {}, keys.map((k) => h('tr', {}, h('th', {}, k),
          h('td', { class: a.old_value ? 'was' : 'dim' }, a.old_value ? show(before[k]) : '—'),
          h('td', { class: 'now' }, show(after[k]))))))
        : h('p', { class: 'dim' }, 'Значения полей не изменились.'));
  };

  return catalogPage(root, {
    head: { eyebrow: 'Процедуры', title: 'Журнал аудита', lead: 'Кто, что и когда изменил. Нажмите на строку, чтобы увидеть прежние и новые значения.' },
    noun: ['событие', 'события', 'событий'], emptyText: 'Событий пока нет.',
    filters: [
      { key: 'entity_type', type: 'select', label: 'Все объекты', options: ['REFERENCE_VERSION', 'DIRECTORY_RECORD', 'PROCEDURE_STAGE', 'AIRCRAFT_TYPE', 'AIRCRAFT', 'OPERATION_TYPE', 'OPERATION_NORM', 'AIRPORT', 'PARKING_STAND'].map((v) => ({ value: v, label: label(v) })) },
      { key: 'user_id', label: 'Пользователь, например demo-supervisor' },
    ],
    load: (query) => list('/audit', query),
    render: (items) => {
      const wrap = table([
        { label: 'Когда', cell: (a) => h('span', { class: 'num' }, fmtDate(a.created_at)) },
        { label: 'Действие', cell: (a) => badge(a.operation) },
        { label: 'Объект', cell: (a) => [label(a.entity_type), ' ', h('span', { class: 'mono dim' }, shortId(a.entity_id))] },
        { label: 'Пользователь', cell: (a) => h('span', { class: 'mono' }, a.actor_id) },
        { label: 'Причина', cell: (a) => a.reason || h('span', { class: 'dim' }, '—') },
      ], items);
      // Раскрытие строки: подробности вставляются следующей строкой таблицы.
      [...wrap.querySelectorAll('tbody tr')].forEach((row, i) => {
        row.classList.add('click');
        row.addEventListener('click', () => {
          const next = row.nextElementSibling;
          if (next && next.classList.contains('detail')) return next.remove();
          row.after(h('tr', { class: 'detail' }, h('td', { colspan: 5 }, detail(items[i]))));
        });
      });
      return h('div', { class: 'card' }, wrap);
    },
  });
}

/* ---------- навигация ---------- */

const ICONS = {
  overview: 'M4 4h7v7H4zM13 4h7v4h-7zM13 11h7v9h-7zM4 14h7v6H4z',
  types: 'M10.5 3.5a1.5 1.5 0 0 1 3 0V9l7.5 4.5v2L13.5 13v5l2.5 1.8V21l-4-1-4 1v-1.2l2.5-1.8v-5L3 15.5v-2L10.5 9z',
  aircraft: 'M3 12V4h8l10 10-8 8zM7.5 8.5h.01',
  operations: 'M10 6h10M10 12h10M10 18h10M4 6l1.2 1.2L7.5 5M4 12l1.2 1.2L7.5 11M4 18l1.2 1.2L7.5 17',
  airports: 'M12 21s7-6.2 7-11.5a7 7 0 0 0-14 0C5 14.8 12 21 12 21zM12 12a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z',
  procedures: 'M4 6h7v4H4zM11 14h9v4h-9zM7.5 10v6H11',
  lock: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 0 1 7 0v3',
  audit: 'M12 7v5l3 2M3.6 13.5A8.5 8.5 0 1 0 6 6L3.5 8.5M3.5 4v4.5H8',
};
const ROUTES = [
  { id: 'overview', label: 'Сводка', page: overviewPage },
  { group: 'Каталоги' },
  { id: 'types', label: 'Типы ВС', page: typesPage },
  { id: 'aircraft', label: 'Борты', page: aircraftPage },
  { id: 'operations', label: 'Операции', page: operationsPage },
  { id: 'airports', label: 'Аэропорты', page: airportsPage },
  { group: 'Процедуры' },
  { id: 'procedures', label: 'Шаблоны процедур', page: proceduresPage },
  { id: 'audit', label: 'Журнал аудита', page: auditPage, allowed: can.supervise },
];

function icon(id) {
  return `<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="${ICONS[id]}"/></svg>`;
}

function currentRoute() {
  const id = location.hash.replace(/^#\/?/, '') || 'overview';
  return ROUTES.find((r) => r.id === id) || ROUTES[0];
}

function renderNav() {
  const active = currentRoute();
  document.getElementById('nav').replaceChildren(...ROUTES.map((r) => (r.group
    ? h('div', { class: 'nav-group' }, r.group)
    : h('a', { href: '#/' + r.id, 'aria-current': r === active ? 'page' : null },
      h('span', { html: icon(r.id), style: 'display:inline-flex' }),
      r.label,
      r.allowed && !r.allowed() ? h('span', { class: 'lock', title: 'Доступно только супервайзеру', html: icon('lock') }) : null))));
}

function renderRoles() {
  document.getElementById('roles').replaceChildren(...ROLES.map((r) => h('button', {
    type: 'button', role: 'radio', 'aria-checked': String(r.id === state.role),
    onclick: () => { state.role = r.id; store.set('nsi.role', r.id); renderRoles(); render(); },
  }, r.label)));
}

let renderSeq = 0;
async function render() {
  const mine = ++renderSeq;
  const route = currentRoute();
  const view = document.getElementById('view');
  renderNav();
  document.title = route.label + ' — НСИ';
  // Страница рисуется в отдельный узел: если пользователь ушёл, результат отбрасывается.
  const root = h('div', {});
  view.replaceChildren(skeleton());
  try {
    if (route.allowed && !route.allowed()) throw new ApiError(403, '');
    await route.page(root);
  } catch (ex) {
    root.replaceChildren(ex.status === 403
      ? h('div', { class: 'card' }, empty('Раздел недоступен', `Для роли «${label(state.role)}» этот раздел закрыт. Смените роль вверху страницы.`))
      : h('div', { class: 'card' }, empty('Не удалось загрузить', ex.message,
        h('button', { class: 'btn', type: 'button', onclick: render }, 'Повторить'))));
  }
  if (mine === renderSeq) view.replaceChildren(root);
}

document.getElementById('theme').addEventListener('click', () => {
  const rootEl = document.documentElement;
  const dark = rootEl.dataset.theme ? rootEl.dataset.theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches;
  rootEl.dataset.theme = dark ? 'light' : 'dark';
  store.set('nsi.theme', rootEl.dataset.theme);
});
window.addEventListener('hashchange', render);
renderRoles();
render();
