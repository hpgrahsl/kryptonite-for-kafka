(function () {
  'use strict';

  /**
   * Determine whether the current page is inside a versioned path, e.g.
   *   /kryptonite-for-kafka/v0.7/...
   *   /kryptonite-for-kafka/dev/...
   *
   * Returns { siteRoot, currentVersion } or null if not in a versioned path.
   */
  function parseSiteInfo() {
    var path = window.location.pathname;
    // Match /base/vX.Y/... or /base/dev/... (base may be empty for root deployments)
    var m = path.match(/^((?:\/[^/]+)?\/)((v\d+\.\d+|dev)\/)/);
    if (!m) return null;
    return {
      siteRoot: window.location.origin + m[1],
      currentVersion: m[2].replace(/\/$/, '')
    };
  }

  function buildPicker(versions, info) {
    var select = document.createElement('select');
    select.className = 'k4k-version-picker';
    select.setAttribute('aria-label', 'Select docs version');
    select.setAttribute('title', 'Select docs version');

    versions.forEach(function (v) {
      var opt = document.createElement('option');
      opt.value = info.siteRoot + v.version + '/';
      opt.textContent = v.title || v.version;
      if (v.version === info.currentVersion) {
        opt.selected = true;
      }
      select.appendChild(opt);
    });

    select.addEventListener('change', function () {
      window.location.href = this.value;
    });

    return select;
  }

  async function initVersionPicker() {
    var info = parseSiteInfo();
    if (!info) return;

    var versions;
    try {
      var resp = await fetch(info.siteRoot + 'versions.json', { cache: 'no-cache' });
      if (!resp.ok) return;
      versions = await resp.json();
    } catch (e) {
      return;
    }

    if (!versions || !versions.length) return;

    var select = buildPicker(versions, info);
    var wrapper = document.createElement('div');
    wrapper.className = 'k4k-version-picker-wrapper';
    wrapper.appendChild(select);

    // Inject into the Material/Zensical header bar
    var header = document.querySelector('.md-header__inner');
    if (header) {
      header.appendChild(wrapper);
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initVersionPicker);
  } else {
    initVersionPicker();
  }
})();
