'use strict';

/* ---------- helpers ---------- */
function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (k.startsWith('aria-') && typeof v === 'boolean') { el.setAttribute(k, String(v)); continue; }
    if (v == null || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const kid of kids.flat()) {
    if (kid == null || kid === false) continue;
    el.append(kid.nodeType ? kid : document.createTextNode(String(kid)));
  }
  return el;
}
const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });
const money = (s) => inr.format(Number(s));            // display only; the server does all arithmetic
const when = (iso) => new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
const acctNo = (n) => String(n).replace(/(\d{4})(?=\d)/g, '$1 ');
const label = (s) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ');
const rand = () => Math.random().toString(36).slice(2, 8);

/* ---------- session (kept in sessionStorage so a refresh does not sign you out) ---------- */
const session = { token: null, email: null };
function claims(token) {
  try {
    const p = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(p.padEnd(Math.ceil(p.length / 4) * 4, '=')));
  } catch { return {}; }
}
const isAdmin = () => !!session.token && (claims(session.token).roles || []).includes('ADMIN');
function persist() { try { sessionStorage.setItem('banksim', JSON.stringify(session)); } catch { /* storage blocked */ } }
function restore() {
  try {
    const s = JSON.parse(sessionStorage.getItem('banksim') || 'null');
    if (s && s.token && claims(s.token).exp * 1000 > Date.now()) Object.assign(session, s);
  } catch { /* ignore */ }
}

/* ---------- state ---------- */
const ui = {
  authTab: 'signin', page: 'accounts', accounts: [], selectedId: null, tab: 'passbook',
  hist: { page: 0, type: '', data: null }, note: null, transferKey: crypto.randomUUID(),
  admin: { accounts: null, audit: null, auditType: '', recon: null },
};
const setNote = (kind, text, requestId) => { ui.note = text ? { kind, text, requestId } : null; };

/* ---------- API client with activity log ---------- */
const activity = [];
let dockOpen = false;
function log(method, path, status, t0, requestId) {
  activity.unshift({ at: new Date(), method, path, status, ms: Math.round(performance.now() - t0), requestId });
  if (activity.length > 40) activity.pop();
  renderDock();
}
async function api(method, path, { body, headers, auth = true } = {}) {
  const t0 = performance.now();
  const init = { method, headers: { Accept: 'application/json', ...(headers || {}) } };
  if (body !== undefined) { init.headers['Content-Type'] = 'application/json'; init.body = JSON.stringify(body); }
  if (auth && session.token) init.headers.Authorization = 'Bearer ' + session.token;
  let res;
  try { res = await fetch(path, init); }
  catch {
    log(method, path, 0, t0, null);
    throw { status: 0, detail: 'Cannot reach the server. It may be waking up; wait a minute and try again.' };
  }
  const requestId = res.headers.get('X-Request-Id');
  log(method, path, res.status, t0, requestId);
  const text = await res.text();
  let data = null;
  if (text) { try { data = JSON.parse(text); } catch { /* not json */ } }
  if (!res.ok) {
    if (res.status === 401 && auth && session.token) signOut('Your session expired. Sign in again.');
    let detail = (data && data.detail) || 'The request failed.';
    if (data && data.errors) {
      const list = Array.isArray(data.errors) ? data.errors : Object.entries(data.errors).map(([f, m]) => f + ': ' + m);
      detail = list.map((e) => (typeof e === 'string' ? e : e.message || JSON.stringify(e))).join('; ');
    }
    throw { status: res.status, code: data && data.code, detail, requestId };
  }
  return data;
}

/* ---------- actions ---------- */
function signOut(message) {
  session.token = null; session.email = null; persist();
  Object.assign(ui, { page: 'accounts', accounts: [], selectedId: null, tab: 'passbook' });
  ui.hist = { page: 0, type: '', data: null };
  ui.admin = { accounts: null, audit: null, auditType: '', recon: null };
  setNote(message ? 'err' : null, message);
  render();
}
async function login(email, password) {
  const res = await api('POST', '/api/v1/auth/login', { auth: false, body: { email, password } });
  session.token = res.accessToken; session.email = email; persist();
}
async function enter() {
  ui.page = 'accounts';
  await loadAccounts();
}
async function loadAccounts(keepSelection = true) {
  ui.accounts = await api('GET', '/api/v1/accounts');
  if (!keepSelection || !ui.accounts.some((a) => a.id === ui.selectedId)) ui.selectedId = ui.accounts[0] ? ui.accounts[0].id : null;
  await loadHistory();
}
async function loadHistory() {
  if (ui.selectedId == null) { ui.hist.data = null; return; }
  const q = new URLSearchParams({ page: ui.hist.page, size: 8 });
  if (ui.hist.type) q.set('type', ui.hist.type);
  ui.hist.data = await api('GET', `/api/v1/accounts/${ui.selectedId}/transactions?${q}`);
}
async function guarded(fn, { ok } = {}) {
  try { await fn(); if (ok) setNote('ok', typeof ok === 'function' ? ok() : ok); }
  catch (e) { setNote('err', e.detail || 'Something went wrong.', e.requestId); }
  render();
}

async function startDemo() {
  await guarded(async () => {
    const id = rand();
    const email = `demo.${id}@example.com`;
    const password = `Demo${id}9x!`;
    await api('POST', '/api/v1/auth/register', { auth: false, body: { fullName: 'Demo Customer', email, password } });
    await login(email, password);
    const savings = await api('POST', '/api/v1/accounts', { body: { type: 'SAVINGS' } });
    await api('POST', '/api/v1/accounts', { body: { type: 'CURRENT' } });
    await api('POST', `/api/v1/accounts/${savings.id}/deposit`, { body: { amount: '10000.00' } });
    ui.selectedId = savings.id;
    await enter();
  }, { ok: 'Demo customer created with a savings account (₹10,000.00) and an empty current account. Try a transfer between them.' });
}

/* ---------- views ---------- */
function Header() {
  const nav = h('nav', { class: 'nav', 'aria-label': 'Main' },
    h('button', { 'aria-current': ui.page === 'accounts' ? 'page' : null, onclick: () => { ui.page = 'accounts'; setNote(); render(); } }, 'Accounts'),
    isAdmin() && h('button', { 'aria-current': ui.page === 'admin' ? 'page' : null, onclick: () => openAdmin() }, 'Operations'));
  return h('header', { class: 'bar' },
    h('span', { class: 'brand' }, h('span', { class: 'mark', 'aria-hidden': 'true' }, h('i'), h('i'), h('i')), 'BankSim'),
    session.token && nav,
    session.token && h('div', { class: 'who' },
      h('span', { class: 'muted' }, session.email + (isAdmin() ? ' (administrator)' : '')),
      h('button', { class: 'btn small', onclick: () => signOut() }, 'Sign out')),
    !session.token && h('div', { class: 'who' }, h('a', { href: '/swagger-ui.html' }, 'API documentation')));
}
const Note = () => ui.note && h('div', { class: 'msg ' + ui.note.kind, role: ui.note.kind === 'err' ? 'alert' : 'status' },
  ui.note.text, ui.note.requestId && h('span', { class: 'rid' }, 'Request id ' + ui.note.requestId));

function Welcome() {
  const signin = ui.authTab === 'signin';
  const form = h('form', {
    onsubmit: (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      guarded(async () => {
        if (!signin) await api('POST', '/api/v1/auth/register', { auth: false, body: { fullName: f.get('fullName'), email: f.get('email'), password: f.get('password') } });
        await login(f.get('email'), f.get('password'));
        await enter();
      }, { ok: signin ? null : 'Account created. You are signed in.' });
    },
  },
    !signin && h('div', { class: 'field' }, h('label', { for: 'fn' }, 'Full name'), h('input', { id: 'fn', name: 'fullName', required: true, maxlength: 100, autocomplete: 'name' })),
    h('div', { class: 'field' }, h('label', { for: 'em' }, 'Email'), h('input', { id: 'em', name: 'email', type: 'email', required: true, autocomplete: 'email' })),
    h('div', { class: 'field' }, h('label', { for: 'pw' }, 'Password'),
      h('input', { id: 'pw', name: 'password', type: 'password', required: true, minlength: 8, autocomplete: signin ? 'current-password' : 'new-password' }),
      !signin && h('span', { class: 'hint' }, '8 or more characters, with a letter and a digit.')),
    h('button', { class: 'btn primary', type: 'submit' }, signin ? 'Sign in' : 'Create account'));
  return h('main', { id: 'main', class: 'welcome' },
    h('section', null,
      h('h1', null, 'Money transfers that stay correct, even when everyone sends at once.'),
      h('p', { class: 'lead' }, 'BankSim is a simulated bank on Spring Boot and PostgreSQL. Create a demo customer, move money between accounts, then open the activity panel at the bottom to see each request and its id.'),
      h('ul', { class: 'proof' },
        h('li', null, h('b', null, 'Atomic transfers'), h('span', { class: 'muted' }, 'Both balances change, or neither does.')),
        h('li', null, h('b', null, 'Row locks in a fixed order'), h('span', { class: 'muted' }, 'Opposite transfers cannot deadlock each other.')),
        h('li', null, h('b', null, 'Audit trail'), h('span', { class: 'muted' }, 'Every action is recorded, and the database refuses edits to it.')),
        h('li', null, h('b', null, 'Reconciliation'), h('span', { class: 'muted' }, 'Balances are recomputed from the ledger to catch drift.')))),
    h('section', { 'aria-label': 'Get started' },
      h('div', { class: 'panel' },
        h('div', { class: 'demo' },
          h('button', { class: 'btn primary', onclick: startDemo }, 'Start with a demo customer'),
          h('p', { class: 'hint' }, 'Creates a customer with two accounts and ₹10,000.00 of fictional money. No signup needed.')),
        h('div', { class: 'tabs', role: 'tablist' },
          h('button', { role: 'tab', 'aria-selected': signin, onclick: () => { ui.authTab = 'signin'; setNote(); render(); } }, 'Sign in'),
          h('button', { role: 'tab', 'aria-selected': !signin, onclick: () => { ui.authTab = 'register'; setNote(); render(); } }, 'Create account')),
        Note(), form),
      h('p', { class: 'hint', style: 'margin-top:.75rem' }, 'Fictional data only. Currency is INR. No real money is involved.')));
}

function AccountList() {
  const openForm = h('form', {
    class: 'row',
    onsubmit: (e) => {
      e.preventDefault();
      const type = new FormData(e.target).get('type');
      guarded(async () => {
        const a = await api('POST', '/api/v1/accounts', { body: { type } });
        ui.selectedId = a.id; ui.hist.page = 0; await loadAccounts();
      }, { ok: 'Account opened.' });
    },
  },
    h('label', { class: 'sr', for: 'newtype', style: 'position:absolute;left:-999px' }, 'Account type'),
    h('select', { id: 'newtype', name: 'type', class: 'btn' }, h('option', { value: 'SAVINGS' }, 'Savings'), h('option', { value: 'CURRENT' }, 'Current')),
    h('button', { class: 'btn', type: 'submit' }, 'Open account'));
  return h('div', { class: 'stack' },
    h('h2', { style: 'font-size:1.1rem' }, 'Your accounts'),
    ui.accounts.length === 0 && h('p', { class: 'empty' }, 'No accounts yet. Open one below.'),
    ui.accounts.map((a) => h('button', {
      class: 'acct', 'aria-pressed': a.id === ui.selectedId,
      onclick: () => { ui.selectedId = a.id; ui.hist.page = 0; ui.hist.type = ''; setNote(); guarded(loadHistory); },
    },
      h('span', { class: 'top' }, h('span', null, label(a.type)), a.status !== 'ACTIVE' && h('span', { class: 'tag frozen' }, label(a.status))),
      h('span', { class: 'bal num' }, money(a.balance)),
      h('span', { class: 'muted num' }, acctNo(a.accountNumber)))),
    openForm);
}

function Passbook() {
  const d = ui.hist.data;
  const filter = h('select', { class: 'btn', 'aria-label': 'Filter by type', onchange: (e) => { ui.hist.type = e.target.value; ui.hist.page = 0; guarded(loadHistory); } },
    h('option', { value: '' }, 'All entries'),
    ['DEPOSIT', 'WITHDRAWAL', 'TRANSFER_DEBIT', 'TRANSFER_CREDIT'].map((t) => h('option', { value: t, selected: ui.hist.type === t }, label(t === 'TRANSFER_DEBIT' ? 'Transfer out' : t === 'TRANSFER_CREDIT' ? 'Transfer in' : t))));
  const rows = d && d.content.map((e) => {
    const debit = e.entryType === 'WITHDRAWAL' || e.entryType === 'TRANSFER_DEBIT';
    return h('tr', null,
      h('td', null, when(e.createdAt)),
      h('td', null, e.entryType === 'TRANSFER_DEBIT' ? 'Transfer out' : e.entryType === 'TRANSFER_CREDIT' ? 'Transfer in' : label(e.entryType)),
      h('td', { class: 'r num dr' }, debit ? money(e.amount) : ''),
      h('td', { class: 'r num cr' }, debit ? '' : money(e.amount)),
      h('td', { class: 'r num' }, money(e.balanceAfter)));
  });
  return h('div', null,
    h('div', { class: 'row', style: 'justify-content:space-between;margin-bottom:.6rem' }, h('h3', { style: 'font-size:1.05rem' }, 'Passbook'), filter),
    d && d.content.length === 0
      ? h('p', { class: 'empty' }, 'No entries yet. Make a deposit to see it here.')
      : h('div', { class: 'tablewrap' }, h('table', { class: 'passbook' },
        h('thead', null, h('tr', null, h('th', null, 'Date'), h('th', null, 'Entry'), h('th', { class: 'r' }, 'Debit'), h('th', { class: 'r' }, 'Credit'), h('th', { class: 'r' }, 'Balance'))),
        h('tbody', null, rows))),
    d && d.totalPages > 1 && h('div', { class: 'pager' },
      h('button', { class: 'btn small', disabled: d.page === 0, onclick: () => { ui.hist.page--; guarded(loadHistory); } }, 'Newer'),
      h('span', { class: 'muted' }, `Page ${d.page + 1} of ${d.totalPages}`),
      h('button', { class: 'btn small', disabled: d.page + 1 >= d.totalPages, onclick: () => { ui.hist.page++; guarded(loadHistory); } }, 'Older')));
}

function MoneyForm(kind) {
  const acct = ui.accounts.find((a) => a.id === ui.selectedId);
  const isTransfer = kind === 'transfer';
  const others = ui.accounts.filter((a) => a.id !== ui.selectedId);
  const verb = { deposit: 'Deposit', withdraw: 'Withdraw', transfer: 'Send money' }[kind];
  return h('form', {
    style: 'max-width:26rem',
    onsubmit: (e) => {
      e.preventDefault();
      const f = new FormData(e.target);
      const amount = String(f.get('amount')).trim();
      guarded(async () => {
        if (isTransfer) {
          const r = await api('POST', '/api/v1/transfers', {
            headers: { 'Idempotency-Key': ui.transferKey },
            body: { fromAccountId: acct.id, toAccountNumber: String(f.get('to')).replace(/\s/g, ''), amount, description: f.get('note') || undefined },
          });
          ui.transferKey = crypto.randomUUID();      // a new key only after success, so a retry cannot double-send
          ui.lastTransfer = r;
        } else {
          await api('POST', `/api/v1/accounts/${acct.id}/${kind}`, { body: { amount } });
        }
        ui.hist.page = 0; await loadAccounts();
      }, { ok: () => (isTransfer ? `Sent ${money(amount)}.` : `${kind === 'deposit' ? 'Deposited' : 'Withdrew'} ${money(amount)}.`) });
    },
  },
    isTransfer && h('div', { class: 'field' }, h('label', { for: 'to' }, 'To account number'),
      h('input', { id: 'to', name: 'to', required: true, inputmode: 'numeric', pattern: '[0-9 ]{12,15}', autocomplete: 'off', placeholder: '12 digits' }),
      others.length > 0 && h('div', { class: 'chips' }, h('span', { class: 'hint' }, 'Your other accounts:'),
        others.map((o) => h('button', { type: 'button', class: 'chip num', onclick: () => { document.getElementById('to').value = o.accountNumber; } }, `${label(o.type)} ${acctNo(o.accountNumber)}`)))),
    h('div', { class: 'field' }, h('label', { for: 'amt' }, 'Amount in rupees'),
      h('input', { id: 'amt', name: 'amount', required: true, inputmode: 'decimal', pattern: '[0-9]+(\\.[0-9]{1,2})?', placeholder: '0.00', autocomplete: 'off' })),
    isTransfer && h('div', { class: 'field' }, h('label', { for: 'note' }, 'Note (optional)'), h('input', { id: 'note', name: 'note', maxlength: 140 })),
    isTransfer && h('p', { class: 'hint', style: 'margin-bottom:.9rem' }, 'Sending is retry-safe: this form attaches an idempotency key, so a repeated submit cannot move the money twice.'),
    h('button', { class: 'btn primary', type: 'submit', disabled: acct && acct.status !== 'ACTIVE' }, verb));
}

function AccountDetail() {
  const acct = ui.accounts.find((a) => a.id === ui.selectedId);
  if (!acct) return h('div', { class: 'panel' }, h('p', { class: 'empty' }, 'Open an account to get started.'));
  const tabs = [['passbook', 'Passbook'], ['deposit', 'Deposit'], ['withdraw', 'Withdraw'], ['transfer', 'Send money']];
  return h('div', { class: 'panel' },
    h('div', { class: 'detail-head' },
      h('div', null, h('p', { class: 'muted' }, `${label(acct.type)} account ${acctNo(acct.accountNumber)}`), h('p', { class: 'big num' }, money(acct.balance))),
      acct.status !== 'ACTIVE' && h('span', { class: 'tag frozen' }, 'Frozen: money movement is blocked')),
    h('div', { class: 'tabs', role: 'tablist' }, tabs.map(([k, t]) => h('button', { role: 'tab', 'aria-selected': ui.tab === k, onclick: () => { ui.tab = k; setNote(); render(); } }, t))),
    ui.tab === 'passbook' ? Passbook() : MoneyForm(ui.tab));
}

function AccountsPage() {
  return h('main', { id: 'main', class: 'layout' }, AccountList(), h('div', null, Note(), AccountDetail()));
}

async function openAdmin() {
  ui.page = 'admin'; setNote(); render();
  await guarded(async () => {
    ui.admin.accounts = await api('GET', '/api/v1/admin/accounts?size=50');
    await loadAudit();
  });
}
async function loadAudit() {
  const q = new URLSearchParams({ size: 15 });
  if (ui.admin.auditType) q.set('type', ui.admin.auditType);
  ui.admin.audit = await api('GET', '/api/v1/admin/audit-events?' + q);
}
function ReconPanel() {
  const recon = ui.admin.recon;
  const run = () => guarded(async () => { ui.admin.recon = await api('GET', '/api/v1/admin/reconciliation'); });
  const mismatchRows = recon && recon.mismatches.map((m) => h('tr', null,
    h('td', { class: 'num' }, acctNo(m.accountNumber)),
    h('td', { class: 'r num' }, money(m.balance)),
    h('td', { class: 'r num' }, money(m.ledgerTotal)),
    h('td', { class: 'r num dr' }, money(m.difference))));
  const head = h('div', { class: 'row', style: 'justify-content:space-between;margin-bottom:.75rem' },
    h('h2', { style: 'font-size:1.1rem' }, 'Reconciliation'),
    h('button', { class: 'btn primary small', onclick: run }, 'Run check now'));
  if (!recon) return h('section', { class: 'panel' }, head,
    h('p', { class: 'muted' }, 'Recomputes every balance from the ledger and reports any account that does not match. It also runs nightly.'));
  const status = recon.healthy ? h('span', { class: 'tag ok' }, 'Healthy') : h('span', { class: 'tag fail' }, 'Mismatch found');
  const table = !recon.healthy && h('div', { class: 'tablewrap' }, h('table', null,
    h('thead', null, h('tr', null, ['Account', 'Stored balance', 'Ledger total', 'Difference'].map((t, i) => h('th', { class: i ? 'r' : '' }, t)))),
    h('tbody', null, mismatchRows)));
  return h('section', { class: 'panel' }, head,
    h('p', null, status, ` ${recon.checkedAccounts} accounts checked at ${when(recon.ranAt)}`), table);
}

function AccountsPanel() {
  const a = ui.admin;
  const toggle = (x) => guarded(async () => {
    await api('PATCH', `/api/v1/admin/accounts/${x.id}/status`, { body: { status: x.status === 'ACTIVE' ? 'FROZEN' : 'ACTIVE' } });
    a.accounts = await api('GET', '/api/v1/admin/accounts?size=50');
    await loadAudit();
  }, { ok: x.status === 'ACTIVE' ? 'Account frozen.' : 'Account unfrozen.' });
  const rows = a.accounts && a.accounts.content.map((x) => h('tr', null,
    h('td', { class: 'num' }, acctNo(x.accountNumber)),
    h('td', null, label(x.type)),
    h('td', { class: 'r num' }, money(x.balance)),
    h('td', null, x.status === 'ACTIVE' ? h('span', { class: 'tag ok' }, 'Active') : h('span', { class: 'tag frozen' }, 'Frozen')),
    h('td', null, h('button', { class: 'btn small', onclick: () => toggle(x) }, x.status === 'ACTIVE' ? 'Freeze' : 'Unfreeze'))));
  const table = h('div', { class: 'tablewrap' }, h('table', null,
    h('thead', null, h('tr', null, h('th', null, 'Account'), h('th', null, 'Type'), h('th', { class: 'r' }, 'Balance'), h('th', null, 'Status'), h('th', null, ''))),
    h('tbody', null, rows)));
  return h('section', { class: 'panel' }, h('h2', { style: 'font-size:1.1rem;margin-bottom:.75rem' }, 'Accounts'),
    a.accounts ? table : h('p', { class: 'muted' }, 'Loading...'));
}

function AuditPanel() {
  const a = ui.admin;
  const types = ['REGISTER', 'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'TRANSFER_COMPLETED', 'TRANSFER_REJECTED', 'ACCOUNT_STATUS_CHANGED'];
  const filter = h('select', { class: 'btn', 'aria-label': 'Filter by event', onchange: (e) => { a.auditType = e.target.value; guarded(loadAudit); } },
    h('option', { value: '' }, 'All events'),
    types.map((t) => h('option', { value: t, selected: a.auditType === t }, label(t))));
  const rows = a.audit && a.audit.content.map((e) => h('tr', null,
    h('td', null, when(e.createdAt)),
    h('td', null, label(e.eventType)),
    h('td', null, h('span', { class: 'tag ' + (e.outcome === 'SUCCESS' ? 'ok' : 'fail') }, label(e.outcome))),
    h('td', { class: 'mono' }, e.target || ''),
    h('td', null, e.detail || ''),
    h('td', { class: 'mono' }, e.requestId ? e.requestId.slice(0, 8) : '')));
  let body;
  if (!a.audit) body = h('p', { class: 'muted' }, 'Loading...');
  else if (a.audit.content.length === 0) body = h('p', { class: 'empty' }, 'No events match.');
  else body = h('div', { class: 'tablewrap' }, h('table', null,
    h('thead', null, h('tr', null, ['Time', 'Event', 'Result', 'Target', 'Detail', 'Request id'].map((t) => h('th', null, t)))),
    h('tbody', null, rows)));
  return h('section', { class: 'panel' },
    h('div', { class: 'row', style: 'justify-content:space-between;margin-bottom:.75rem' }, h('h2', { style: 'font-size:1.1rem' }, 'Audit trail'), filter),
    body);
}

function AdminPage() {
  return h('main', { id: 'main', class: 'admin-grid' }, Note(), ReconPanel(), AccountsPanel(), AuditPanel());
}

/* ---------- activity dock: every call this page makes, with the server's request id ---------- */
function renderDock() {
  const dock = document.getElementById('dock');
  if (!dock) return;
  document.body.classList.toggle('dock-open', dockOpen);
  const last = activity[0];
  const head = h('button', { class: 'head', 'aria-expanded': dockOpen, onclick: () => { dockOpen = !dockOpen; renderDock(); } },
    h('strong', null, 'API activity'),
    h('span', null, `${activity.length} calls`),
    last && h('span', { class: 'last num' }, `${last.method} ${last.path.split('?')[0]}  ${last.status || 'no response'}  ${last.ms} ms`));
  const rows = activity.map((x) => h('tr', null,
    h('td', { class: 'num' }, x.at.toLocaleTimeString('en-IN')),
    h('td', { class: 'mono' }, `${x.method} ${x.path.split('?')[0]}`),
    h('td', { class: 'num s' + String(x.status)[0] }, x.status || 'failed'),
    h('td', { class: 'num' }, x.ms + ' ms'),
    h('td', { class: 'mono' }, x.requestId || '')));
  const table = h('table', null,
    h('thead', null, h('tr', null, ['Time', 'Call', 'Status', 'Time taken', 'X-Request-Id'].map((t) => h('th', null, t)))),
    h('tbody', null, rows));
  const body = dockOpen && h('div', { class: 'body' },
    activity.length === 0 ? h('p', { style: 'padding:.5rem 0' }, 'Calls appear here as you use the app.') : table);
  dock.replaceChildren(...[head, body].filter(Boolean));
}

function render() {
  const app = document.getElementById('app');
  const view = !session.token ? Welcome() : ui.page === 'admin' && isAdmin() ? AdminPage() : AccountsPage();
  const active = document.activeElement && document.activeElement.id;
  app.replaceChildren(Header(), view);
  if (active) { const el = document.getElementById(active); if (el) el.focus(); }
}

restore();
render();
renderDock();
if (session.token) guarded(enter);
