(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        var toggle = document.getElementById('sidebarToggle');
        var wrapper = document.getElementById('wrapper');
        var sidebar = document.getElementById('sidebar-wrapper');
        if (toggle && wrapper && sidebar) {
            toggle.addEventListener('click', function () {
                if (window.innerWidth < 992) {
                    wrapper.classList.toggle('sidebar-open');
                } else {
                    wrapper.classList.toggle('toggled');
                }
            });
            document.addEventListener('click', function (event) {
                if (window.innerWidth < 992 && wrapper.classList.contains('sidebar-open')
                    && !sidebar.contains(event.target) && !toggle.contains(event.target)) {
                    wrapper.classList.remove('sidebar-open');
                }
            });
        }

        var alerts = document.querySelectorAll('.alert-dismissible');
        alerts.forEach(function (el) {
            window.setTimeout(function () {
                var close = el.querySelector('.btn-close');
                if (close) close.click();
            }, 5000);
        });

        var confirms = document.querySelectorAll('[data-confirm]');
        confirms.forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                if (!window.confirm(btn.getAttribute('data-confirm'))) {
                    e.preventDefault();
                }
            });
        });
    });
})();
