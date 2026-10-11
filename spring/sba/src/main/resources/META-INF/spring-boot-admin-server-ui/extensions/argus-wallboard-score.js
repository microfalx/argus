/*
 * Spring Boot Admin UI extension: overlays a Health pill onto the Wallboard hexagons and
 * onto the Applications list (service-level aggregate in the group title, per-instance value
 * next to each instance row), plus a "Health" action on each instance row that opens a dialog
 * with the instance health report (full report and worst items). Loaded automatically by SBA
 * because it lives under META-INF/spring-boot-admin-server-ui/extensions/ (see UiExtensionsScanner).
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

  function extractReport(instance) {
    var details = instance && instance.statusInfo && instance.statusInfo.details;
    var health = details && details.health;
    var report = health && health.details && health.details.reportSummaryText;
    return typeof report === 'string' && report.trim().length > 0 ? report : null;
  }

  function extractReportHtml(instance) {
    var details = instance && instance.statusInfo && instance.statusInfo.details;
    var health = details && details.health;
    var report = health && health.details && health.details.reportHtml;
    return typeof report === 'string' && report.trim().length > 0 ? report : null;
  }

  function extractSummaryHtml(instance) {
    var details = instance && instance.statusInfo && instance.statusInfo.details;
    var health = details && details.health;
    var report = health && health.details && health.details.reportSummaryHtml;
    return typeof report === 'string' && report.trim().length > 0 ? report : null;
  }

  // Native title tooltip (a positioned popup would be clipped by the wallboard hexagons).
  // For an aggregate, names the instance the report comes from.
  function tooltip(instance, named) {
    var report = extractReport(instance);
    if (!report) return null;
    var name = (instance.registration && instance.registration.serviceUrl) || instance.id;
    return named ? 'Lowest score: ' + name + '\n\n' + report : report;
  }

  function computeStats(application) {
    var instances = application.instances || [];
    var scores = instances.map(extractScore).filter(function (score) {
      return score !== null;
    });
    if (scores.length === 0) return null;
    // The tooltip shows the report of the worst (lowest-scoring) instance - the one that
    // determines the displayed score.
    var worst = null;
    instances.forEach(function (instance) {
      var score = extractScore(instance);
      if (score !== null && (worst === null || score < extractScore(worst))) worst = instance;
    });
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
      report: tooltip(worst, scores.length > 1),
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
    return variant + '|' + base + '|' + (stats.report || '');
  }

  function buildBadge(stats, variant) {
    var badge = document.createElement('p');
    badge.className = 'argus-health-badge argus-health-badge--' + variant + ' is-muted';
    if (stats.report) badge.title = stats.report;

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

  // Wallboard hexagons are SVG <g class="hex"> elements whose HTML body sits inside a
  // <foreignObject>; browsers don't show HTML title tooltips there, so the tooltip is an
  // SVG <title> child of the hexagon instead (which also covers the whole hexagon). It is
  // appended last so Vue's own insertions, which anchor on its own nodes, leave it alone.
  function upsertSvgTitle(hex, text) {
    if (!hex) return;
    var existing = hex.querySelector(':scope > title.argus-health-title');
    if (!text) {
      if (existing) existing.remove();
      return;
    }
    if (existing) {
      if (existing.textContent !== text) existing.textContent = text;
      return;
    }
    var title = document.createElementNS('http://www.w3.org/2000/svg', 'title');
    title.setAttribute('class', 'argus-health-title');
    title.textContent = text;
    hex.appendChild(title);
  }

  function applyWallboardBadges(applications) {
    var wallboard = document.querySelector('.wallboard');
    if (!wallboard) return;

    var statsByName = collectStats(applications);

    wallboard.querySelectorAll('.application__name').forEach(function (nameEl) {
      var body = nameEl.closest('.hex__body');
      if (!body) return;

      var stats = statsByName[nameEl.textContent];
      upsertSvgTitle(body.closest('g.hex'), stats && stats.report);

      upsertBadge(body.querySelector('.argus-health-badge'), stats, 'wallboard', function (badge) {
        var instancesEl = body.querySelector('.application__instances');
        if (instancesEl && instancesEl.parentNode) {
          instancesEl.parentNode.insertBefore(badge, instancesEl.nextSibling);
        } else {
          body.appendChild(badge);
        }
      });
    });
  }

  // Latest application list from the store; the report dialog reads it when opened so it
  // always shows current data, not what was known when the button was created.
  var currentApplications = [];

  function closeReportDialog() {
    var existing = document.querySelector('.argus-health-modal');
    if (existing) existing.remove();
    document.removeEventListener('keydown', onDialogKeyDown, true);
  }

  function onDialogKeyDown(event) {
    if (event.key === 'Escape') {
      event.stopPropagation();
      closeReportDialog();
    }
  }

  function appendReport(section, title, html) {
    if (!html) return;
    var heading = document.createElement('h5');
    heading.textContent = title;
    section.appendChild(heading);
    var report = document.createElement('div');
    report.className = 'argus-health-report';
    report.innerHTML = html;
    section.appendChild(report);
  }

  function findInstance(instanceId) {
    for (var i = 0; i < currentApplications.length; i++) {
      var application = currentApplications[i];
      var instances = application.instances || [];
      for (var j = 0; j < instances.length; j++) {
        if (instances[j].id === instanceId) return { application: application, instance: instances[j] };
      }
    }
    return null;
  }

  // Shows the full health report (reportHtml) of an instance followed by its worst items
  // (reportSummaryHtml). Plain DOM instead of sba-modal: SBA's components can't be mounted
  // from a non-Vue extension, so the look of sba-modal is reproduced in the CSS file.
  function openReportDialog(instanceId) {
    closeReportDialog();

    var found = findInstance(instanceId);
    var instance = found && found.instance;

    var overlay = document.createElement('div');
    overlay.className = 'argus-health-modal';
    overlay.addEventListener('click', function (event) {
      if (event.target === overlay) closeReportDialog();
    });

    var panel = document.createElement('div');
    panel.className = 'argus-health-modal__panel';
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'true');
    overlay.appendChild(panel);

    var header = document.createElement('div');
    header.className = 'argus-health-modal__header';
    var title = document.createElement('h3');
    title.textContent = 'Health Report' + (found ? ' - ' + found.application.name : '');
    header.appendChild(title);
    var close = document.createElement('button');
    close.type = 'button';
    close.setAttribute('aria-label', 'Close');
    close.innerHTML = '<span aria-hidden="true">&times;</span>';
    close.addEventListener('click', closeReportDialog);
    header.appendChild(close);
    panel.appendChild(header);

    var body = document.createElement('div');
    body.className = 'argus-health-modal__body';
    if (!instance || extractReportHtml(instance) === null) {
      body.textContent = 'No health report available for this instance.';
    } else {
      var section = document.createElement('section');
      section.className = 'argus-health-modal__instance';

      var heading = document.createElement('h4');
      var label = document.createElement('span');
      label.textContent = (instance.registration && instance.registration.serviceUrl) || instance.id;
      heading.appendChild(label);
      var score = extractScore(instance);
      if (score !== null) {
        var pill = document.createElement('span');
        pill.className = 'argus-health-pill ' + severityClass(score);
        pill.textContent = format(score);
        heading.appendChild(pill);
      }
      section.appendChild(heading);

      // Both rendered as HTML by Health on the monitored application: the full report
      // (getReport) and the summary of its worst items (getWorstItemsReport).
      appendReport(section, 'Full Report', extractReportHtml(instance));
      appendReport(section, 'Worst Items', extractSummaryHtml(instance));

      body.appendChild(section);
    }
    panel.appendChild(body);

    document.body.appendChild(overlay);
    document.addEventListener('keydown', onDialogKeyDown, true);
    close.focus();
  }

  // Font Awesome "heart-pulse" (solid), to match the icon-only buttons next to it.
  var HEALTH_ICON_VIEW_BOX = '0 0 512 512';
  var HEALTH_ICON_PATH =
    'M228.3 469.1L47.6 300.4c-4.2-3.9-8.2-8.1-11.9-12.4l87 0c22.6 0 43-13.6 51.7-34.5l10-23.9 49.4 109.5c3.8 8.5 ' +
    '12.1 14 21.4 14.1s17.8-5 22-13.3L320 253.7l1.7 3.4c9.5 19 28.9 31 50.1 31l104.5 0c-3.7 4.3-7.7 8.5-11.9 ' +
    '12.4L283.7 469.1c-7.5 7-17.4 10.9-27.7 10.9s-20.2-3.9-27.7-10.9zM503.7 240l-132 0c-3 0-5.8-1.7-7.2-4.4l-23.2' +
    '-46.3c-4.1-8.1-12.4-13.3-21.5-13.3s-17.4 5.1-21.5 13.3l-41.4 82.8L205.9 158.2c-3.9-8.7-12.7-14.3-22.2-14.1s-18.1 ' +
    '5.9-21.8 14.8l-31.8 76.3c-1.2 3-4.2 4.9-7.4 4.9L16 240c-2.6 0-5 .4-7.3 1.1C3 225.2 0 208.2 0 190.9l0-5.8c0-69.9 ' +
    '50.5-129.5 119.4-141C165 36.5 211.4 51.4 244 84l12 12 12-12c32.6-32.6 79-47.5 124.6-39.9C461.5 55.6 512 115.2 ' +
    '512 185.1l0 5.8c0 16.9-2.8 33.5-8.3 49.1z';

  // Turns a deep clone of an existing button's Font Awesome <svg> into the health icon, so
  // it keeps the same size/alignment classes; falls back to a standalone <svg>.
  function healthIcon(templateSvg) {
    var svg;
    if (templateSvg) {
      svg = templateSvg.cloneNode(false);
      svg.setAttribute('class', (svg.getAttribute('class') || '').replace(/\bfa-(?!fw\b|xs\b|sm\b|lg\b|2xs\b)[\w-]+/g, '').trim() + ' fa-heart-pulse');
      svg.setAttribute('data-icon', 'heart-pulse');
    } else {
      svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('class', 'svg-inline--fa fa-heart-pulse');
      svg.setAttribute('aria-hidden', 'true');
    }
    svg.setAttribute('viewBox', HEALTH_ICON_VIEW_BOX);
    var path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('fill', 'currentColor');
    path.setAttribute('d', HEALTH_ICON_PATH);
    svg.appendChild(path);
    return svg;
  }

  // Adds a "Health" button next to the instance's own actions (journal, notifications,
  // unregister, ...). It is a clone of an existing button so it carries the same classes
  // and Vue scoped-style attributes, and is appended last so Vue's own patching, which
  // anchors on its own nodes, leaves it alone.
  function upsertHealthButton(group, instanceId, show) {
    if (!group) return;
    var existing = group.querySelector(':scope > .argus-health-button');
    if (!show) {
      if (existing) existing.remove();
      return;
    }
    if (existing) {
      existing.dataset.instance = instanceId;
      return;
    }
    var template = group.querySelector('button');
    if (!template) return;

    var button = template.cloneNode(false);
    button.removeAttribute('id');
    button.classList.remove('is-danger', 'btn-unregister', 'btn-shutdown');
    button.classList.add('argus-health-button');
    button.type = 'button';
    button.title = 'Health report';
    button.setAttribute('aria-label', 'Health report');
    button.appendChild(healthIcon(template.querySelector('svg')));
    button.dataset.instance = instanceId;
    button.addEventListener('click', function (event) {
      // The instance row navigates to the instance details on click.
      event.preventDefault();
      event.stopPropagation();
      openReportDialog(button.dataset.instance);
    });
    group.appendChild(button);
  }

  function applyApplicationsBadges(applications) {
    if (location.pathname.indexOf('applications') === -1) return;

    var statsByName = collectStats(applications);
    var statsByInstanceId = {};
    var hasReportByInstanceId = {};
    applications.forEach(function (application) {
      (application.instances || []).forEach(function (instance) {
        if (extractReportHtml(instance) !== null) hasReportByInstanceId[instance.id] = true;
        var score = extractScore(instance);
        if (score !== null) {
          statsByInstanceId[instance.id] = { min: score, avg: score, max: score, count: 1, report: tooltip(instance, false) };
        }
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

    // Instance-level: the same "Health [pill]" badge, single value, no avg/max, plus the
    // "Health" report button among the instance's actions.
    document.querySelectorAll('li[data-testid]').forEach(function (li) {
      var instanceId = li.dataset.testid;
      var actions = li.querySelector('.application-list-item__header__actions');
      upsertHealthButton(actions, instanceId, hasReportByInstanceId[instanceId] === true);

      var info = li.querySelector('.instance-item-information');
      if (!info) return;

      var stats = statsByInstanceId[instanceId] || null;
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

    currentApplications = (store.applications && store.applications.value) || [];
    var scheduled = false;

    function schedule() {
      if (scheduled) return;
      scheduled = true;
      requestAnimationFrame(function () {
        scheduled = false;
        applyWallboardBadges(currentApplications);
        applyApplicationsBadges(currentApplications);
      });
    }

    if (store.applicationStore) {
      store.applicationStore.addEventListener('connected', schedule);
      store.applicationStore.addEventListener('changed', function (applications) {
        currentApplications = applications || [];
        schedule();
      });
      store.applicationStore.addEventListener('removed', schedule);
    }

    new MutationObserver(schedule).observe(document.body, { childList: true, subtree: true });

    schedule();
  }

  globalThis.SBA.use({ install: install });
})();
