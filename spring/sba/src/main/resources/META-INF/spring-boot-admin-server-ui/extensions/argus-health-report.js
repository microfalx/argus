/*
 * Spring Boot Admin UI extension: adds a "Health" tab to the instance details page (in the
 * Insights group, after Scheduled Tasks) that embeds the application-specific dashboard from
 * HealthHealthIndicator's "reportPath" detail in an iframe, resolved against the instance's
 * own registration.serviceUrl (the base URL SBA's client told SBA to use for this app) rather
 * than the absolute "reportUrl" the monitored app computed for itself from the inbound health
 * check request - that can reflect an internal/proxy-side address instead of what a browser
 * should actually use.
 *
 * Unlike the wallboard/applications badge overlay, this uses SBA's official extension point
 * (viewRegistry.addView) to register a real nested route under "instances", exactly like the
 * built-in beans/caches/scheduledtasks tabs do - no DOM patching involved. "insights" is the
 * group's raw string value (SBA's internal ViewGroup enum isn't exposed on the global SBA
 * object, only its values are, e.g. VIEW_GROUP.INSIGHTS === "insights").
 */
(function () {
  'use strict';

  function reportPath(instance) {
    var details = instance && instance.statusInfo && instance.statusInfo.details;
    var health = details && details.health;
    var path = health && health.details && health.details.reportPath;
    return typeof path === 'string' && path.length > 0 ? path : null;
  }

  function buildReportUrl(instance) {
    var path = reportPath(instance);
    var base = instance && instance.registration && instance.registration.serviceUrl;
    if (!path || !base) return null;
    return base.replace(/\/+$/, '') + '/' + path.replace(/^\/+/, '');
  }

  var HealthReportView = {
    props: {
      instance: {
        type: Object,
        required: true,
      },
    },
    inheritAttrs: false,
    render: function () {
      var h = globalThis.Vue.h;
      var url = buildReportUrl(this.instance);

      // Same outer <section><div class="flex-1 px-2 md:px-6 py-6"> every instance tab is
      // wrapped in (see sba-instance-section.vue) - reproduced by hand since it isn't
      // globally registered and so isn't resolvable by tag name from a plain extension.
      var content;
      if (!url) {
        content = h('div', { class: 'message is-warning' }, [
          h('div', { class: 'message-body' }, 'No health report available for this instance.'),
        ]);
      } else {
        // Same "white card with a titled header" look sba-panel gives Metrics/Beans/etc,
        // reproduced by hand for the same reason.
        content = h('div', { class: 'shadow-sm border rounded break-inside-avoid mb-4' }, [
          h(
            'header',
            { class: 'rounded-t flex justify-between px-4 pt-5 pb-5 border-b sm:px-6 items-center bg-white' },
            [h('h3', { class: 'text-lg leading-6 font-medium text-gray-900 flex-1' }, 'Health Report')],
          ),
          h('div', { class: 'rounded-b px-4 py-3 bg-white' }, [
            h('div', { class: 'external-view', style: 'height: 75vh;' }, [h('iframe', { src: url })]),
          ]),
        ]);
      }

      return h('section', { class: 'relative' }, [h('div', { class: 'flex-1 px-2 md:px-6 py-6' }, [content])]);
    },
  };

  globalThis.SBA.use({
    install: function (context) {
      context.viewRegistry.addView({
        name: 'instances/health-report',
        parent: 'instances',
        path: 'health-report',
        label: 'Health Report',
        group: 'insights',
        order: 951,
        component: HealthReportView,
        isEnabled: function (context) {
          return buildReportUrl(context.instance) !== null;
        },
      });
    },
  });
})();
