/*
 * Spring Boot Admin UI extension: overlays a Health pill onto the Wallboard hexagons.
 * Loaded automatically by SBA because it lives under
 * META-INF/spring-boot-admin-server-ui/extensions/ (see UiExtensionsScanner).
 *
 * The built-in Wallboard hexagon is a Vue-rendered component that we do not own, so
 * instead of replacing it we overlay a badge and keep it in sync with two update
 * sources: the application store's "changed" event (real data updates) and a
 * MutationObserver (catches the DOM being replaced on re-render/resize). Re-applying
 * is idempotent - a badge is only rebuilt when its computed values actually change, so
 * the observer loop settles on its own instead of fighting Vue's own re-renders.
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

  function signature(stats) {
    return stats.count > 1
      ? format(stats.min) + '|' + format(stats.avg) + '|' + format(stats.max)
      : format(stats.min);
  }

  function buildBadge(stats) {
    var badge = document.createElement('p');
    badge.className = 'argus-health-badge is-muted';

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

  function applyBadges(applications) {
    var wallboard = document.querySelector('.wallboard');
    if (!wallboard) return;

    var statsByName = {};
    applications.forEach(function (application) {
      var stats = computeStats(application);
      if (stats) statsByName[application.name] = stats;
    });

    wallboard.querySelectorAll('.application__name').forEach(function (nameEl) {
      var body = nameEl.closest('.hex__body');
      if (!body) return;

      var stats = statsByName[nameEl.textContent];
      var badge = body.querySelector('.argus-health-badge');

      if (!stats) {
        if (badge) badge.remove();
        return;
      }

      var sig = signature(stats);
      if (badge && badge.dataset.sig === sig) return;

      var replacement = buildBadge(stats);
      replacement.dataset.sig = sig;

      if (badge) {
        badge.replaceWith(replacement);
      } else {
        var instancesEl = body.querySelector('.application__instances');
        if (instancesEl && instancesEl.parentNode) {
          instancesEl.parentNode.insertBefore(replacement, instancesEl.nextSibling);
        } else {
          body.appendChild(replacement);
        }
      }
    });
  }

  function install(context) {
    var applicationStoreFactory = context.applicationStore;
    var store;
    try {
      store = applicationStoreFactory();
    } catch (e) {
      console.warn('[argus] wallboard health extension: application store not ready yet', e);
      return;
    }

    var latestApplications = (store.applications && store.applications.value) || [];
    var scheduled = false;

    function schedule() {
      if (scheduled) return;
      scheduled = true;
      requestAnimationFrame(function () {
        scheduled = false;
        applyBadges(latestApplications);
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
