/*
 * Spring Boot Admin UI extension: adds a "Health" tab to the instance details page (in the
 * Insights group, after Scheduled Tasks) that embeds the application-specific dashboard from
 * HealthHealthIndicator's "reportUrl" detail in an iframe.
 *
 * Unlike the wallboard/applications badge overlay, this uses SBA's official extension point
 * (viewRegistry.addView) to register a real nested route under "instances", exactly like the
 * built-in beans/caches/scheduledtasks tabs do - no DOM patching involved. "insights" is the
 * group's raw string value (SBA's internal ViewGroup enum isn't exposed on the global SBA
 * object, only its values are, e.g. VIEW_GROUP.INSIGHTS === "insights").
 */
(function () {
  'use strict';

  function reportUrl(instance) {
    var details = instance && instance.statusInfo && instance.statusInfo.details;
    var health = details && details.health;
    var url = health && health.details && health.details.reportUrl;
    return typeof url === 'string' && url.length > 0 ? url : null;
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
      var url = reportUrl(this.instance);

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
            h('div', { class: 'external-view', style: 'height: 80vh;' }, [h('iframe', { src: url })]),
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
          return reportUrl(context.instance) !== null;
        },
      });
    },
  });
})();
