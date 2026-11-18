import {CONFIG} from './config.js';
import {LiveAuction} from './live.js';

const app = document.querySelector('#app');
const nav = document.querySelector('#nav');
let token = sessionStorage.getItem('accessToken');
let user = null;
let dispose = () => {};
let routeId = 0;
const money = value => `${new Intl.NumberFormat('en-HU').format(value)} HUF`;
const el = (tag, attrs = {}, ...children) => {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key.startsWith('on')) node.addEventListener(key.slice(2), value);
    else if (key === 'class') node.className = value;
    else if (value !== null && value !== undefined) node.setAttribute(key, value);
  }
  for (const child of children.flat()) if (child !== null && child !== undefined) node.append(child instanceof Node ? child : document.createTextNode(String(child)));
  return node;
};
const button = (label, action, className = 'button') => el('button', {type: 'button', class: className, onclick: action}, label);
const link = (label, path, className = '') => el('a', {href: `#${path}`, class: className}, label);
const empty = message => el('div', {class: 'empty'}, message);
function flash(node, text, ok = false) { node.textContent = text; node.className = ok ? 'notice success' : 'notice error'; }
function field(label, name, type = 'text', attrs = {}) {
  const input = type === 'textarea' ? el('textarea', {name, id: name, required: true, ...attrs}) : el('input', {name, id: name, type, required: true, ...attrs});
  return el('label', {}, el('span', {}, label), input);
}
function image(url, title) {
  const img = el('img', {src: url, alt: title, loading: 'lazy', referrerpolicy: 'no-referrer'});
  img.addEventListener('error', () => { img.removeAttribute('src'); img.alt = 'Image unavailable'; }, {once: true});
  return img;
}
async function api(service, path, options = {}) {
  const headers = {'Content-Type': 'application/json', ...options.headers};
  if (token) headers.Authorization = `Bearer ${token}`;
  let response;
  try { response = await fetch(CONFIG[service] + path, {...options, headers, signal: AbortSignal.timeout(10000)}); }
  catch { const e = new Error('Cannot reach the service. Your request may have completed. Retry with the same details.'); e.network = true; throw e; }
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    if (response.status === 401 && token) { token = null; user = null; sessionStorage.removeItem('accessToken'); renderNav(); }
    const error = new Error(body.detail || `Request failed (${response.status}).`); error.status = response.status; throw error;
  }
  return body;
}
function renderNav() {
  nav.replaceChildren(link('Explore', '/'), link('Sell an item', '/sell', 'nav-sell'));
  if (user) nav.append(link('My auctions', '/mine'), el('span', {class: 'user-name'}, user.displayName), button('Log out', () => {
    sessionStorage.removeItem('accessToken'); token = null; user = null; renderNav(); location.hash = '#/'; route();
  }, 'text-button'));
  else nav.append(link('Log in', '/login'), link('Join SecondRound', '/register', 'button small'));
}
function requireLogin() {
  if (user) return true;
  app.replaceChildren(empty('Please log in to continue.'), link('Log in', '/login', 'button'));
  return false;
}
function pager(page, count, size, change) {
  const previous = button('← Previous', () => change(page - 1), 'secondary');
  const next = button('Next →', () => change(page + 1), 'secondary');
  previous.disabled = page === 0;
  next.disabled = (page + 1) * size >= count;
  return el('div', {class: 'pager'}, previous, el('span', {}, `Page ${page + 1} · ${count} results`), next);
}

function remaining(end, offset = 0) {
  const seconds = Math.ceil((Date.parse(end) - Date.now() - offset) / 1000);
  if (seconds <= 0) return 'Awaiting final result';
  if (seconds >= 86400) return `${Math.floor(seconds / 86400)}d ${Math.floor(seconds % 86400 / 3600)}h left`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor(seconds % 3600 / 60)}m ${seconds % 60}s left`;
}
function card(a) {
  const state = a.status === 'ENDED' ? (a.leadingBidder ? 'SOLD' : 'NO BIDS') : a.status;
  const anchor = link('', `/auction/${a.id}`, 'card');
  anchor.setAttribute('aria-label', `${a.name || 'Auction'} · ${money(a.currentPrice)} · ${a.status}`);
  anchor.append(el('article', {},
    el('div', {class: 'card-image'}, a.imageUrl ? image(a.imageUrl, a.name) : el('span', {}, 'Preparing your listing…'), el('span', {class: `badge ${a.status.toLowerCase()}`}, state)),
    el('div', {class: 'card-body'}, el('h3', {}, a.name || (a.status === 'FAILED' ? 'Auction not created' : 'Pending auction')), el('p', {class: 'muted card-clock', ...(a.status === 'ACTIVE' ? {'data-end':a.endsAt} : {})}, a.status === 'ACTIVE' ? remaining(a.endsAt) : a.status === 'PREPARING' ? 'Recovering item connection automatically' : a.status === 'FAILED' ? a.failure : 'Auction ended'),
      el('div', {class: 'card-price'}, el('strong', {}, money(a.currentPrice)), el('span', {}, 'View auction ↗')))));
  return anchor;
}
async function home(page = 0, query = '') {
  dispose();
  const mine = ++routeId;
  const search = el('input', {type: 'search', name: 'q', placeholder: 'Search for your next great find', value: query, maxlength: 120, 'aria-label': 'Search auctions'});
  const searchForm = el('form', {class: 'search', onsubmit: e => {e.preventDefault(); home(0, search.value);}}, search, el('button', {}, 'Search'));
  const content = el('section', {class: 'grid'}, empty('Loading auctions…'));
  const pagination = el('div');
  app.replaceChildren(el('section', {class: 'hero'}, el('div', {}, el('p', {class: 'eyebrow'}, 'PRE-LOVED. READY FOR WHAT’S NEXT.'), el('h1', {}, 'A little history.\nA new beginning.'), el('p', {}, 'Discover second-hand treasures, place your bid and give good things another round.'), link('Start selling ↗', '/sell', 'button')), el('div', {class: 'hero-note'}, el('span', {}, '↻'), el('strong', {}, 'Find it. Bid for it. Love it.'), el('p', {}, 'Live auctions · Real people · Whole HUF'))),
    el('section', {class: 'section-title'}, el('div', {}, el('p', {class: 'eyebrow'}, 'THE NEXT ROUND'), el('h2', {}, 'Discover auctions')), searchForm), content, pagination);
  let loading = false;
  async function load() {
    if (loading || mine !== routeId) return;
    loading = true;
    try {
      const result = await api('auction', `/api/auctions?page=${page}&size=12&q=${encodeURIComponent(query)}`);
      if (mine !== routeId) return;
      content.replaceChildren(...(result.content.length ? result.content.map(card) : [empty('No auctions yet. Be the first to list something worth another round.')]));
      pagination.replaceChildren(pager(page, result.totalElements, 12, p => home(p, query)));
    } catch(e) { if (mine === routeId) content.replaceChildren(empty(e.message), button('Try again', load)); }
    finally { loading = false; }
  }
  const clock = setInterval(() => {
    if (mine === routeId) for (const node of content.querySelectorAll('[data-end]')) node.textContent = remaining(node.dataset.end);
  }, 1000);
  const refresh = setInterval(load, 10000);
  dispose = () => { clearInterval(clock); clearInterval(refresh); };
  await load();
}

function authPage(register) {
  const error = el('p', {role: 'status'});
  const form = el('form', {class: 'panel auth'});
  const submit = el('button', {type: 'submit'}, register ? 'Create account' : 'Log in');
  form.append(el('p', {class: 'eyebrow'}, register ? 'WELCOME TO THE NEXT ROUND' : 'GOOD TO SEE YOU AGAIN'), el('h1', {}, register ? 'Make yourself at home.' : 'Welcome back.'),
    ...(register ? [field('Display name', 'displayName', 'text', {maxlength: 80, autocomplete: 'nickname'})] : []),
    field('Email address', 'email', 'email', {maxlength: 254, autocomplete: 'email'}),
    field('Password', 'password', 'password', {minlength: register ? 10 : 1, maxlength: 72, autocomplete: register ? 'new-password' : 'current-password'}),
    ...(register ? [el('p', {class: 'muted'}, 'Use at least 10 characters, at most 72 UTF-8 bytes.')] : []),
    error, submit, el('p', {}, register ? 'Already a member? ' : 'New around here? ', link(register ? 'Log in' : 'Create an account', register ? '/login' : '/register')),
    el('small', {class: 'muted'}, 'Login lasts one hour. This tab stores your token in sessionStorage; closing the tab ends the browser session.'));
  form.addEventListener('submit', async e => {
    e.preventDefault(); submit.disabled = true; flash(error, 'Please wait…', true);
    const values = Object.fromEntries(new FormData(form));
    values.email = values.email.trim().toLowerCase();
    try {
      if (register) await api('user', '/api/auth/register', {method: 'POST', body: JSON.stringify(values)});
      const result = await api('user', '/api/auth/login', {method: 'POST', body: JSON.stringify({email: values.email, password: values.password})});
      token = result.accessToken; user = result.user; sessionStorage.setItem('accessToken', token); renderNav(); location.hash = '#/';
    } catch (e) { flash(error, e.message); } finally { submit.disabled = false; }
  });
  app.replaceChildren(form);
}
function sellPage() {
  if (!requireLogin()) return;
  const draftKey = `publish:${user.id}`;
  let draft;
  try { draft = JSON.parse(sessionStorage.getItem(draftKey)); } catch {}
  const msg = el('p', {role: 'status'});
  const itemFields = el('fieldset', {}, el('legend', {}, '1. Tell us about your item'), field('Item name', 'name', 'text', {maxlength: 120}),
    field('Description & condition', 'description', 'textarea', {maxlength: 4000, rows: 5}), field('Image URL (HTTP or HTTPS)', 'imageUrl', 'url', {maxlength: 2000, placeholder: 'https://…'}));
  const auctionFields = el('fieldset', {}, el('legend', {}, '2. Set up the auction'), el('div', {class: 'two-cols'}, field('Starting price · HUF', 'startingPrice', 'number', {min: 1, max: 1000000000000, step: 1}), field('Minimum raise · HUF', 'minimumIncrement', 'number', {min: 1, max: 1000000000000, step: 1})), field('Ends at (your local time)', 'endsAt', 'datetime-local'));
  const submit = el('button', {type: 'submit'}, draft ? 'Retry / recover this publication' : 'Publish auction ↗');
  const form = el('form', {class: 'panel sell'}, el('p', {class: 'eyebrow'}, 'PASS ON SOMETHING GOOD'), el('h1', {}, 'Give it another round.'), el('p', {class: 'muted'}, 'Your auction starts immediately. Item details are frozen once the auction is reserved.'), itemFields, auctionFields, msg, submit);
  function showDraft() {
    if (!draft) return;
    for (const [name, value] of Object.entries(draft.fields)) if (form.elements[name]) form.elements[name].value = value;
    for (const input of itemFields.elements) input.readOnly = true;
    if (draft.end) for (const input of auctionFields.elements) input.readOnly = true;
    flash(msg, draft.itemId ? `Item saved (${draft.itemId}). Retry uses the same item and auction request; no duplicate auction will be created.` : 'A publication is pending. Retry uses the same details and request keys.', true);
  }
  showDraft();
  form.addEventListener('submit', async e => {
    e.preventDefault(); submit.disabled = true;
    try {
      if (!draft?.end) {
        const fields = Object.fromEntries(new FormData(form));
        const end = new Date(fields.endsAt).toISOString();
        if (Date.parse(end) <= Date.now()) throw new Error('Choose an end time in the future.');
        draft = {...draft, fields, itemKey: draft?.itemKey || crypto.randomUUID(), auctionKey: draft?.auctionKey || crypto.randomUUID(), end};
        sessionStorage.setItem(draftKey, JSON.stringify(draft)); showDraft();
      }
      if (!draft.itemId) {
        flash(msg, 'Saving item…', true);
        const item = await api('item', '/api/items', {method: 'POST', headers: {'Idempotency-Key': draft.itemKey}, body: JSON.stringify({name: draft.fields.name, description: draft.fields.description, imageUrl: draft.fields.imageUrl})});
        draft.itemId = item.id; sessionStorage.setItem(draftKey, JSON.stringify(draft));
      }
      flash(msg, 'Item saved. Reserving and freezing auction…', true);
      const auction = await api('auction', '/api/auctions', {method: 'POST', headers: {'Idempotency-Key': draft.auctionKey}, body: JSON.stringify({itemId: draft.itemId, startingPrice: Number(draft.fields.startingPrice), minimumIncrement: Number(draft.fields.minimumIncrement), endsAt: draft.end})});
      if (auction.status === 'FAILED') throw new Error(`Item is still saved; auction failed: ${auction.failure}. Check My auctions for the recovery record.`);
      sessionStorage.removeItem(draftKey); location.hash = `#/auction/${auction.id}`;
    } catch (e) { flash(msg, e.message + ' Your draft is saved in this tab.'); } finally { submit.disabled = false; }
  });
  form.append(button('Discard local draft', () => { sessionStorage.removeItem(draftKey); sellPage(); }, 'text-button'), el('small', {class: 'muted'}, 'Discard only clears this tab’s draft. Any saved item or reserved auction remains visible in My auctions / My items.'));
  app.replaceChildren(form);
}
async function detailPage(id) {
  const generation = ++routeId;
  const shell = el('section', {class: 'detail'});
  const media = el('div', {class: 'detail-media'});
  const title = el('h1'); const desc = el('p', {class: 'description'});
  const price = el('strong', {class: 'big-price', id: 'current-price'});
  const minimum = el('p', {class: 'muted'}); const state = el('span', {class: 'badge'});
  const countdown = el('p', {class: 'countdown'}); const result = el('p', {class: 'result', role: 'status'});
  const connection = el('span', {class: 'connection', role: 'status'}, 'Connecting…');
  const amount = el('input', {type: 'number', id: 'bid-amount', name: 'amount', min: 1, step: 1, max: 1000000000000, required: true, 'aria-label': 'Your bid in HUF'});
  const feedback = el('p', {role: 'status'}); const bidButton = el('button', {type: 'submit'}, 'Place bid ↗');
  const bidForm = el('form', {class: 'bid-form'}, el('label', {}, 'Your bid · HUF', amount), bidButton, feedback);
  const history = el('ol', {class: 'bid-history'}); const historyTitle = el('h2', {}, 'Bid history');
  const moreHistory = button('Load older bids', () => loadHistory());
  const panel = el('div', {class: 'detail-panel'}, el('div', {class: 'detail-top'}, state, connection), title, el('p', {class: 'eyebrow'}, 'CURRENT PRICE'), price, minimum, countdown, result, bidForm, desc);
  shell.append(media, panel);
  app.replaceChildren(link('← Back to explore', '/'), shell, el('section', {class: 'panel history-panel'}, historyTitle, history, moreHistory));
  let current = null, live, timer, bidPage = 0, offset = 0;
  async function loadHistory() {
    if (!current) return;
    moreHistory.disabled = true;
    try {
      const data = await api('auction', `/api/auctions/${id}/bids?page=${bidPage + 1}&size=50`);
      if (generation !== routeId) return;
      history.append(...data.content.map(bidRow)); bidPage++;
      moreHistory.hidden = (bidPage + 1) * 50 >= data.totalElements;
    } catch(e) { flash(feedback, e.message); } finally { moreHistory.disabled = false; }
  }
  function bidRow(b) {
    return el('li', {}, el('span', {}, b.bidderId === user?.id ? 'You' : `Bidder ${b.bidderId.slice(0, 8)}`), el('strong', {}, money(b.amount)), el('time', {datetime: b.createdAt}, new Date(b.createdAt).toLocaleString()));
  }
  function apply(data) {
    if (generation !== routeId) return;
    const a = data.auction;
    const first = !current;
    current = a; offset = Date.parse(a.serverTime) - Date.now();
    if (first) { media.replaceChildren(a.imageUrl ? image(a.imageUrl, a.name) : empty('Preparing item…')); }
    if (a.imageUrl && !media.querySelector('img')) media.replaceChildren(image(a.imageUrl, a.name));
    title.textContent = a.name || (a.status === 'FAILED' ? 'Auction not created' : 'Preparing auction…'); desc.textContent = a.description || (a.status === 'FAILED' ? 'This creation was rejected. The item and any other auction have been preserved. Check My items before starting another publication.' : 'We are connecting to the item service. This page will recover automatically.');
    price.textContent = money(a.currentPrice); minimum.textContent = `Next minimum ${money(a.nextMinimum)} · Raise ${money(a.minimumIncrement)}`;
    state.textContent = a.status; state.className = `badge ${a.status.toLowerCase()}`;
    if (a.status === 'ENDED') result.textContent = a.leadingBidder ? (a.leadingBidder === user?.id ? 'You won this auction!' : `Sold to bidder ${a.leadingBidder.slice(0, 8)} for ${money(a.currentPrice)}.`) : 'No bids. This item did not sell.';
    else if (a.status === 'FAILED') result.textContent = a.failure;
    else result.textContent = a.sellerId === user?.id ? 'This is your auction. You cannot bid on it.' : '';
    amount.min = a.nextMinimum;
    if (first || (!amount.value && document.activeElement !== amount)) amount.value = a.nextMinimum;
    bidButton.disabled = !user || user.id === a.sellerId || a.status !== 'ACTIVE';
    if (!user) feedback.replaceChildren(link('Log in to place a bid', '/login'));
    historyTitle.textContent = `Bid history (${data.bidCount})`;
    history.replaceChildren(...(data.bids.length ? data.bids.map(bidRow) : [el('li', {}, 'No bids yet. The first one could be yours.')]));
    bidPage = 0; moreHistory.hidden = data.bidCount <= 50;
    tick();
  }
  function tick() {
    if (!current) return;
    countdown.textContent = current.status === 'ACTIVE' ? remaining(current.endsAt, offset) : current.status === 'PREPARING' ? 'Recovering creation · please wait' : 'Auction closed';
  }
  bidForm.addEventListener('submit', async e => {
    e.preventDefault(); if (!user || !current) return;
    const value = Number(amount.value);
    if (!Number.isSafeInteger(value)) return flash(feedback, 'Enter a whole HUF amount.');
    const storageKey = `bid:${user.id}:${id}`;
    let pending;
    try { pending = JSON.parse(sessionStorage.getItem(storageKey)); } catch {}
    if (!pending || pending.amount !== value) pending = {amount: value, key: crypto.randomUUID()};
    sessionStorage.setItem(storageKey, JSON.stringify(pending)); bidButton.disabled = true;
    try {
      await api('auction', `/api/auctions/${id}/bids`, {method: 'POST', headers: {'Idempotency-Key': pending.key}, body: JSON.stringify({amount: value})});
      sessionStorage.removeItem(storageKey); flash(feedback, 'Bid accepted.', true); amount.value = '';
    } catch(e) { flash(feedback, e.message); } finally { await live.refresh().catch(() => {}); }
  });
  live = new LiveAuction({id, url: CONFIG.websocket, snapshot: () => api('auction', `/api/auctions/${id}`), apply, status: text => {if(generation === routeId) connection.textContent = text;}});
  live.refresh().catch(e => { if(generation === routeId) flash(feedback, e.message); });
  timer = setInterval(tick, 1000);
  dispose = () => { live.close(); clearInterval(timer); };
}
async function minePage(mode = 'mine', page = 0) {
  dispose(); dispose = () => {};
  if (!requireLogin()) return;
  const generation = ++routeId;
  const content = el('section', {class: 'grid'}, empty('Loading your auctions…'));
  const tabs = el('div', {class: 'tabs'}, ...[['mine','Selling'],['participated','Bidding'],['won','Won'],['items','My items']].map(([id,label]) => button(label, () => minePage(id), mode === id ? 'button small' : 'secondary')));
  const pagination = el('div');
  app.replaceChildren(el('p', {class: 'eyebrow'}, 'YOUR SECOND ROUND'), el('h1', {}, 'My auctions'), tabs, content, pagination);
  try {
    const data = await api(mode === 'items' ? 'item' : 'auction', mode === 'items' ? `/api/items/mine?page=${page}` : `/api/auctions/${mode}?page=${page}`);
    if(generation !== routeId)return;
    content.replaceChildren(...(data.content.length ? data.content.map(mode === 'items' ? itemCard : card) : [empty('Nothing here yet. Explore an auction or list your first item.')]));
    pagination.replaceChildren(pager(page, data.totalElements, data.size, p => minePage(mode, p)));
  } catch(e) { if(generation === routeId)content.replaceChildren(empty(e.message)); }
}
function itemCard(item) {
  const status = el('p', {role: 'status'});
  const form = el('form', {class: 'panel item-editor'}, image(item.imageUrl,item.name), field('Name','name','text',{value:item.name,maxlength:120}),field('Description','description','textarea',{maxlength:4000}),field('Image URL','imageUrl','url',{value:item.imageUrl,maxlength:2000}),status);
  form.elements.description.value = item.description;
  if(item.frozenBy) { form.append(el('p', {class:'muted'}, 'Frozen for auction'),link('View auction',`/auction/${item.frozenBy}`)); for(const input of form.querySelectorAll('input,textarea'))input.readOnly=true; }
  else {
    const save=el('button',{type:'submit'},'Save item');form.append(save);
    form.append(button('Start auction', () => {
      sessionStorage.setItem(`publish:${user.id}`,JSON.stringify({fields:{name:item.name,description:item.description,imageUrl:item.imageUrl},itemId:item.id,itemKey:crypto.randomUUID(),auctionKey:crypto.randomUUID()}));
      location.hash='#/sell';
    }, 'secondary'));
    form.addEventListener('submit',async e=>{e.preventDefault();save.disabled=true;try{await api('item',`/api/items/${item.id}`,{method:'PUT',body:JSON.stringify(Object.fromEntries(new FormData(form)))});flash(status,'Item saved.',true);}catch(e){flash(status,e.message);}finally{save.disabled=false;}});
  }
  return form;
}
async function route() {
  dispose(); dispose = () => {}; routeId++;
  const path = location.hash.slice(1) || '/';
  if (path === '/login') authPage(false);
  else if (path === '/register') authPage(true);
  else if (path === '/sell') sellPage();
  else if (path === '/mine') minePage();
  else if (/^\/auction\/[0-9a-f-]{36}$/.test(path)) detailPage(path.split('/')[2]);
  else home();
}
window.addEventListener('hashchange', route);
async function boot() {
  if (token) { try { user = await api('user','/api/users/me'); } catch { token = null; sessionStorage.removeItem('accessToken'); } }
  renderNav(); route();
}
boot();
