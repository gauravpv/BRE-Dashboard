(() => {
    'use strict';

    const pageCleanups = [];
    const reportPageCache = new Map();
    const reportPageScripts = new Map();
    let navigationController;
    // When the numbers currently on screen were fetched from the server.
    let currentDataAt = Date.now();

    function reportPageKey(url) {
        const pageUrl = new URL(url, window.location.href);
        if (!/^\/transactions\/(hourly|response-time|channels)$/.test(pageUrl.pathname)) {
            return null;
        }
        pageUrl.searchParams.delete('fragment');
        pageUrl.searchParams.sort();
        return pageUrl.pathname + pageUrl.search;
    }

    function cacheCurrentPage(url = window.location.href, scriptNodes = null) {
        const pageUrl = new URL(url, window.location.href);
        const key = reportPageKey(pageUrl);
        if (!key) {
            return;
        }

        const currentScripts = scriptNodes || [...document.body.querySelectorAll('script')];
        const savedScripts = currentScripts.length
            ? currentScripts.map(script => script.cloneNode(true))
            : reportPageScripts.get(pageUrl.pathname) || [];
        if (savedScripts.length) {
            reportPageScripts.set(pageUrl.pathname, savedScripts.map(script => script.cloneNode(true)));
        }

        const bodyNodes = [...document.body.childNodes]
            .filter(node => !(node instanceof HTMLScriptElement))
            .map(node => {
                const clone = node.cloneNode(true);
                // Store the target value of numbers that are still animating, not the in-between frame.
                clone.querySelectorAll?.('[data-rt-final]').forEach(el => {
                    el.textContent = el.dataset.rtFinal;
                    delete el.dataset.rtFinal;
                    el.classList.remove('rt-changed--up', 'rt-changed--down');
                });
                return clone;
            });
        const styleNodes = [...document.head.querySelectorAll('[data-bre-page-style]')]
            .map(style => style.cloneNode(true));

        reportPageCache.set(key, {
            savedAt: currentDataAt,
            title: document.title,
            bodyClass: document.body.className,
            bodyNodes,
            styleNodes,
            scripts: savedScripts.map(script => script.cloneNode(true)),
            scrollY: window.scrollY
        });

        if (reportPageCache.size > 6) {
            reportPageCache.delete(reportPageCache.keys().next().value);
        }
    }

    function freshCacheEntry(key) {
        const entry = key ? reportPageCache.get(key) : null;
        if (!entry) {
            return null;
        }
        const maxAgeMs = Math.max(15, Number(window.breDashboardTransactionRefreshSeconds) || 30) * 1000;
        if (Date.now() - entry.savedAt >= maxAgeMs) {
            reportPageCache.delete(key);
            return null;
        }
        return entry;
    }

    function documentFromCache(entry) {
        const cachedDocument = document.implementation.createHTMLDocument(entry.title);
        cachedDocument.body.className = entry.bodyClass;
        entry.styleNodes.forEach(style => cachedDocument.head.appendChild(style.cloneNode(true)));
        entry.bodyNodes.forEach(node => cachedDocument.body.appendChild(node.cloneNode(true)));
        return cachedDocument;
    }

    function isPlainLeftClick(event, link) {
        return event.button === 0
            && !event.metaKey
            && !event.ctrlKey
            && !event.shiftKey
            && !event.altKey
            && !link.target
            && !link.hasAttribute('download');
    }

    function loadPageStyle(source) {
        const style = source.cloneNode(true);
        if (style.tagName !== 'LINK') {
            document.head.appendChild(style);
            return Promise.resolve(style);
        }
        const loaded = new Promise(resolve => {
            style.addEventListener('load', () => resolve(style), { once: true });
            style.addEventListener('error', () => resolve(style), { once: true });
        });
        document.head.appendChild(style);
        return loaded;
    }

    async function replacePageStyles(freshDocument) {
        const oldStyles = [...document.head.querySelectorAll('[data-bre-page-style]')];
        const newStyles = [...freshDocument.head.querySelectorAll('[data-bre-page-style]')];
        await Promise.all(newStyles.map(loadPageStyle));
        oldStyles.forEach(style => style.remove());
    }

    function scopedExecution(run) {
        const controller = new AbortController();
        const timers = [];
        const originalAddEventListener = EventTarget.prototype.addEventListener;
        const originalSetInterval = window.setInterval;
        const originalSetTimeout = window.setTimeout;

        EventTarget.prototype.addEventListener = function (type, listener, options) {
            const normalized = typeof options === 'boolean' ? { capture: options } : { ...(options || {}) };
            if (!normalized.signal) normalized.signal = controller.signal;
            return originalAddEventListener.call(this, type, listener, normalized);
        };
        window.setInterval = function (...args) {
            const id = originalSetInterval.apply(this, args);
            timers.push(['interval', id]);
            return id;
        };
        window.setTimeout = function (...args) {
            const id = originalSetTimeout.apply(this, args);
            timers.push(['timeout', id]);
            return id;
        };

        try {
            return run();
        } finally {
            EventTarget.prototype.addEventListener = originalAddEventListener;
            window.setInterval = originalSetInterval;
            window.setTimeout = originalSetTimeout;
            pageCleanups.push(() => {
                controller.abort();
                timers.forEach(([kind, id]) => kind === 'interval'
                    ? window.clearInterval(id)
                    : window.clearTimeout(id));
            });
        }
    }

    function runInlineScript(source) {
        const declaredFunctions = [...source.textContent.matchAll(/(?:async\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/g)]
            .map(match => match[1]);
        const globals = [...new Set(declaredFunctions)].map(name =>
            `window[${JSON.stringify(name)}] = ${name};`).join('\n');
        const script = document.createElement('script');
        script.textContent = `(function () {\n${source.textContent}\n${globals}\n}).call(window);`;
        scopedExecution(() => document.body.appendChild(script));
        script.remove();
    }

    async function runExternalScript(source) {
        const script = document.createElement('script');
        [...source.attributes].forEach(attribute => script.setAttribute(attribute.name, attribute.value));
        const response = await fetch(source.src, { credentials: 'same-origin', cache: 'force-cache' });
        if (!response.ok) throw new Error(`Unable to load page script: ${source.src}`);
        script.removeAttribute('src');
        script.textContent = await response.text();
        scopedExecution(() => document.body.appendChild(script));
        script.remove();
    }

    async function runPageScripts(sources) {
        for (const source of sources) {
            if (source.src) {
                await runExternalScript(source);
            } else {
                runInlineScript(source);
            }
        }
        document.dispatchEvent(new Event('DOMContentLoaded'));
        window.dispatchEvent(new CustomEvent('bre:page:navigated'));
    }

    function cleanupCurrentPage() {
        while (pageCleanups.length) pageCleanups.pop()();
    }

    async function navigate(url, addHistory, state) {
        cacheCurrentPage();
        navigationController?.abort();
        const requestController = new AbortController();
        navigationController = requestController;
        document.body.setAttribute('aria-busy', 'true');

        try {
            const targetKey = reportPageKey(url);
            const cachedPage = freshCacheEntry(targetKey);
            if (cachedPage) {
                const ageMs = Date.now() - cachedPage.savedAt;
                const cachedDocument = documentFromCache(cachedPage);
                const scripts = cachedPage.scripts.map(script => script.cloneNode(true));
                window.dispatchEvent(new CustomEvent('bre:page:before-navigation'));
                cleanupCurrentPage();
                await replacePageStyles(cachedDocument);
                document.title = cachedDocument.title;
                document.body.className = cachedDocument.body.className;
                document.body.replaceChildren(...[...cachedDocument.body.childNodes]
                    .map(node => document.importNode(node, true)));

                if (addHistory) {
                    window.history.replaceState({ ...window.history.state, scrollY: window.scrollY }, '', window.location.href);
                    window.history.pushState({}, '', url);
                }
                await runPageScripts(scripts);
                currentDataAt = cachedPage.savedAt;
                window.dispatchEvent(new CustomEvent('bre:page:cache-restored', { detail: { ageMs } }));
                window.scrollTo(0, state?.scrollY ?? cachedPage.scrollY ?? 0);
                return;
            }

            const response = await fetch(url, {
                credentials: 'same-origin',
                cache: 'no-store',
                headers: { 'X-Requested-With': 'XMLHttpRequest' },
                signal: requestController.signal
            });
            const responseUrl = response.url || url;
            const freshDocument = response.ok
                ? new DOMParser().parseFromString(await response.text(), 'text/html')
                : null;

            if (!freshDocument?.querySelector('.bre-dashboard-sidebar')) {
                window.location.assign(responseUrl);
                return;
            }

            const scripts = [...freshDocument.body.querySelectorAll('script')];
            const savedScripts = scripts.map(script => script.cloneNode(true));
            scripts.forEach(script => script.remove());
            window.dispatchEvent(new CustomEvent('bre:page:before-navigation'));
            cleanupCurrentPage();
            await replacePageStyles(freshDocument);
            document.title = freshDocument.title;
            document.body.className = freshDocument.body.className;
            document.body.replaceChildren(...[...freshDocument.body.childNodes]
                .map(node => document.importNode(node, true)));

            if (addHistory) {
                window.history.replaceState({ ...window.history.state, scrollY: window.scrollY }, '', window.location.href);
                window.history.pushState({}, '', url);
            }
            await runPageScripts(scripts);
            currentDataAt = Date.now();
            cacheCurrentPage(url, savedScripts);
            window.scrollTo(0, state?.scrollY || 0);
        } catch (error) {
            if (error.name !== 'AbortError') {
                console.warn('Soft navigation failed; using standard navigation', error);
                window.location.assign(url);
            }
        } finally {
            if (navigationController === requestController) {
                document.body.removeAttribute('aria-busy');
            }
        }
    }

    window.addEventListener('bre:report:updated', () => {
        currentDataAt = Date.now();
        cacheCurrentPage();
    });

    document.addEventListener('click', event => {
        const link = event.target.closest('.bre-dashboard-sidebar a[href]');
        if (!link || !isPlainLeftClick(event, link)) return;
        const destination = new URL(link.href, window.location.href);
        if (destination.origin !== window.location.origin) return;
        event.preventDefault();
        navigate(destination.href, true);
    });

    window.addEventListener('popstate', event => {
        navigate(window.location.href, false, event.state);
    });
})();