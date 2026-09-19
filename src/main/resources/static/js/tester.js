(() => {
  'use strict';

  const PLACEHOLDER = 'Select an API endpoint…';
  const NO_APIS = 'No APIs loaded';

  const environmentSelect = document.getElementById('btEnvironment');
  const envWrap = document.getElementById('btEnvWrap');
  const apiCombobox = document.getElementById('btApiCombobox');
  const apiTrigger = document.getElementById('btApiTrigger');
  const apiTriggerText = document.getElementById('btApiTriggerText');
  const apiDropdown = document.getElementById('btApiDropdown');
  const apiSearch = document.getElementById('btApiSearch');
  const apiList = document.getElementById('btApiList');
  const reloadBtn = document.getElementById('btReloadBtn');
  const sendBtn = document.getElementById('btSendBtn');
  const formatBtn = document.getElementById('btFormatBtn');
  const clearReqBtn = document.getElementById('btClearReqBtn');
  const copyBtn = document.getElementById('btCopyBtn');
  const statusEl = document.getElementById('btStatus');
  const modeBadge = document.getElementById('btModeBadge');
  const apiCountEl = document.getElementById('btApiCount');
  const tokenBadge = document.getElementById('btTokenBadge');
  const tokenTextEl = document.getElementById('btTokenText');
  const endpointEl = document.getElementById('btEndpoint');
  const requestBodyEl = document.getElementById('btRequestBody');
  const responseBodyEl = document.getElementById('btResponseBody');
  const statusCodeEl = document.getElementById('btStatusCode');
  const durationValEl = document.getElementById('btDurationVal');
  const responseSizeValEl = document.getElementById('btResponseSizeVal');
  const toastEl = document.getElementById('btToast');

  let operations = [];
  let selectedIndex = -1;
  let lastResponseText = '';
  let toastTimer = null;
  let apiDropdownOpen = false;
  let tokenBusy = false;

  function escapeHtml(value) {
    return String(value)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;');
  }

  function prettyJson(text) {
    try {
      return JSON.stringify(JSON.parse(text), null, 2);
    } catch {
      return text;
    }
  }

  function highlightJson(text) {
    const pretty = prettyJson(text);
    if (pretty === text && !text.trim().startsWith('{') && !text.trim().startsWith('[')) {
      return escapeHtml(text);
    }
    return pretty.replace(
      /("(\\u[\dA-Fa-f]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
      (match) => {
        let cls = 'bt-json-number';
        if (/^"/.test(match)) {
          cls = /:$/.test(match) ? 'bt-json-key' : 'bt-json-string';
        } else if (/true|false/.test(match)) {
          cls = 'bt-json-bool';
        } else if (/null/.test(match)) {
          cls = 'bt-json-null';
        }
        return `<span class="${cls}">${escapeHtml(match)}</span>`;
      }
    );
  }

  function showToast(message, tone = 'info') {
    toastEl.textContent = message;
    toastEl.className = `bt-toast bt-toast-${tone} bt-toast-visible`;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
      toastEl.classList.remove('bt-toast-visible');
    }, 2200);
  }

  function setStatus(message, isError = false) {
    statusEl.textContent = message;
    statusEl.classList.toggle('error', isError);
    statusEl.classList.toggle('bt-status-success', !isError && /^\d{3}\s·/.test(message));
  }

  function updateEnvStyle() {
    const isProd = environmentSelect.value === 'PROD';
    envWrap.classList.toggle('bt-env-uat', !isProd);
    envWrap.classList.toggle('bt-env-prod', isProd);
  }

  function selectedOperation() {
    return selectedIndex >= 0 ? operations[selectedIndex] : null;
  }

  function updateEndpoint(op) {
    endpointEl.textContent = op
      ? op.fullUrl
      : 'Select an API to see the full endpoint URL';
  }

  function setTriggerLabel(text, isPlaceholder = false) {
    apiTriggerText.textContent = text;
    apiTriggerText.classList.toggle('is-placeholder', isPlaceholder);
  }

  function closeApiDropdown() {
    apiDropdownOpen = false;
    apiDropdown.classList.add('hidden');
    apiTrigger.setAttribute('aria-expanded', 'false');
  }

  function openApiDropdown() {
    if (apiTrigger.disabled) return;
    apiDropdownOpen = true;
    apiDropdown.classList.remove('hidden');
    apiTrigger.setAttribute('aria-expanded', 'true');
    apiSearch.value = '';
    renderApiList('');
    apiSearch.focus();
  }

  function toggleApiDropdown() {
    if (apiDropdownOpen) {
      closeApiDropdown();
    } else {
      openApiDropdown();
    }
  }

  function updateSendState() {
    const hasApi = selectedIndex >= 0;
    const hasBody = requestBodyEl.value.trim().length > 0;
    sendBtn.disabled = !hasApi || !hasBody;
    requestBodyEl.disabled = !hasApi;
    formatBtn.disabled = !hasApi || !hasBody;
    clearReqBtn.disabled = !hasApi || !requestBodyEl.value.length;
  }

  function setResponseStats(code, durationMs, sizeBytes) {
    statusCodeEl.textContent = code != null && code !== '—' ? String(code) : '—';
    statusCodeEl.className = 'bt-stat bt-stat-status ' + statusStatClass(code);
    durationValEl.textContent = durationMs != null ? `${durationMs} ms` : '—';
    responseSizeValEl.textContent = formatBytes(sizeBytes);
    copyBtn.disabled = !lastResponseText;
  }

  function statusStatClass(code) {
    if (code == null || code === '—' || code === '…' || code === 'Failed') return 'bt-stat-idle';
    const num = Number(code);
    if (Number.isNaN(num) || num === 0) return 'bt-stat-err';
    if (num >= 200 && num < 300) return 'bt-stat-ok';
    if (num >= 400) return 'bt-stat-err';
    return 'bt-stat-warn';
  }

  function setResponseBody(text, { error = false, loading = false, highlight = true } = {}) {
    lastResponseText = text || '';
    responseBodyEl.classList.toggle('bt-error', error);
    responseBodyEl.classList.toggle('bt-loading', loading);

    if (loading) {
      responseBodyEl.innerHTML = escapeHtml(text || 'Waiting for response…');
      copyBtn.disabled = true;
      return;
    }

    if (!text) {
      responseBodyEl.innerHTML = '<span class="bt-empty-state">Send a request to see the decrypted, beautified JSON response here.</span>';
      copyBtn.disabled = true;
      return;
    }

    if (highlight && !error) {
      responseBodyEl.innerHTML = highlightJson(text);
    } else {
      responseBodyEl.textContent = text;
    }
    copyBtn.disabled = false;
  }

  function resetResponse() {
    lastResponseText = '';
    setResponseBody('', { highlight: false });
    setResponseStats('—', null, null);
  }

  function formatBytes(bytes) {
    if (bytes == null) return '—';
    if (bytes < 1024) return `${bytes} B`;
    return `${(bytes / 1024).toFixed(1)} KB`;
  }

  function renderApiList(filter = '') {
    const query = filter.trim().toLowerCase();
    apiList.innerHTML = '';

    if (!operations.length) {
      apiList.innerHTML = `<li class="bt-api-empty">${escapeHtml(NO_APIS)}</li>`;
      return;
    }

    const matches = operations
      .map((op, index) => ({ op, index }))
      .filter(({ op }) => !query || op.publicUrl.toLowerCase().includes(query));

    if (!matches.length) {
      apiList.innerHTML = '<li class="bt-api-empty">No APIs match your filter</li>';
      return;
    }

    matches.forEach(({ op, index }) => {
      const li = document.createElement('li');
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'bt-api-option' + (index === selectedIndex ? ' active' : '');
      btn.textContent = op.publicUrl;
      btn.setAttribute('role', 'option');
      btn.setAttribute('aria-selected', index === selectedIndex ? 'true' : 'false');
      btn.addEventListener('click', () => {
        selectOperation(index);
        closeApiDropdown();
      });
      li.appendChild(btn);
      apiList.appendChild(li);
    });
  }

  function setApiPickerEnabled(enabled, label = PLACEHOLDER) {
    apiTrigger.disabled = !enabled;
    setTriggerLabel(label, true);
    if (!enabled) {
      closeApiDropdown();
    }
  }

  function selectOperation(index) {
    selectedIndex = index;
    const op = operations[index];
    setTriggerLabel(op.publicUrl, false);
    updateEndpoint(op);
    updateSendState();
    resetResponse();
    renderApiList(apiSearch.value);
  }

  function clearSelection() {
    selectedIndex = -1;
    setTriggerLabel(operations.length ? PLACEHOLDER : NO_APIS, true);
    updateEndpoint(null);
    updateSendState();
    resetResponse();
    renderApiList(apiSearch.value);
  }

  async function loadOperations(forceRefresh = false) {
    const environment = environmentSelect.value;
    updateEnvStyle();
    closeApiDropdown();
    setStatus(forceRefresh
      ? `Refreshing ${environment} operation list…`
      : `Loading ${environment} operation list…`);
    reloadBtn.disabled = true;
    sendBtn.disabled = true;
    setApiPickerEnabled(false, 'Loading APIs…');
    setTokenStatus({ loading: true });

    try {
      const refreshParam = forceRefresh ? '&refresh=true' : '';
      const response = await fetch(
        `/api/tester/operations?environment=${encodeURIComponent(environment)}${refreshParam}`,
        { headers: { Accept: 'application/json' } }
      );
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload.message || `Request failed (${response.status})`);
      }

      operations = payload.operations || [];
      setApiPickerEnabled(operations.length > 0, operations.length ? PLACEHOLDER : NO_APIS);
      clearSelection();

      modeBadge.textContent = 'Live';
      modeBadge.classList.add('live');
      apiCountEl.textContent = `${operations.length} API${operations.length === 1 ? '' : 's'}`;

      // The server fetches oauth-token right after the list, so the header token is
      // already warm before any API is selected.
      setTokenStatus(payload.tokenStatus);

      const cacheNote = payload.fromCache
        ? `cached${payload.listExpiresInSeconds != null ? ' · ' + formatDuration(payload.listExpiresInSeconds) + ' left' : ''}`
        : 'fresh from Bajaj';
      setStatus(
        `${payload.description || 'Loaded'} — ${operations.length} endpoints · ${cacheNote} · ${payload.baseUrl}`
      );
    } catch (err) {
      operations = [];
      setApiPickerEnabled(false, NO_APIS);
      clearSelection();
      apiCountEl.textContent = '0 APIs';
      setTokenStatus({ ready: false, error: 'Operation list failed, so no token was fetched' });
      setStatus(err.message || 'Failed to load operation list', true);
    } finally {
      reloadBtn.disabled = false;
      updateSendState();
    }
  }

  function formatDuration(seconds) {
    if (seconds == null) return '';
    const total = Number(seconds);
    if (!Number.isFinite(total) || total <= 0) return '';
    const hours = Math.floor(total / 3600);
    const minutes = Math.floor((total % 3600) / 60);
    if (hours > 0) return `${hours}h ${minutes}m`;
    if (minutes > 0) return `${minutes}m`;
    return `${Math.floor(total)}s`;
  }

  /** Reflects the pre-fetched oauth-token state in the header badge. */
  function setTokenStatus(status) {
    tokenBadge.classList.remove('is-ready', 'is-error', 'is-loading');

    if (!status) {
      tokenBadge.classList.add('is-error');
      tokenTextEl.textContent = 'Token —';
      tokenBadge.title = 'No token available';
      return;
    }

    if (status.loading) {
      tokenBadge.classList.add('is-loading');
      tokenTextEl.textContent = 'Token…';
      tokenBadge.title = 'Fetching oauth-token…';
      return;
    }

    if (status.ready) {
      tokenBadge.classList.add('is-ready');
      const remaining = formatDuration(status.expiresInSeconds);
      tokenTextEl.textContent = remaining ? `Token ready · ${remaining}` : 'Token ready';
      tokenBadge.title =
        `Token ${status.preview} is set as the "token" header on every request.`
        + (remaining ? ` Valid for about ${remaining}.` : '')
        + ' Click to force a refresh.';
      return;
    }

    tokenBadge.classList.add('is-error');
    tokenTextEl.textContent = 'Token failed';
    tokenBadge.title = (status.error || 'Token unavailable') + ' — click to retry.';
  }

  async function refreshToken() {
    if (tokenBusy) return;
    tokenBusy = true;
    setTokenStatus({ loading: true });
    try {
      const response = await fetch(
        `/api/tester/token/refresh?environment=${encodeURIComponent(environmentSelect.value)}`,
        { method: 'POST', headers: { Accept: 'application/json' } }
      );
      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload.message || `Token refresh failed (${response.status})`);
      }
      setTokenStatus(payload);
      showToast('Token refreshed', 'success');
    } catch (err) {
      setTokenStatus({ ready: false, error: err.message });
      showToast(err.message || 'Token refresh failed', 'error');
    } finally {
      tokenBusy = false;
    }
  }

  async function sendRequest() {
    const op = selectedOperation();
    if (!op) {
      setStatus('Select an API first', true);
      return;
    }

    const body = requestBodyEl.value.trim();
    if (!body) {
      setStatus('Request body is required', true);
      return;
    }

    try {
      JSON.parse(body);
    } catch {
      setStatus('Request body must be valid JSON', true);
      showToast('Invalid JSON in request body', 'error');
      return;
    }

    sendBtn.disabled = true;
    sendBtn.classList.add('bt-send-loading');
    reloadBtn.disabled = true;
    setStatus(`Encrypting & sending to ${op.publicUrl}…`);
    setResponseBody('Waiting for response…', { loading: true });
    setResponseStats('…', '…', null);

    try {
      const response = await fetch('/api/tester/invoke', {
        method: 'POST',
        headers: {
          Accept: 'application/json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          environment: environmentSelect.value,
          publicUrl: op.publicUrl,
          encryptionKey: op.encryptionKey,
          encryptionIv: op.encryptionIv,
          requestBody: body,
        }),
      });

      const payload = await response.json();
      if (!response.ok) {
        throw new Error(payload.message || `Invoke failed (${response.status})`);
      }

      if (payload.error) {
        setResponseBody(payload.decryptedBody || payload.error, { error: true, highlight: false });
        setResponseStats(payload.statusCode || 'Failed', payload.durationMs, payload.responseSizeBytes);
      } else {
        setResponseBody(payload.decryptedBody || '(empty response)', { highlight: true });
        setResponseStats(payload.statusCode, payload.durationMs, payload.responseSizeBytes);
      }

      setStatus(`${payload.statusCode} · ${payload.durationMs} ms`);
      showToast(`Response received (${payload.durationMs} ms)`, 'success');
    } catch (err) {
      setResponseBody(err.message || 'Request failed', { error: true, highlight: false });
      setResponseStats('Failed', null, null);
      setStatus(err.message || 'Failed to invoke API', true);
      showToast(err.message || 'Request failed', 'error');
    } finally {
      sendBtn.classList.remove('bt-send-loading');
      reloadBtn.disabled = false;
      updateSendState();
    }
  }

  function formatRequest() {
    const raw = requestBodyEl.value.trim();
    if (!raw) return;
    try {
      requestBodyEl.value = prettyJson(raw);
      updateSendState();
      showToast('Request formatted', 'info');
    } catch {
      showToast('Invalid JSON', 'error');
    }
  }

  function clearRequest() {
    requestBodyEl.value = '';
    updateSendState();
  }

  async function copyResponse() {
    if (!lastResponseText) return;
    try {
      await navigator.clipboard.writeText(lastResponseText);
      showToast('Response copied', 'success');
    } catch {
      showToast('Copy failed', 'error');
    }
  }

  environmentSelect.addEventListener('change', () => loadOperations(false));
  reloadBtn.addEventListener('click', () => loadOperations(true));
  sendBtn.addEventListener('click', sendRequest);
  formatBtn.addEventListener('click', formatRequest);
  clearReqBtn.addEventListener('click', clearRequest);
  copyBtn.addEventListener('click', copyResponse);
  tokenBadge.addEventListener('click', refreshToken);
  requestBodyEl.addEventListener('input', updateSendState);

  apiTrigger.addEventListener('click', (event) => {
    event.stopPropagation();
    toggleApiDropdown();
  });

  apiSearch.addEventListener('input', () => renderApiList(apiSearch.value));
  apiSearch.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      closeApiDropdown();
      apiTrigger.focus();
    }
  });

  document.addEventListener('click', (event) => {
    if (apiDropdownOpen && !apiCombobox.contains(event.target)) {
      closeApiDropdown();
    }
  });

  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && apiDropdownOpen) {
      closeApiDropdown();
    }
  });

  requestBodyEl.addEventListener('keydown', (event) => {
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      if (!sendBtn.disabled) {
        sendRequest();
      }
    }
  });

  updateEnvStyle();
  loadOperations();
})();