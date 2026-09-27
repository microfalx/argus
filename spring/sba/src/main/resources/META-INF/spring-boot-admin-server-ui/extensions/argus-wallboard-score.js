/*
 * Spring Boot Admin UI extension: overlays a Health pill onto the Wallboard hexagons and
 * onto the Applications list (service-level aggregate in the group title, per-instance value
 * next to each instance row). Loaded automatically by SBA because it lives under
 * META-INF/spring-boot-admin-server-ui/extensions/ (see UiExtensionsScanner).
 *
 * Both views are Vue-rendered components we do not own, so instead of replacing them we
 * overlay badges and keep them in sync with two update sources: the application store's
 * "changed" event (real data updates) and a MutationObserver (catches the DOM being
 * replaced on re-render/resize/expand-collapse). Re-applying is idempotent - a badge is
 * only rebuilt when its computed values actually change, so the observer loop settles on
 * its own instead of fighting Vue's own re-renders.
 *
 * Applications list note: sba-panel (used for each service's group row) has
 * inheritAttrs: false and only forwards `id` to its outer wrapper div and `class` to an
 * *inner* content div that (a) only exists while the group is expanded and (b) is a
 * sibling of <header>, not an ancestor of it. So the service row can't be found via the
 * "application-group" class passed to <sba-panel> - instead we anchor on the always-present
 * `header h3 button` title element and walk up to the nearest ancestor with an `id`
 * (which sba-panel sets to the application name when grouped by application).
 */
(function () {
  'use strict';

  // Mirrors net.microfalx.argus.api.Health: MIN=1, MAX=10, ERROR=4, WARNING=7.
  var THRESHOLD_WARNING = 7;
  var THRESHOLD_ERROR = 4;

  function extractScore(instance) {
    var details = instance && instance.statusInfo && instance.statusInfo.details;
    // SBA normalizes the actuator "components" tree into "details"; "health" is
    // the indicator key Spring derives from the healthHealthIndicator bean name.
    var health = details && details.health;
    var score = health && health.details && health.details.score;
    return typeof score === 'number' && isFinite(score) ? score : null;
  }

  function computeStats(application) {
    var scores = (application.instances || [])
      .map(extractScore)
      .filter(function (score) {
        return score !== null;
      });
    if (scores.length === 0) return null;
    var sum = scores.reduce(function (a, b) {
      return a + b;
    }, 0);
    return {
      avg: sum / scores.length,
      // The service's health is the worst (lowest) score across its instances,
      // mirroring how Health.getScore() picks the lowest-scoring item within a group.
      min: Math.min.apply(null, scores),
      max: Math.max.apply(null, scores),
      count: scores.length,
    };
  }

  function format(value) {
    return value.toFixed(1);
  }

  function severityClass(score) {
    if (score > THRESHOLD_WARNING) return 'argus-health-green';
    if (score >= THRESHOLD_ERROR) return 'argus-health-yellow';
    return 'argus-health-red';
  }

  function signature(stats, variant) {
    var base = stats.count > 1 ? format(stats.min) + '|' + format(stats.avg) + '|' + format(stats.max) : format(stats.min);
    return variant + '|' + base;
  }

  function buildBadge(stats, variant) {
    var badge = document.createElement('p');
    badge.className = 'argus-health-badge argus-health-badge--' + variant + ' is-muted';

    var label = document.createElement('span');
    label.className = 'argus-health-label';
    label.textContent = 'Health';
    badge.appendChild(label);

    var pill = document.createElement('span');
    pill.className = 'argus-health-pill ' + severityClass(stats.min);
    pill.textContent = format(stats.min);
    badge.appendChild(pill);

    if (stats.count > 1) {
      var extra = document.createElement('span');
      extra.className = 'argus-health-extra';
      extra.textContent = 'avg ' + format(stats.avg) + '  ·  max ' + format(stats.max);
      badge.appendChild(extra);
    }

    return badge;
  }

  function upsertBadge(existing, stats, variant, insertNew) {
    if (!stats) {
      if (existing) existing.remove();
      return;
    }

    var sig = signature(stats, variant);
    if (existing && existing.dataset.sig === sig) return;

    var replacement = buildBadge(stats, variant);
    replacement.dataset.sig = sig;

    if (existing) {
      existing.replaceWith(replacement);
    } else {
      insertNew(replacement);
    }
  }

  function collectStats(applications) {
    var statsByName = {};
    applications.forEach(function (application) {
      var stats = computeStats(application);
      if (stats) statsByName[application.name] = stats;
    });
    return statsByName;
  }

  function applyWallboardBadges(applications) {
    var wallboard = document.querySelector('.wallboard');
    if (!wallboard) return;

    var statsByName = collectStats(applications);

    wallboard.querySelectorAll('.application__name').forEach(function (nameEl) {
      var body = nameEl.closest('.hex__body');
      if (!body) return;

      upsertBadge(body.querySelector('.argus-health-badge'), statsByName[nameEl.textContent], 'wallboard', function (badge) {
        var instancesEl = body.querySelector('.application__instances');
        if (instancesEl && instancesEl.parentNode) {
          instancesEl.parentNode.insertBefore(badge, instancesEl.nextSibling);
        } else {
          body.appendChild(badge);
        }
      });
    });
  }

  function applyApplicationsBadges(applications) {
    if (location.pathname.indexOf('applications') === -1) return;

    var statsByName = collectStats(applications);
    var scoreByInstanceId = {};
    applications.forEach(function (application) {
      (application.instances || []).forEach(function (instance) {
        var score = extractScore(instance);
        if (score !== null) scoreByInstanceId[instance.id] = score;
      });
    });

    // Service-level: one badge per group title, found via the title button and matched
    // back to an application through the nearest ancestor with an id. Only matches (and
    // only shows a badge) when the list is grouped by application - the default, and the
    // only case where the aggregate is unambiguous; a custom metadata group can span
    // several applications and is intentionally left alone.
    document.querySelectorAll('header h3 button').forEach(function (button) {
      var panel = button.closest('[id]');
      if (!panel || !panel.id) return;

      upsertBadge(button.querySelector('.argus-health-badge'), statsByName[panel.id], 'title', function (badge) {
        button.appendChild(badge);
      });
    });

    // Instance-level: the same "Health [pill]" badge, single value, no avg/max.
    document.querySelectorAll('li[data-testid]').forEach(function (li) {
      var info = li.querySelector('.instance-item-information');
      if (!info) return;

      var score = scoreByInstanceId[li.dataset.testid];
      var stats = typeof score === 'number' ? { min: score, avg: score, max: score, count: 1 } : null;
      var next = info.nextElementSibling;
      var existing = next && next.classList.contains('argus-health-badge') ? next : null;

      upsertBadge(existing, stats, 'inline', function (badge) {
        info.insertAdjacentElement('afterend', badge);
      });
    });
  }

  function install(context) {
    var applicationStoreFactory = context.applicationStore;
    var store;
    try {
      store = applicationStoreFactory();
    } catch (e) {
      console.warn('[argus] health badge extension: application store not ready yet', e);
      return;
    }

    var latestApplications = (store.applications && store.applications.value) || [];
    var scheduled = false;

    function schedule() {
      if (scheduled) return;
      scheduled = true;
      requestAnimationFrame(function () {
        scheduled = false;
        applyWallboardBadges(latestApplications);
        applyApplicationsBadges(latestApplications);
      });
    }

    if (store.applicationStore) {
      store.applicationStore.addEventListener('connected', schedule);
      store.applicationStore.addEventListener('changed', function (applications) {
        latestApplications = applications || [];
        schedule();
      });
      store.applicationStore.addEventListener('removed', schedule);
    }

    new MutationObserver(schedule).observe(document.body, { childList: true, subtree: true });

    schedule();
  }

  globalThis.SBA.use({ install: install });
})();
