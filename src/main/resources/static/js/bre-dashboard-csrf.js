(function () {
    'use strict';

    function csrfToken() {
        var match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/);
        return match ? decodeURIComponent(match[1]) : '';
    }

    var originalFetch = window.fetch;
    window.fetch = function (input, init) {
        init = init || {};
        var method = String(init.method || 'GET').toUpperCase();
        var headers = new Headers(init.headers || {});
        if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
            var token = csrfToken();
            if (token && !headers.has('X-XSRF-TOKEN')) {
                headers.set('X-XSRF-TOKEN', token);
            }
        }
        init.headers = headers;
        if (!init.credentials) {
            init.credentials = 'same-origin';
        }
        return originalFetch.call(this, input, init);
    };
})();
