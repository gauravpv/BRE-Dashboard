(() => {
  'use strict';

  const workspace = document.getElementById('duWorkspace');
  const toolNav = document.getElementById('duToolNav');
  const toolSearch = document.getElementById('duToolSearch');
  const headerDesc = document.getElementById('duHeaderDesc');

  const TOOL_CATALOG = [
    { id: 'json-beautify', cat: 'Format', icon: 'data_object', label: 'JSON Beautify', desc: 'Format, minify, validate, and analyze JSON' },
    { id: 'json-compare', cat: 'Compare', icon: 'compare', label: 'JSON Compare', desc: 'Diff two JSON documents side by side' },
    { id: 'html-viewer', cat: 'Format', icon: 'html', label: 'HTML Viewer', desc: 'Live HTML preview with sandboxed iframe' },
    { id: 'xml-viewer', cat: 'Format', icon: 'code', label: 'XML Viewer', desc: 'Format XML and convert to JSON' },
    { id: 'string-compare', cat: 'Compare', icon: 'difference', label: 'String Compare', desc: 'Line-by-line text diff with options' },
    { id: 'base64', cat: 'Encode', icon: 'swap_horiz', label: 'Base64', desc: 'Standard and URL-safe Base64 codec' },
    { id: 'url-codec', cat: 'Encode', icon: 'link', label: 'URL Codec', desc: 'Encode, decode, and parse URLs' },
    { id: 'aes', cat: 'Crypto', icon: 'lock', label: 'AES-256-GCM', desc: 'Encrypt and decrypt with a raw 256-bit key and IV' },
    { id: 'jwt', cat: 'Crypto', icon: 'token', label: 'JWT Decoder', desc: 'Inspect JWT header and payload' },
    { id: 'regex', cat: 'Text', icon: 'regular_expression', label: 'Regex Tester', desc: 'Test regular expressions with match highlights' },
    { id: 'uuid', cat: 'Generate', icon: 'fingerprint', label: 'UUID Generator', desc: 'Generate random v4 UUIDs in bulk' },
    { id: 'timestamp', cat: 'Generate', icon: 'schedule', label: 'Timestamp', desc: 'Convert Unix, ISO, and relative time' },
    { id: 'text-case', cat: 'Text', icon: 'text_fields', label: 'Text Case', desc: 'Transform casing and count words/chars' },
  ];

  let activeTool = localStorage.getItem('du-active-tool') || 'json-beautify';
  if (!TOOL_CATALOG.some(t => t.id === activeTool)) activeTool = 'json-beautify';

  const TOOLS = Object.fromEntries(TOOL_CATALOG.map(t => [t.id, null]));
  TOOLS['json-beautify'] = renderJsonBeautify;
  TOOLS['json-compare'] = renderJsonCompare;
  TOOLS['html-viewer'] = renderHtmlViewer;
  TOOLS['xml-viewer'] = renderXmlViewer;
  TOOLS['string-compare'] = renderStringCompare;
  TOOLS['base64'] = renderBase64;
  TOOLS['url-codec'] = renderUrlCodec;
  TOOLS['aes'] = renderAes;
  TOOLS['jwt'] = renderJwt;
  TOOLS['regex'] = renderRegex;
  TOOLS['uuid'] = renderUuid;
  TOOLS['timestamp'] = renderTimestamp;
  TOOLS['text-case'] = renderTextCase;

  /* ── DOM helpers ── */
  function el(tag, cls, html) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (html != null) n.innerHTML = html;
    return n;
  }

  function icon(name) {
    return `<span class="material-symbols-outlined text-[16px]">${name}</span>`;
  }

  function btn(label, opts = {}) {
    const { icon: ic, primary, ghost, small, onClick } = opts;
    const cls = ghost ? 'du-btn du-btn-ghost' : primary ? 'du-btn du-btn-primary' : 'du-btn du-btn-secondary';
    const b = el('button', cls + (small ? ' text-xs py-1 px-2' : ''));
    b.type = 'button';
    b.innerHTML = ic ? `${icon(ic)}<span>${label}</span>` : label;
    if (onClick) b.addEventListener('click', onClick);
    return b;
  }

  function field(label, control, actions) {
    const g = el('div', 'du-field-group');
    const lbl = el('div', 'du-field-label');
    lbl.appendChild(el('span', '', label));
    if (actions) {
      const acts = el('div', 'du-field-label-actions');
      actions.forEach(a => acts.appendChild(a));
      lbl.appendChild(acts);
    }
    g.appendChild(lbl);
    mountControl(g, control);
    return g;
  }

  function copyBtn(getText) {
    return btn('Copy', { small: true, onClick: () => copyText(typeof getText === 'function' ? getText() : getText) });
  }

  function pasteBtn(setText) {
    return btn('Paste', { small: true, onClick: () => pasteText(setText) });
  }

  function clearBtn(clearFn) {
    return btn('Clear', { small: true, onClick: clearFn });
  }

  function panelActions(control) {
    const actions = [copyBtn(() => control.value)];
    if (!control.readOnly) {
      actions.unshift(pasteBtn(v => { control.value = v; }));
      actions.push(clearBtn(() => { control.value = ''; control.dispatchEvent(new Event('input')); }));
    }
    return actions;
  }

  function genBtn(onClick) {
    return btn('Generate', { small: true, onClick });
  }

  function mountControl(parent, control) {
    parent.appendChild(control.duWrapper || control);
  }

  function textarea(ph, opts = {}) {
    const t = el('textarea', 'du-textarea custom-scrollbar' + (opts.readOnly ? ' du-input-readonly' : ''));
    t.placeholder = ph;
    if (opts.readOnly) t.readOnly = true;
    if (opts.rows) t.rows = opts.rows;
    if (opts.noLines) return t;

    const wrap = el('div', 'du-code-editor');
    const gutter = el('div', 'du-line-numbers custom-scrollbar');
    gutter.setAttribute('aria-hidden', 'true');
    wrap.appendChild(gutter);
    wrap.appendChild(t);

    const minLines = opts.minLines || 12;

    function updateLines() {
      const text = t.value || '';
      const lineCount = text.length ? text.split('\n').length : 1;
      const total = Math.max(lineCount, minLines);
      gutter.textContent = Array.from({ length: total }, (_, i) => String(i + 1)).join('\n');
    }

    t.duRefreshLines = updateLines;
    t.duWrapper = wrap;
    t.addEventListener('input', updateLines);
    t.addEventListener('scroll', () => { gutter.scrollTop = t.scrollTop; });
    t.addEventListener('keydown', e => {
      if (e.key === 'Tab' && !e.shiftKey && !e.ctrlKey && !e.metaKey && !e.altKey) {
        e.preventDefault();
        const start = t.selectionStart;
        const end = t.selectionEnd;
        const val = t.value;
        t.value = val.slice(0, start) + '  ' + val.slice(end);
        t.selectionStart = t.selectionEnd = start + 2;
        t.dispatchEvent(new Event('input', { bubbles: true }));
      }
    });

    const nativeValue = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value');
    Object.defineProperty(t, 'value', {
      get() { return nativeValue.get.call(t); },
      set(v) { nativeValue.set.call(t, v); updateLines(); },
      configurable: true,
    });

    queueMicrotask(updateLines);
    return t;
  }

  function input(ph, type = 'text') {
    const i = el('input', 'du-input du-input-sm');
    i.type = type;
    i.placeholder = ph;
    return i;
  }

  function select(options, value) {
    const s = el('select', 'du-select du-input-sm');
    options.forEach(([v, l]) => {
      const o = el('option');
      o.value = v;
      o.textContent = l;
      if (v === value) o.selected = true;
      s.appendChild(o);
    });
    return s;
  }

  function statusBar(msg, type = 'info') {
    const s = el('div', `du-status-bar ${type}`);
    s.innerHTML = `${icon(type === 'success' ? 'check_circle' : type === 'error' ? 'error' : 'info')}<span>${escapeHtml(msg)}</span>`;
    return s;
  }

  function statusFooter() {
    return el('div', 'du-status-footer');
  }

  function workspaceMain() {
    return el('div', 'du-workspace-main');
  }

  function workspaceShell(meta, bodyFn) {
    workspace.innerHTML = '';
    const card = el('div', 'du-workspace-card');
    const head = el('div', 'du-workspace-header');
    head.innerHTML = `<div class="du-workspace-header-inner">
      <h3 class="du-workspace-title">${escapeHtml(meta.label)}</h3>
      <span class="du-chip du-workspace-cat">${escapeHtml(meta.cat)}</span>
    </div>`;
    const body = el('div', 'du-workspace-body');
    card.appendChild(head);
    card.appendChild(body);
    workspace.appendChild(card);
    if (headerDesc) headerDesc.textContent = meta.desc;
    bodyFn(body);
    queueMicrotask(() => {
      const first = body.querySelector('textarea:not([readonly]), input:not([readonly]):not([type=checkbox]):not([type=number])');
      first?.focus();
    });
  }

  function split(leftLabel, leftEl, rightLabel, rightEl) {
    const row = el('div', 'du-split');
    const mk = (label, content) => {
      const col = el('div', 'du-panel-col');
      col.appendChild(field(label, content, panelActions(content)));
      return col;
    };
    row.appendChild(mk(leftLabel, leftEl));
    row.appendChild(mk(rightLabel, rightEl));
    return row;
  }

  function toolbar(...items) {
    const bar = el('div', 'du-toolbar');
    items.forEach(i => bar.appendChild(i));
    return bar;
  }

  function optionsRow(...labels) {
    const row = el('div', 'du-options-row');
    labels.forEach(({ id, label, checked }) => {
      const lbl = el('label');
      const cb = el('input');
      cb.type = 'checkbox';
      cb.id = id;
      if (checked) cb.checked = true;
      lbl.appendChild(cb);
      lbl.appendChild(document.createTextNode(label));
      row.appendChild(lbl);
    });
    return row;
  }

  let toastTimer;
  function showToast(msg, type = 'success') {
    let toast = document.getElementById('duToast');
    if (!toast) {
      toast = el('div', 'du-toast');
      toast.id = 'duToast';
      document.body.appendChild(toast);
    }
    toast.className = `du-toast du-toast-${type}`;
    toast.textContent = msg;
    requestAnimationFrame(() => toast.classList.add('du-toast-visible'));
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('du-toast-visible'), 2200);
  }

  async function copyText(text) {
    const value = text ?? '';
    if (!value) {
      showToast('Nothing to copy', 'info');
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      showToast('Copied to clipboard', 'success');
    } catch {
      showToast('Copy failed', 'error');
    }
  }

  async function pasteText(setText) {
    try {
      const text = await navigator.clipboard.readText();
      if (typeof setText === 'function') setText(text);
      showToast('Pasted from clipboard', 'success');
    } catch {
      showToast('Paste failed — allow clipboard access', 'error');
    }
  }

  function escapeHtml(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  /* ── utilities ── */
  function prettyJson(obj, spaces) {
    return JSON.stringify(obj, null, spaces);
  }

  function jsonStats(obj) {
    let nodes = 0;
    const walk = (v) => {
      nodes++;
      if (Array.isArray(v)) v.forEach(walk);
      else if (v && typeof v === 'object') Object.values(v).forEach(walk);
    };
    walk(obj);
    return nodes;
  }

  function sortKeysDeep(o) {
    if (Array.isArray(o)) return o.map(sortKeysDeep);
    if (o && typeof o === 'object') {
      return Object.keys(o).sort().reduce((acc, k) => { acc[k] = sortKeysDeep(o[k]); return acc; }, {});
    }
    return o;
  }

  function formatXml(xml) {
    const PAD = '  ';
    let formatted = '';
    let pad = 0;
    xml = xml.replace(/>\s*</g, '><').trim();
    xml.split(/(<[^>]+>)/g).filter(Boolean).forEach(token => {
      if (/^<\/\w/.test(token)) pad = Math.max(0, pad - 1);
      formatted += PAD.repeat(pad) + token + '\n';
      if (/^<\w[^>]*[^/]>$/.test(token)) pad++;
    });
    return formatted.trim();
  }

  function xmlToJson(node) {
    if (node.nodeType === 3) return node.textContent.trim() || undefined;
    if (node.nodeType !== 1) return null;
    const obj = {};
    if (node.attributes.length) {
      obj['@attributes'] = {};
      for (const attr of node.attributes) obj['@attributes'][attr.name] = attr.value;
    }
    for (const child of node.childNodes) {
      if (child.nodeType === 3 && !child.textContent.trim()) continue;
      const name = child.nodeName;
      const val = xmlToJson(child);
      if (val === undefined) continue;
      if (obj[name] !== undefined) {
        if (!Array.isArray(obj[name])) obj[name] = [obj[name]];
        obj[name].push(val);
      } else obj[name] = val;
    }
    if (Object.keys(obj).length === 1 && obj['#text']) return obj['#text'];
    return obj;
  }

  function lineDiff(a, b) {
    const la = a.split('\n');
    const lb = b.split('\n');
    const max = Math.max(la.length, lb.length);
    return Array.from({ length: max }, (_, i) => {
      const left = la[i] ?? '';
      const right = lb[i] ?? '';
      return { type: left === right ? 'same' : 'diff', left, right };
    });
  }

  function renderDiff(rows, container) {
    container.innerHTML = '';
    const table = el('table', 'du-diff-table');
    const tbody = el('tbody');
    rows.forEach((row, i) => {
      const tr = el('tr');
      const lc = row.type === 'diff' ? 'du-diff-removed' : 'du-diff-same';
      const rc = row.type === 'diff' ? 'du-diff-added' : 'du-diff-same';
      tr.innerHTML = `<td class="w-10 text-center text-on-surface-variant/60">${i + 1}</td><td class="${lc}">${escapeHtml(row.left)}</td><td class="${rc}">${escapeHtml(row.right)}</td>`;
      tbody.appendChild(tr);
    });
    table.appendChild(tbody);
    container.appendChild(table);
  }

  function b64Encode(str) { return btoa(unescape(encodeURIComponent(str))); }
  function b64Decode(str) { return decodeURIComponent(escape(atob(str.replace(/\s/g, '')))); }
  function b64UrlEncode(str) { return b64Encode(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); }
  function b64UrlDecode(str) { const p = str.replace(/-/g, '+').replace(/_/g, '/'); return b64Decode(p + '='.repeat((4 - p.length % 4) % 4)); }

  function bytesToB64(bytes) { return btoa(String.fromCharCode(...bytes)); }
  function bytesToHex(bytes) { return Array.from(bytes).map(b => b.toString(16).padStart(2, '0')).join(''); }
  function parseBytes(str, encoding) {
    const clean = str.trim();
    if (!clean) throw new Error('Empty value');
    if (encoding === 'hex') {
      if (!/^[0-9a-fA-F]+$/.test(clean) || clean.length % 2) throw new Error('Invalid hex');
      return Uint8Array.from(clean.match(/.{2}/g).map(h => parseInt(h, 16)));
    }
    return Uint8Array.from(atob(clean.replace(/\s/g, '')), c => c.charCodeAt(0));
  }
  function formatBytes(bytes, encoding) {
    return encoding === 'hex' ? bytesToHex(bytes) : bytesToB64(bytes);
  }

  async function importAesKey(keyBytes) {
    if (keyBytes.length !== 32) throw new Error(`Key must be 32 bytes (got ${keyBytes.length})`);
    return crypto.subtle.importKey('raw', keyBytes, { name: 'AES-GCM' }, false, ['encrypt', 'decrypt']);
  }

  async function aesEncryptRaw(plaintext, keyBytes, iv) {
    const key = await importAesKey(keyBytes);
    const cipher = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, new TextEncoder().encode(plaintext));
    return new Uint8Array(cipher);
  }

  async function aesDecryptRaw(ciphertext, keyBytes, iv) {
    const key = await importAesKey(keyBytes);
    const plain = await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, ciphertext);
    return new TextDecoder().decode(plain);
  }

  function decodeJwtPart(part) {
    const padded = part.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(decodeURIComponent(escape(atob(padded + '='.repeat((4 - padded.length % 4) % 4)))));
  }

  function relativeTime(d) {
    const sec = Math.round((d.getTime() - Date.now()) / 1000);
    const abs = Math.abs(sec);
    const unit = abs < 60 ? 'second' : abs < 3600 ? 'minute' : abs < 86400 ? 'hour' : 'day';
    const val = unit === 'second' ? abs : unit === 'minute' ? Math.round(abs / 60) : unit === 'hour' ? Math.round(abs / 3600) : Math.round(abs / 86400);
    return sec <= 0 ? `${val} ${unit}${val !== 1 ? 's' : ''} ago` : `in ${val} ${unit}${val !== 1 ? 's' : ''}`;
  }

  /* ── Tool renderers ── */
  function renderJsonBeautify(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'json-beautify'), (body) => {
      const input = textarea('Paste JSON…');
      const output = textarea('Output…', { readOnly: true });
      const statSlot = statusFooter();
      const chips = el('div', 'du-meta-chips');
      body.appendChild(toolbar(
        btn('Beautify', { icon: 'auto_fix_high', primary: true, onClick: () => run(2) }),
        btn('4 spaces', { icon: 'format_indent_increase', onClick: () => run(4) }),
        btn('Minify', { icon: 'compress', onClick: minify }),
        btn('Validate', { icon: 'check', onClick: validate }),
        btn('Sort keys', { icon: 'sort', onClick: sortKeys }),
        btn('Escape Unicode', { icon: 'code', onClick: () => { try { output.value = JSON.stringify(JSON.parse(input.value)); statSlot.replaceChildren(statusBar('Unicode escaped', 'success')); } catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); } } }),
        btn('Copy', { icon: 'content_copy', onClick: () => copyText(output.value) }),
        btn('Clear', { icon: 'delete', ghost: true, onClick: () => { input.value = ''; output.value = ''; statSlot.innerHTML = ''; chips.innerHTML = ''; } })
      ));
      function run(spaces) {
        try {
          const parsed = JSON.parse(input.value);
          output.value = prettyJson(parsed, spaces);
          chips.innerHTML = `<span class="du-chip">${jsonStats(parsed)} nodes</span><span class="du-chip">${output.value.length} chars</span><span class="du-chip">${output.value.split('\n').length} lines</span>`;
          statSlot.replaceChildren(statusBar('Valid JSON', 'success'));
        } catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      function minify() {
        try { output.value = JSON.stringify(JSON.parse(input.value)); statSlot.replaceChildren(statusBar('Minified', 'success')); }
        catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      function validate() {
        try { JSON.parse(input.value); statSlot.replaceChildren(statusBar('Valid JSON ✓', 'success')); }
        catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      function sortKeys() {
        try { output.value = prettyJson(sortKeysDeep(JSON.parse(input.value)), 2); statSlot.replaceChildren(statusBar('Keys sorted', 'success')); }
        catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      body.appendChild(chips);
      const main = workspaceMain();
      main.appendChild(split('Input', input, 'Output', output));
      body.appendChild(main);
      body.appendChild(statSlot);
    });
  }

  function renderJsonCompare(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'json-compare'), (body) => {
      const left = textarea('Original JSON…');
      const right = textarea('Modified JSON…');
      const diffBox = el('div', 'du-diff-scroll custom-scrollbar');
      const statSlot = statusFooter();
      const opts = optionsRow(
        { id: 'du-json-sort', label: 'Sort keys before compare' },
        { id: 'du-json-ws', label: 'Ignore whitespace-only lines' }
      );
      body.appendChild(opts);
      body.appendChild(toolbar(
        btn('Compare', { icon: 'compare', primary: true, onClick: compare }),
        btn('Swap', { icon: 'swap_horiz', onClick: () => { const t = left.value; left.value = right.value; right.value = t; } }),
        btn('Clear diff', { icon: 'clear', ghost: true, onClick: () => { diffBox.innerHTML = ''; statSlot.innerHTML = ''; } })
      ));
      function compare() {
        try {
          let a = JSON.parse(left.value);
          let b = JSON.parse(right.value);
          if (document.getElementById('du-json-sort').checked) { a = sortKeysDeep(a); b = sortKeysDeep(b); }
          let sa = prettyJson(a, 2);
          let sb = prettyJson(b, 2);
          if (document.getElementById('du-json-ws').checked) {
            sa = sa.split('\n').filter(l => l.trim()).join('\n');
            sb = sb.split('\n').filter(l => l.trim()).join('\n');
          }
          renderDiff(lineDiff(sa, sb), diffBox);
          const same = sa === sb;
          statSlot.replaceChildren(statusBar(same ? 'Documents are identical' : 'Differences highlighted below', same ? 'success' : 'info'));
        } catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      const main = workspaceMain();
      main.appendChild(split('Original', left, 'Modified', right));
      main.appendChild(field('Diff result', diffBox));
      body.appendChild(main);
      body.appendChild(statSlot);
    });
  }

  function renderHtmlViewer(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'html-viewer'), (body) => {
      const input = textarea('Paste or type HTML here…');
      input.value = '<!DOCTYPE html>\n<html>\n<head>\n  <style>\n    body { font-family: system-ui; padding: 2rem; background: #f6faff; color: #051125; }\n  </style>\n</head>\n<body>\n  <h1>Hello BRE Dashboard</h1>\n  <p>Edit the HTML in the left panel.</p>\n</body>\n</html>';
      const previewWrap = el('div', 'du-preview-panel');
      const iframe = el('iframe', 'du-preview-frame');
      iframe.sandbox = 'allow-same-origin';
      iframe.title = 'HTML preview';
      previewWrap.appendChild(iframe);

      const autoCb = optionsRow({ id: 'du-html-auto', label: 'Auto-render on type (400ms debounce)', checked: true });
      let timer;
      const render = () => { iframe.srcdoc = input.value; };
      input.addEventListener('input', () => {
        const cb = document.getElementById('du-html-auto');
        if (cb?.checked) { clearTimeout(timer); timer = setTimeout(render, 400); }
      });

      body.appendChild(autoCb);
      body.appendChild(toolbar(
        btn('Render preview', { icon: 'preview', primary: true, onClick: render }),
        btn('Copy HTML', { icon: 'content_copy', onClick: () => copyText(input.value) }),
        btn('Clear', { icon: 'delete', ghost: true, onClick: () => { input.value = ''; render(); } }),
        btn('Open in new tab', { icon: 'open_in_new', onClick: () => { const w = window.open(); w.document.write(input.value); w.document.close(); } })
      ));
      const main = workspaceMain();
      main.appendChild(split('HTML source — paste here', input, 'Live preview', previewWrap));
      body.appendChild(main);
      render();
    });
  }

  function renderXmlViewer(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'xml-viewer'), (body) => {
      const input = textarea('<root><item id="1">value</item></root>');
      const output = textarea('Output…', { readOnly: true });
      const statSlot = statusFooter();
      body.appendChild(toolbar(
        btn('Format', { icon: 'auto_fix_high', primary: true, onClick: format }),
        btn('Minify', { icon: 'compress', onClick: minify }),
        btn('To JSON', { icon: 'data_object', onClick: toJson }),
        btn('Copy', { icon: 'content_copy', onClick: () => copyText(output.value) })
      ));
      function parseDoc() {
        const doc = new DOMParser().parseFromString(input.value, 'application/xml');
        if (doc.querySelector('parsererror')) throw new Error('Invalid XML');
        return doc;
      }
      function format() {
        try { output.value = formatXml(new XMLSerializer().serializeToString(parseDoc())); statSlot.replaceChildren(statusBar('Formatted', 'success')); }
        catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      function minify() {
        try { output.value = new XMLSerializer().serializeToString(parseDoc()).replace(/>\s+</g, '><'); statSlot.replaceChildren(statusBar('Minified', 'success')); }
        catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      function toJson() {
        try { output.value = prettyJson(xmlToJson(parseDoc().documentElement), 2); statSlot.replaceChildren(statusBar('Converted to JSON', 'success')); }
        catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      const main = workspaceMain();
      main.appendChild(split('XML input', input, 'Output', output));
      body.appendChild(main);
      body.appendChild(statSlot);
    });
  }

  function renderStringCompare(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'string-compare'), (body) => {
      const left = textarea('Text A…');
      const right = textarea('Text B…');
      const diffBox = el('div', 'du-diff-scroll custom-scrollbar');
      const statSlot = statusFooter();
      body.appendChild(optionsRow(
        { id: 'du-str-trim', label: 'Trim trailing whitespace per line' },
        { id: 'du-str-ignore', label: 'Ignore case' }
      ));
      body.appendChild(toolbar(
        btn('Compare', { icon: 'difference', primary: true, onClick: compare }),
        btn('Clear', { icon: 'delete', ghost: true, onClick: () => { left.value = ''; right.value = ''; diffBox.innerHTML = ''; statSlot.innerHTML = ''; } })
      ));
      function compare() {
        let a = left.value;
        let b = right.value;
        if (document.getElementById('du-str-trim').checked) {
          a = a.split('\n').map(l => l.trimEnd()).join('\n');
          b = b.split('\n').map(l => l.trimEnd()).join('\n');
        }
        if (document.getElementById('du-str-ignore').checked) {
          a = a.toLowerCase();
          b = b.toLowerCase();
        }
        renderDiff(lineDiff(a, b), diffBox);
        const same = a === b;
        statSlot.replaceChildren(statusBar(same ? 'Identical' : `${left.value.length} vs ${right.value.length} chars · ${a.split('\n').length} vs ${b.split('\n').length} lines`, same ? 'success' : 'info'));
      }
      const main = workspaceMain();
      main.appendChild(split('Text A', left, 'Text B', right));
      main.appendChild(field('Line diff', diffBox));
      body.appendChild(main);
      body.appendChild(statSlot);
    });
  }

  function renderBase64(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'base64'), (body) => {
      const input = textarea('Text or Base64…');
      const output = textarea('Result…', { readOnly: true });
      const statSlot = statusFooter();
      body.appendChild(toolbar(
        btn('Encode', { icon: 'north_east', primary: true, onClick: () => run(() => b64Encode(input.value), 'Standard Base64 encoded') }),
        btn('Decode', { icon: 'south_west', onClick: () => run(() => b64Decode(input.value), 'Decoded') }),
        btn('URL-safe encode', { icon: 'link', onClick: () => run(() => b64UrlEncode(input.value), 'URL-safe encoded') }),
        btn('URL-safe decode', { icon: 'link_off', onClick: () => run(() => b64UrlDecode(input.value), 'URL-safe decoded') }),
        btn('Copy', { icon: 'content_copy', onClick: () => copyText(output.value) })
      ));
      function run(fn, msg) {
        try { output.value = fn(); statSlot.replaceChildren(statusBar(msg, 'success')); }
        catch (e) { statSlot.replaceChildren(statusBar(e.message || 'Invalid input', 'error')); }
      }
      const main = workspaceMain();
      main.appendChild(split('Input', input, 'Output', output));
      body.appendChild(main);
      body.appendChild(statSlot);
    });
  }

  function renderUrlCodec(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'url-codec'), (body) => {
      const input = textarea('URL or text…');
      const output = textarea('Result…', { readOnly: true });
      const statSlot = statusFooter();
      body.appendChild(toolbar(
        btn('Encode URI', { icon: 'link', primary: true, onClick: () => { output.value = encodeURI(input.value); statSlot.replaceChildren(statusBar('URI encoded', 'success')); } }),
        btn('Encode component', { icon: 'link', onClick: () => { output.value = encodeURIComponent(input.value); statSlot.replaceChildren(statusBar('Component encoded', 'success')); } }),
        btn('Decode', { icon: 'link_off', onClick: () => { try { output.value = decodeURIComponent(input.value); statSlot.replaceChildren(statusBar('Decoded', 'success')); } catch { statSlot.replaceChildren(statusBar('Invalid encoded URI', 'error')); } } }),
        btn('Parse URL', { icon: 'travel_explore', onClick: () => {
          try {
            const u = new URL(input.value.trim());
            output.value = prettyJson({ protocol: u.protocol, host: u.host, pathname: u.pathname, search: u.search, hash: u.hash, origin: u.origin, params: Object.fromEntries(u.searchParams) }, 2);
            statSlot.replaceChildren(statusBar('URL parsed', 'success'));
          } catch (e) { statSlot.replaceChildren(statusBar('Invalid URL', 'error')); }
        } }),
        btn('Copy', { icon: 'content_copy', onClick: () => copyText(output.value) })
      ));
      const main = workspaceMain();
      main.appendChild(split('Input', input, 'Output', output));
      body.appendChild(main);
      body.appendChild(statSlot);
    });
  }

  function renderAes(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'aes'), (body) => {
      let mode = 'encrypt';
      const encoding = select([['base64', 'Base64'], ['hex', 'Hex']], 'base64');
      const aesKey = input('AES key (32 bytes)');
      const iv = input('IV / nonce (12 bytes)');

      const plaintext = textarea('Plaintext to encrypt…');
      const ciphertextIn = textarea('Ciphertext (base64 or hex)…');
      const ciphertextOut = textarea('Encrypted ciphertext…', { readOnly: true });
      const plaintextOut = textarea('Decrypted plaintext…', { readOnly: true });

      const statSlot = statusFooter();
      const encTab = btn('Encrypt', { primary: true, onClick: () => setMode('encrypt') });
      const decTab = btn('Decrypt', { onClick: () => setMode('decrypt') });

      const encPanel = el('div');
      const decPanel = el('div', 'du-hidden');
      encPanel.appendChild(split('Plaintext', plaintext, 'Ciphertext', ciphertextOut));
      decPanel.appendChild(split('Ciphertext', ciphertextIn, 'Plaintext', plaintextOut));

      const runBtn = btn('Encrypt', { icon: 'lock', primary: true, onClick: () => run(mode) });

      function setMode(m) {
        mode = m;
        encTab.className = m === 'encrypt' ? 'du-btn du-btn-primary' : 'du-btn du-btn-secondary';
        decTab.className = m === 'decrypt' ? 'du-btn du-btn-primary' : 'du-btn du-btn-secondary';
        encPanel.classList.toggle('du-hidden', m !== 'encrypt');
        decPanel.classList.toggle('du-hidden', m !== 'decrypt');
        const label = runBtn.querySelector('span');
        const ic = runBtn.querySelector('.material-symbols-outlined');
        if (label) label.textContent = m === 'encrypt' ? 'Encrypt' : 'Decrypt';
        if (ic) ic.textContent = m === 'encrypt' ? 'lock' : 'lock_open';
      }

      function genKeyIv(quiet) {
        aesKey.value = formatBytes(crypto.getRandomValues(new Uint8Array(32)), encoding.value);
        iv.value = formatBytes(crypto.getRandomValues(new Uint8Array(12)), encoding.value);
        if (!quiet) statSlot.replaceChildren(statusBar('New key and IV generated', 'success'));
      }

      encoding.addEventListener('change', () => {
        [aesKey, iv].forEach(elRef => {
          if (!elRef.value.trim()) return;
          try {
            const bytes = parseBytes(elRef.value, encoding.value);
            elRef.value = formatBytes(bytes, encoding.value);
          } catch { /* keep as-is */ }
        });
      });

      const modeRow = el('div', 'du-mode-row');
      modeRow.appendChild(encTab);
      modeRow.appendChild(decTab);
      const encWrap = el('div', 'du-encoding-wrap');
      encWrap.appendChild(document.createTextNode('Encoding'));
      encWrap.appendChild(encoding);
      modeRow.appendChild(encWrap);

      const keyIvRow = el('div', 'du-crypto-grid');
      keyIvRow.appendChild(field('Key', aesKey, [copyBtn(() => aesKey.value)]));
      keyIvRow.appendChild(field('IV', iv, [copyBtn(() => iv.value)]));

      body.appendChild(modeRow);
      body.appendChild(keyIvRow);
      body.appendChild(toolbar(
        btn('Generate key & IV', { icon: 'casino', onClick: () => genKeyIv(false) }),
        runBtn,
        btn('Copy bundle', { icon: 'content_copy', onClick: () => {
          copyText(prettyJson({
            algorithm: 'AES-256-GCM',
            encoding: encoding.value,
            key: aesKey.value,
            iv: iv.value,
            ciphertext: mode === 'encrypt' ? ciphertextOut.value : ciphertextIn.value.trim(),
          }, 2));
        } }),
      ));
      const main = workspaceMain();
      main.appendChild(encPanel);
      main.appendChild(decPanel);
      body.appendChild(main);
      body.appendChild(statSlot);

      async function run(action) {
        const enc = encoding.value;
        try {
          const keyBytes = parseBytes(aesKey.value, enc);
          const ivBytes = parseBytes(iv.value, enc);
          if (ivBytes.length !== 12) throw new Error(`IV must be 12 bytes for GCM (got ${ivBytes.length})`);

          if (action === 'encrypt') {
            const cipherBytes = await aesEncryptRaw(plaintext.value, keyBytes, ivBytes);
            ciphertextOut.value = formatBytes(cipherBytes, enc);
            statSlot.replaceChildren(statusBar(`Encrypted · ${cipherBytes.length} bytes`, 'success'));
          } else {
            const cipherBytes = parseBytes(ciphertextIn.value, enc);
            plaintextOut.value = await aesDecryptRaw(cipherBytes, keyBytes, ivBytes);
            statSlot.replaceChildren(statusBar('Decrypted successfully', 'success'));
          }
        } catch (e) {
          statSlot.replaceChildren(statusBar(e.message || 'Operation failed', 'error'));
        }
      }

      genKeyIv(true);
    });
  }

  function renderJwt(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'jwt'), (body) => {
      const input = textarea('Paste JWT (eyJhbGciOiJ…)…');
      const headerOut = textarea('Header…', { readOnly: true });
      const payloadOut = textarea('Payload…', { readOnly: true });
      const statSlot = statusFooter();
      let chipsEl = null;
      body.appendChild(toolbar(
        btn('Decode', { icon: 'token', primary: true, onClick: decode }),
        btn('Copy payload', { icon: 'content_copy', onClick: () => copyText(payloadOut.value) })
      ));
      function decode() {
        try {
          const parts = input.value.trim().split('.');
          if (parts.length < 2) throw new Error('JWT must have at least header.payload');
          headerOut.value = prettyJson(decodeJwtPart(parts[0]), 2);
          payloadOut.value = prettyJson(decodeJwtPart(parts[1]), 2);
          const payload = decodeJwtPart(parts[1]);
          if (chipsEl) chipsEl.remove();
          chipsEl = el('div', 'du-meta-chips');
          if (payload.exp) chipsEl.innerHTML += `<span class="du-chip">exp: ${new Date(payload.exp * 1000).toISOString()}</span>`;
          if (payload.iat) chipsEl.innerHTML += `<span class="du-chip">iat: ${new Date(payload.iat * 1000).toISOString()}</span>`;
          chipsEl.innerHTML += `<span class="du-chip">${parts[2] ? 'Signature present' : 'Unsigned'}</span>`;
          statSlot.replaceChildren(statusBar('JWT decoded (signature not verified)', 'success'));
          statSlot.after(chipsEl);
        } catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      const main = workspaceMain();
      main.appendChild(split('JWT token', input, 'Header', headerOut));
      main.appendChild(field('Payload', payloadOut, panelActions(payloadOut)));
      body.appendChild(main);
      body.appendChild(statSlot);
    });
  }

  function renderRegex(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'regex'), (body) => {
      const pattern = input('Pattern e.g. \\d+');
      const flags = input('Flags e.g. gi');
      flags.value = 'g';
      const text = textarea('Text to test against…');
      const output = textarea('Matches…', { readOnly: true });
      const statSlot = statusFooter();
      body.appendChild(field('Pattern', pattern));
      body.appendChild(field('Flags', flags));
      body.appendChild(toolbar(
        btn('Test', { icon: 'regular_expression', primary: true, onClick: test }),
        btn('Replace preview', { icon: 'find_replace', onClick: () => {
          try {
            const re = new RegExp(pattern.value, flags.value.replace(/[^gimsuy]/g, ''));
            output.value = text.value.replace(re, '[$&]');
            statSlot.replaceChildren(statusBar('Replace preview (matches wrapped in brackets)', 'info'));
          } catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
        } })
      ));
      function test() {
        try {
          const re = new RegExp(pattern.value, flags.value.replace(/[^gimsuy]/g, ''));
          const matches = [...text.value.matchAll(re)];
          output.value = matches.length
            ? matches.map((m, i) => `#${i + 1}: "${m[0]}" @ index ${m.index}${m.groups ? '\n  groups: ' + prettyJson(m.groups) : ''}`).join('\n\n')
            : 'No matches';
          statSlot.replaceChildren(statusBar(`${matches.length} match${matches.length !== 1 ? 'es' : ''}`, matches.length ? 'success' : 'info'));
        } catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      const main = workspaceMain();
      main.appendChild(split('Test string', text, 'Results', output));
      body.appendChild(main);
      body.appendChild(statSlot);
    });
  }

  function renderUuid(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'uuid'), (body) => {
      const count = input('Count');
      count.type = 'number';
      count.value = '10';
      count.min = '1';
      count.max = '500';
      const output = textarea('UUIDs…', { readOnly: true });
      const statSlot = statusFooter();
      const topRow = el('div', 'du-form-inline');
      topRow.appendChild(field('How many', count));
      topRow.appendChild(toolbar(
        btn('Generate v4', { icon: 'fingerprint', primary: true, onClick: gen }),
        btn('Nil UUID', { icon: 'block', onClick: () => { output.value = '00000000-0000-0000-0000-000000000000'; statSlot.replaceChildren(statusBar('Nil UUID', 'info')); } }),
        btn('Copy all', { icon: 'content_copy', onClick: () => copyText(output.value) })
      ));
      function gen() {
        const n = Math.min(500, Math.max(1, parseInt(count.value, 10) || 1));
        output.value = Array.from({ length: n }, () => crypto.randomUUID()).join('\n');
        statSlot.replaceChildren(statusBar(`Generated ${n} UUID v4`, 'success'));
      }
      body.appendChild(topRow);
      const main = workspaceMain();
      main.appendChild(field('Output', output, panelActions(output)));
      body.appendChild(main);
      body.appendChild(statSlot);
      gen();
    });
  }

  function renderTimestamp(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'timestamp'), (body) => {
      const unix = input('Unix timestamp (seconds or milliseconds)');
      const iso = input('ISO 8601');
      const tz = select([['UTC', 'UTC'], ['local', 'Local timezone'], ['Asia/Kolkata', 'Asia/Kolkata (IST)'], ['America/New_York', 'America/New_York'], ['Europe/London', 'Europe/London']], 'local');
      const output = textarea('Details…', { readOnly: true });
      const statSlot = statusFooter();
      const inputGrid = el('div', 'du-form-grid');
      inputGrid.appendChild(field('Unix', unix));
      inputGrid.appendChild(field('ISO 8601', iso));
      const tzField = field('Display timezone', tz);
      tzField.classList.add('du-form-span-2');
      inputGrid.appendChild(tzField);
      body.appendChild(inputGrid);
      body.appendChild(toolbar(
        btn('Unix → Date', { icon: 'schedule', primary: true, onClick: () => convert('unix') }),
        btn('ISO → Unix', { icon: 'event', onClick: () => convert('iso') }),
        btn('Now', { icon: 'update', onClick: () => { const d = new Date(); unix.value = String(Math.floor(d.getTime() / 1000)); iso.value = d.toISOString(); fill(d); statSlot.replaceChildren(statusBar('Current time', 'success')); } })
      ));
      function fmt(d) {
        const tzVal = tz.value;
        const localeStr = tzVal === 'local' ? d.toString() : d.toLocaleString('en-US', { timeZone: tzVal, dateStyle: 'full', timeStyle: 'long' });
        return `${localeStr}\n\nISO: ${d.toISOString()}\nUnix (s): ${Math.floor(d.getTime() / 1000)}\nUnix (ms): ${d.getTime()}\nRelative: ${relativeTime(d)}`;
      }
      function fill(d) { output.value = fmt(d); }
      function convert(from) {
        try {
          let d;
          if (from === 'unix') {
            let ts = Number(unix.value.trim());
            if (String(Math.floor(ts)).length <= 10) ts *= 1000;
            d = new Date(ts);
            if (isNaN(d.getTime())) throw new Error('Invalid timestamp');
            iso.value = d.toISOString();
          } else {
            d = new Date(iso.value.trim());
            if (isNaN(d.getTime())) throw new Error('Invalid ISO date');
            unix.value = String(Math.floor(d.getTime() / 1000));
          }
          fill(d);
          statSlot.replaceChildren(statusBar('Converted', 'success'));
        } catch (e) { statSlot.replaceChildren(statusBar(e.message, 'error')); }
      }
      const main = workspaceMain();
      main.appendChild(field('Output', output, panelActions(output)));
      body.appendChild(main);
      body.appendChild(statSlot);
      body.querySelector('.du-toolbar button:last-child').click();
    });
  }

  function renderTextCase(wrap) {
    workspaceShell(TOOL_CATALOG.find(t => t.id === 'text-case'), (body) => {
      const input = textarea('Enter text…');
      const output = textarea('Result…', { readOnly: true });
      const chips = el('div', 'du-meta-chips');
      const updateStats = () => {
        const t = input.value;
        chips.innerHTML = `<span class="du-chip">${t.length} chars</span><span class="du-chip">${t.trim() ? t.trim().split(/\s+/).length : 0} words</span><span class="du-chip">${t.split('\n').length} lines</span>`;
      };
      input.addEventListener('input', updateStats);
      const transforms = {
        'UPPER': s => s.toUpperCase(),
        'lower': s => s.toLowerCase(),
        'Title Case': s => s.replace(/\w\S*/g, t => t.charAt(0).toUpperCase() + t.slice(1).toLowerCase()),
        'camelCase': s => s.toLowerCase().replace(/[^a-zA-Z0-9]+(.)/g, (_, c) => c.toUpperCase()).replace(/^[A-Z]/, c => c.toLowerCase()),
        'PascalCase': s => s.replace(/(?:^\w|[A-Z]|\b\w)/g, w => w.toUpperCase()).replace(/\s+/g, ''),
        'snake_case': s => s.trim().toLowerCase().replace(/\s+/g, '_').replace(/[^\w_]/g, ''),
        'kebab-case': s => s.trim().toLowerCase().replace(/\s+/g, '-').replace(/[^\w-]/g, ''),
        'CONSTANT': s => s.trim().toUpperCase().replace(/\s+/g, '_').replace(/[^\w_]/g, ''),
        'Reverse': s => s.split('').reverse().join(''),
        'Trim lines': s => s.split('\n').map(l => l.trim()).join('\n'),
      };
      const bar = toolbar();
      Object.entries(transforms).forEach(([label, fn], i) => {
        bar.appendChild(btn(label, { primary: i === 0, onClick: () => { output.value = fn(input.value); updateStats(); } }));
      });
      bar.appendChild(btn('Copy', { icon: 'content_copy', onClick: () => copyText(output.value) }));
      body.appendChild(chips);
      body.appendChild(bar);
      const main = workspaceMain();
      main.appendChild(split('Input', input, 'Output', output));
      body.appendChild(main);
      updateStats();
    });
  }

  /* ── Navigation ── */
  function buildNav(filter = '') {
    toolNav.innerHTML = '';
    const q = filter.toLowerCase().trim();
    const matches = TOOL_CATALOG.filter(t => !q || t.label.toLowerCase().includes(q) || t.cat.toLowerCase().includes(q) || t.id.includes(q));
    if (!matches.length) {
      toolNav.appendChild(el('div', 'du-nav-empty', 'No tools match your search'));
      return;
    }
    let currentCat = '';
    matches.forEach(t => {
      if (t.cat !== currentCat) {
        currentCat = t.cat;
        toolNav.appendChild(el('div', 'du-category-label', t.cat));
      }
      const b = el('button', 'du-tool-btn' + (t.id === activeTool ? ' du-tool-active' : ''), `<span class="material-symbols-outlined">${t.icon}</span><span>${t.label}</span>`);
      b.type = 'button';
      b.dataset.tool = t.id;
      b.title = t.desc;
      toolNav.appendChild(b);
    });
    toolNav.querySelector('.du-tool-active')?.scrollIntoView({ block: 'nearest' });
  }

  function loadTool(toolId) {
    if (!TOOLS[toolId]) return;
    activeTool = toolId;
    localStorage.setItem('du-active-tool', toolId);
    TOOLS[toolId]();
    buildNav(toolSearch?.value || '');
  }

  toolNav.addEventListener('click', e => {
    const b = e.target.closest('[data-tool]');
    if (b) loadTool(b.dataset.tool);
  });

  toolSearch?.addEventListener('input', () => buildNav(toolSearch.value));

  /* ── Collapsible toolbox ── */
  const duLayout = document.getElementById('duLayout');
  const duCollapseBtn = document.getElementById('duCollapseBtn');
  const duExpandBtn = document.getElementById('duExpandBtn');
  const duHeaderCollapseBtn = document.getElementById('duHeaderCollapseBtn');
  const duHeaderCollapseIcon = document.getElementById('duHeaderCollapseIcon');

  function isToolboxCollapsed() {
    return duLayout?.classList.contains('du-toolbox-collapsed');
  }

  function setToolboxCollapsed(collapsed) {
    if (!duLayout) return;
    duLayout.classList.toggle('du-toolbox-collapsed', collapsed);
    localStorage.setItem('du-toolbox-collapsed', collapsed ? '1' : '0');
    const title = collapsed ? 'Show toolbox' : 'Hide toolbox';
    duCollapseBtn?.setAttribute('title', title);
    duHeaderCollapseBtn?.setAttribute('title', title);
    if (duHeaderCollapseIcon) {
      duHeaderCollapseIcon.textContent = collapsed ? 'left_panel_open' : 'left_panel_close';
    }
  }

  function toggleToolbox() {
    setToolboxCollapsed(!isToolboxCollapsed());
  }

  duCollapseBtn?.addEventListener('click', toggleToolbox);
  duExpandBtn?.addEventListener('click', toggleToolbox);
  duHeaderCollapseBtn?.addEventListener('click', toggleToolbox);

  if (localStorage.getItem('du-toolbox-collapsed') === '1') {
    setToolboxCollapsed(true);
  }

  document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      if (isToolboxCollapsed()) setToolboxCollapsed(false);
      toolSearch?.focus();
      toolSearch?.select();
    }
    if (e.key === 'Escape' && document.activeElement === toolSearch) {
      toolSearch.value = '';
      buildNav();
      toolSearch.blur();
    }
  });

  buildNav();
  loadTool(activeTool);
})();
