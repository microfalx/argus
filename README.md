# Argus

An opinionated self-monitoring & self-diagnostics library for JVM services.

Argus lives inside your application instead of beside it: every service that depends on it becomes able to
score its own health, describe the resources it depends on, capture logging events and issues as they
happen, and render all of that into a single, self-contained HTML report. When the host application is a
Spring Boot service, Argus also exposes the same information through Actuator and, if present, through
Spring Boot Admin.

## Introduction

Most monitoring stacks answer "is it up?" from the outside, by polling an endpoint or scraping metrics into
a time-series database. Argus takes the opposite approach: the process itself continuously assesses its own
condition — memory pressure, GC pauses, disk usage, thread/file-descriptor counts, and anything a service
wants to contribute — and reduces that assessment to a single, well-defined **health score**. The score, the
detail behind it, and the resources it was computed from are always available in-process, without depending
on an external system being reachable.

This makes Argus useful in three situations that external monitoring struggles with:

* **Self-diagnostics** — a service can inspect its own health (and act on it: log, refuse traffic, restart)
  without a round-trip to an external system.
* **Self-healing** — beyond notifying administrators that a service is struggling, a service in a genuinely
  unrecoverable state (score below a configurable floor, `1.2` by default — just above `Health.MIN`) can
  self-terminate rather than keep serving traffic in a broken state, relying on the surrounding orchestration
  engine (Kubernetes, a process supervisor, ...) to restart the instance. This is the same intent behind the
  `Health.Policy.RESTART_IF_NEEDED` item policy.
* **Support/incident response** — a single HTML report, downloadable or emailed on a schedule or on
  degradation, gives a human everything needed to triage an incident, offline, without needing access to a
  monitoring stack.

### Related projects

Argus grew out of its authors' years of experience building, running and supporting JVM services in
production — and out of the recurring need to answer "how healthy is this service, and why?" quickly. Along
the way, several excellent open source projects served as inspiration and deserve credit:

* [Spring Boot Admin](https://github.com/codecentric/spring-boot-admin) (by codecentric) has been the go-to
  administration UI for Spring Boot fleets for years: it discovers instances, polls their Actuator endpoints
  and presents health, metrics, environment, loggers, thread and heap dumps, log files and more in one
  polished web UI, with notifications when an instance changes status.
* [BootUI](https://github.com/jdubois/boot-ui) (by Julien Dubois) is an embedded, local-only developer
  console for Spring Boot and Quarkus that lets developers — and their AI coding agents, via MCP and a CLI —
  inspect beans, properties, databases and other runtime diagnostics while building an application.
* [Arthas](https://github.com/alibaba/arthas) (by Alibaba) is the closest in spirit to Argus: a production
  diagnostic tool that attaches to a running JVM and offers a real-time dashboard, thread and memory
  inspection, method tracing/watching, decompilation, hot-swapping and profiling — all without restarting the
  application. It sits in a family of similar tools: [Bistoury](https://github.com/qunarcorp/bistoury)
  (Qunar's web-based platform built on top of Arthas), [BTrace](https://github.com/btraceio/btrace) (safe
  dynamic tracing scripts) and [jvm-sandbox](https://github.com/alibaba/jvm-sandbox) (a non-invasive AOP
  runtime for building such tools).
* [JavaMelody](https://github.com/javamelody/javamelody) and [Glowroot](https://github.com/glowroot/glowroot)
  are lightweight, embeddable monitoring/APM tools that collect request, SQL and JVM statistics inside the
  application, with their own UI — JavaMelody can even mail periodic PDF reports.

All of these are well worth using, and Argus is designed to work alongside them rather than replace them (the
`argus-spring-sba` module plugs straight into Spring Boot Admin). They are, however, mostly *instruments*:
they show what is happening, or let a skilled engineer dig into it, but leave the conclusion to a human. What
Argus adds to the mix is a *judgment* about the process, not just a view of it:

* **A graded health score instead of raw data or `UP`/`DOWN`.** Actuator health (and therefore Spring Boot
  Admin) is essentially binary per indicator; Arthas, JavaMelody and Glowroot present metrics and traces for
  a person to interpret. Argus computes a 1–10 score from thresholds over JVM, server, service and custom
  metrics, explains which group and item pulled it down, and tracks its trend over time — the same score Argus
  then surfaces inside Spring Boot Admin as badges.
* **Continuous self-assessment, not on-demand investigation.** Arthas-style tools are attached by an
  engineer once something already looks wrong; Spring Boot Admin needs a separate server that can reach the
  instance; BootUI is intentionally limited to local development. Argus runs in production, in-process, all
  the time, with or without Spring Boot, and can act on its own health — including self-terminating when the
  score falls below a floor so the orchestrator restarts the instance.
* **Issues and alerts feed the score.** Problems a service reports about itself (`Issue`) and spikes of
  `WARN`/`ERROR` log events (`Alert`) are first-class inputs to the health score rather than something an
  operator has to correlate by hand.
* **A self-contained, offline HTML report built around the verdict.** Argus renders the health breakdown,
  services, metrics, tasks, logs and environment into a single HTML file that can be downloaded, emailed on a
  schedule *or as soon as the health degrades*, and optionally encrypted — useful for support and incident
  response when nobody has access to a live UI or to the running process.
* **A model of the application's resources.** Argus describes the service, its host and its external
  dependencies (databases, caches, brokers, storage, search) as `Resource`s, each with its own health, so the
  score reflects the whole picture an application depends on.

In short: use BootUI while developing, Spring Boot Admin to administer a fleet, Arthas (or its siblings) to
dig into a live JVM, and Argus to let each service continuously grade, explain and report on its own health —
and surface that verdict in Spring Boot Admin too.

## Scope

Argus is a library, not a standalone service. It is meant to be embedded in a JVM application (with or
without Spring Boot) and covers:

* Computing a **health score** for the current process, aggregated from pluggable contributors (JVM memory,
  GC, file system, services, custom checks, ...).
* Modeling the **resources** an application is made of (its own service instances, servers, and external
  dependencies such as databases or caches) and attaching health to each of them.
* Capturing **issues** (self-reported problems) and **alerts** (aggregated from logging events) so both
  voluntary and observed problems feed into diagnostics and reporting.
* Rendering a **self-contained HTML report** (metrics, health, services, logs, tasks, environment, ...) that
  can be viewed offline and sent by email/notification on a schedule or when things go wrong.
* Integrating with **Spring Boot Actuator** (a `HealthIndicator` and a `resource` endpoint) and
  **Spring Boot Admin** (UI extensions that surface the health score and the HTML report directly in SBA).

It intentionally does *not* try to be a metrics time-series store, a tracing system, or a replacement for
centralized observability — it builds on top of companion libraries (`metrics`, `tracing`, `jvm`) for the
low-level data and focuses on turning that data into a single, actionable health signal and a human-readable
report.

## Core concepts

### Health

[`Health`](api/src/main/java/net/microfalx/argus/api/Health.java) is the central concept of the library: a
rating assigned to something (a resource, a service instance, an entire site) to represent how healthy it
is. A `Health` instance is a tree of named **groups** (e.g. "JVM" → "Memory" → "Eden") whose leaves are
**items**, each with a score between `Health.MIN` (1, completely faulty) and `Health.MAX` (10, completely
healthy):

```
Score:        10 -------- 7 -------- 4 -------- 1
Thresholds:            warning     error
Areas:        healthy    with issues   faulty
```

* The score of a group (and of the `Health` instance itself) is always the score of its **worst** item —
  a single failing item is enough to drag the whole score down, mirroring how an incident actually plays out.
* `Health.Severity` translates a score into `OK` / `LOW` / `MEDIUM` / `HIGH` / `CRITICAL`, and
  `Health.Type` records what the score represents: `INSTANCE` (this process), `RESOURCE` (a resource and
  all its instances), or `SITE` (the whole application).
* `getReport()` renders a human-readable, indented text summary that highlights the group and item
  responsible for the current score — the same text used in Slack/email alerts and the actuator health
  details.
* Once built, a `Health` can be turned `readOnly()` — a defensive snapshot that is safe to hand out (e.g.
  attached to a `Resource`) without risking further mutation.

Scores are not assigned by hand: they are derived from a value and a pair of [`Thresholds`](api/src/main/java/net/microfalx/argus/api/Thresholds.java)
(a warning level and an error level, optionally reversed for metrics where *higher is better*, e.g.
available disk space). `Thresholds.getScore(value)` linearly interpolates between the configured bounds to
produce a score, which is how a raw metric (85% memory used, 350 open file descriptors, ...) becomes a
comparable `Health.Item`.

### HealthContributor and HealthService

A [`HealthContributor`](api/src/main/java/net/microfalx/argus/api/HealthContributor.java) is how a service
plugs into the scoring process: it declares which `Resource.Type` it applies to, contributes items into a
shared `Health` instance when asked (`update(Health)`), and optionally reports its own metrics
(`update(Batch)`). Argus ships several built-in contributors (see
[`core/.../contributor`](core/src/main/java/net/microfalx/argus/core/contributor)):

* `VirtualMachineHealthContributor` — JVM memory pools, GC pause times, thread and file-descriptor counts,
  file system usage.
* `ServerHealthContributor` / `ServiceHealthContributor` — host- and service-level health.
* `AlertHealthContributor` / `IssueHealthContributor` — turn active alerts/issues into health items, so a
  spike in errors or a self-reported issue is reflected in the score, not just in a side channel.

[`HealthService`](api/src/main/java/net/microfalx/argus/api/HealthService.java) is the orchestrator:
it discovers contributors and thresholds from the classpath (and accepts manual registration), scrapes them
on a schedule (`HealthSettings.scrapeInterval`, 10s by default), keeps a rolling trend per resource/group/item
(`HealthSettings.healthInterval`, 5 minutes by default), and is the single place to ask "what is the health
of X" (`getHealth(Resource.Type)`, `getHealth(Health.Type)`) or "what does X look like" (`getResource(Resource.Type)`).

### Resource

[`Resource`](api/src/main/java/net/microfalx/argus/api/Resource.java) is the other core concept: a
description of something an application depends on or is made of. Every resource has a `Type`:

| Type       | Meaning                                            |
|------------|-----------------------------------------------------|
| `SERVICE`  | The application itself (a service replica)          |
| `SERVER`   | The host/VM/container the service runs on           |
| `DATABASE` | An external database dependency                     |
| `CACHE`    | An external cache dependency                         |
| `BROKER`   | A message broker/queue dependency (Kafka, RabbitMQ, SQS, ...) |
| `STORAGE`  | An external object/blob/file storage dependency (S3, ...)     |
| `SEARCH`   | An external search engine dependency (Elasticsearch, Solr, ...) |
| `OTHER`    | Anything else worth tracking                         |

A service **registers its own resources**: `HealthService` always builds and registers a `SERVICE` resource
(itself) and a `SERVER` resource (its host), computed from the same contributors that feed the health score,
and exposes them (via the `registry` library) alongside any `DATABASE`/`CACHE`/`OTHER` resources a service
chooses to register for its external dependencies. Every resource carries a read-only `Health` snapshot and
can nest child **instances** (e.g. a clustered service's individual replicas, or a database's read replicas)
so a single `Resource` can represent both an individual instance and the aggregate of a whole fleet.

### Issues and alerts

Argus distinguishes between two ways a problem can surface:

* An [`Issue`](api/src/main/java/net/microfalx/argus/api/Issue.java) is a **voluntary** declaration — code
  explicitly calls `Issue.create(type, name)...register()` when it detects a problem (a typed category such
  as `SECURITY`, `AVAILABILITY`, `CAPACITY`, `LATENCY`, ...), with a severity, description and free-form
  attributes. Repeated occurrences of the same issue are merged (counted, timestamps widened) rather than
  duplicated.
* An [`Alert`](api/src/main/java/net/microfalx/argus/api/Alert.java) is **derived** — an aggregation built
  indirectly from events the system already produces, primarily logger events (a spike of `WARN`/`ERROR`
  log lines of the same failure type becomes a single alert with an event count).

Both are tracked by their respective services (`IssueService`, `LoggerService`/`NotificationService`) and,
through `AlertHealthContributor`/`IssueHealthContributor`, feed back into the overall health score — so an
unhandled exception or a self-reported capacity problem is visible in the same score as a JVM memory
threshold breach.

## Architecture

| Module                | Artifact                 | Purpose                                                                 |
|------------------------|---------------------------|--------------------------------------------------------------------------|
| `api`                  | `argus-api`               | Public API: `Health`, `Resource`, `Thresholds`, `Issue`, `Alert`, and the `HealthService`/`IssueService`/`LoggerService`/`NotificationService` contracts. No implementation dependencies. |
| `spi`                  | `argus-spi`               | Extension points for pluggable implementations (currently minimal).      |
| `core`                 | `argus-core`               | Default implementations of the API services, plus the built-in health contributors (JVM, server, service, alerts, issues). |
| `logger`               | `argus-logger`             | Captures logging events (Logback-based) and turns them into `LoggerEvent`s/alerts, independent of the logging framework the host application uses. |
| `report`               | `argus-report`             | Builds the self-contained HTML report (Thymeleaf-based) out of pluggable fragments; also schedules and sends the report by email/notification. |
| `spring/core`          | `argus-spring-core`        | Spring Boot bootstrapping glue: indexes Spring-managed services, and registers Spring/Hibernate/JPA/Security-aware object-size estimators so JVM memory diagnostics correctly account for framework object overhead. |
| `spring/actuator`      | `argus-spring-actuator`    | Exposes `Health` through a Spring Boot Actuator `HealthIndicator` and adds a custom `resource` endpoint. |
| `spring/sba`           | `argus-spring-sba`         | Spring Boot Admin server-side UI extensions: health badges on the wallboard/applications views and a "Health Report" tab per instance. |
| `monitor` / `inspector` / `web` | `argus-monitor` / `argus-inspector` / `argus-web` | Reserved for a future first-class web UI; currently empty placeholder modules depending on `core`. |
| `bom`                  | —                          | Bill of materials for consumers that want to align versions across the Argus modules. |

A typical dependency direction is `api` ← `core` ← `report`/`logger`, with the `spring/*` modules layering
Spring Boot auto-configuration on top of `core`/`api` without the rest of the stack needing to know Spring
exists.

## HTML report

The `report` module renders a single, self-contained HTML page (Thymeleaf templates, offline-capable —
CSS/JS can be inlined instead of loaded from a CDN) that is organized into independent **fragments**, each
contributed by a `Fragment.Provider` and rendered in isolation so one failing fragment doesn't take down the
rest of the report. The report can be generated on demand, on a daily schedule, or automatically when issues
of a given severity are detected (`ReportService`/`ReportSettings`), and — when a password is configured —
encrypted into a zip before being sent.

> The screenshots below are taken from **Heimdall** running in **demo mode**: they exist purely to show how
> each fragment renders, and every score, metric and log entry shown is randomly generated rather than
> pulled from a real, running system.

![Argus HTML report overview](docs/images/report-overview.png "Full HTML report open in a browser: left-hand fragment navigation (Summary, Health, Services, Meters, Performance, Tasks, Logger, Environment) with the Summary fragment selected")

Current sections, in display order:

1. **Summary** — headline numbers for the reporting interval, which includes the summary of the health, Issue Types, Pending Alerts and the state of the application services.

   ![Report – Summary fragment](docs/images/report-section-summary.png "The report's Summary fragment: headline counters for the reporting interval (issues by severity, resource/service counts, ...)")

2. **Health** — the service and server `Resource`s with their computed `Health`.

   ![Report – Health fragment](docs/images/report-section-health.png "The report's Health fragment, showing the SERVICE and SERVER resources with their health score, severity and the group/item breakdown behind it")

3. **Services** — registered services/resources, their metadata and core metrics (memory, events, tasks, etc).

   ![Report – Services fragment](docs/images/report-section-services.png "The report's Services fragment: registered services/resources and their metadata (id, group, instances)")

4. **Meters** — application metrics collected during the interval (timers, summaries, counters, etc).

   ![Report – Meters fragment](docs/images/report-section-meters.png "The report's Meters fragment: application metrics collected during the reporting interval")

5. **Performance** — timing/performance statistics.

   ![Report – Performance fragment](docs/images/report-section-performance.png "The report's Performance fragment: timing/performance statistics for the reporting interval")

6. **Tasks** — scheduled/background task status.

   ![Report – Tasks fragment](docs/images/report-section-tasks.png "The report's Tasks fragment: scheduled/background task status")

7. **Logger** — captured log events and alerts.

   ![Report – Logger fragment](docs/images/report-section-logger.png "The report's Logger fragment: captured log events and alerts (with severity and occurrence count)")

8. **Environment** — environment/system information.

   ![Report – Environment fragment](docs/images/report-section-environment.png "The report's Environment fragment: environment/system information (JVM, OS, application build info)")

> **Health Report** — a dedicated, drill-down view of the health score and its trend (already surfaced as an
> iframe-embedded tab in the Spring Boot Admin extension, see below) is planned as its own report section.
> This section will be documented here once it lands.

## Spring Boot integration

Argus ships three layered Spring modules:

* **`argus-spring-core`** provides the bootstrapping glue used by the modules above it — for example,
  making sure the `jvm` module's object-size estimator understands Spring/Hibernate/JPA/Security types
  (proxies, repositories, lazy wrappers) so the JVM memory contributor's numbers aren't skewed by framework
  overhead it doesn't recognize.
* **`argus-spring-actuator`** auto-configures:
    * `HealthHealthIndicator`, a Spring Boot Actuator `HealthIndicator` that maps the process's `Health`
      (`Health.Type.INSTANCE`) onto an Actuator `Status` (`UP`, a custom `IMPACTED` status for `HIGH`
      severity, `OUT_OF_SERVICE` for `CRITICAL`), including the score, severity, textual report and a link
      to the full HTML report as details.
    * A custom `resource` Actuator endpoint that exposes every registered `Resource`, grouped by type.
* **`argus-spring-sba`** adds Spring Boot Admin server-side UI extensions (plain JS/CSS dropped into
  `META-INF/spring-boot-admin-server-ui/extensions`, auto-loaded by SBA):
    * Health badges overlaid on the wallboard hexagons and on the applications list, both service-level
      (worst score across instances) and per-instance, without modifying SBA's own Vue components. Hovering a
      badge lists the worst items behind the score.
    * Warnings/Errors log-event counters (startup/5m/1h/24h) on each instance row, colored by severity.
    * A "Health Report" tab on each instance's detail page (in the Insights group) that embeds the
      application's own HTML report in an iframe, using the `reportPath` detail published by
      `HealthHealthIndicator`.

The Wallboard is SBA's fleet-wide, at-a-glance view (one hexagon per application). Overlaying the Argus
health score there means an operator can spot a degraded application without opening it — the badge shows
the worst instance score, plus the average/max when the application has more than one instance.

![Spring Boot Admin wallboard with health badges](docs/images/sba-wallboard-health-badges.png "SBA Wallboard view: application hexagons, each overlaid with an Argus Health badge (score pill, plus avg/max when the application has multiple instances)")

The Applications list is where an operator drills from "something is degraded" into "which application and
which instance." The same badge is repeated at both levels — next to the application group title (aggregated)
and next to each instance row (individual) — so the worst-scoring instance is identifiable without opening
every instance's own page.

![Spring Boot Admin applications list with health badges](docs/images/sba-applications-health-badges.png "SBA Applications list, grouped by application: a Health badge next to each application group title (worst score across instances) and, on each instance row, the Warnings/Errors counters followed by the instance's Health badge")

Each instance row also shows two counters before its Health badge: **Warnings** and **Errors**, the number of
`WARN`/`ERROR` log events captured by the `logger` module. Each counter is a group of four numbers, one per time
window — **since startup / last 5 minutes / last hour / last 24 hours** — so an operator can tell at a glance
whether a problem is old (only the startup count is high) or happening right now (the 5m/1h counts are rising).
Each number is colored by the score of the matching `Logger` health item (green = healthy, yellow = with issues,
red = faulty), the same items that feed the instance's health score. Hovering the counters shows a tooltip that
explains the order of the windows.

![Warnings/Errors counters tooltip](docs/images/sba-applications-health-badges-logger-tooltip.png "Instance row in the SBA Applications list: Warnings 12/1/13/13 and Errors 0/0/2/2, colored per window, with the tooltip 'Counts for: startup/5m/1h/24h'")

The Health badge itself has a tooltip too: hovering it lists the **worst items** — the few health items with the
lowest scores, each with its group path, score and the value behind it (e.g. `JVM/GC/Eden: 5.1 (162ms)`). This
answers "what is the problem, and why is the score low?" without opening anything. On an application group badge
the tooltip shows the worst items of the worst-scoring instance.

![Health badge tooltip with worst items](docs/images/sba-applications-health-badges-worst-items.png "Hovering an instance Health badge (5.1): a tooltip listing the worst items - JVM/GC/Eden 5.1 (162ms), Logger/Warning/Startup 5.6 (12), Logger/Warning/Last 1h 6.8 (12)")

For the full picture, each instance can open its health report overview by clicking the button with the "heart"
icon. It shows the whole group/item tree as plain text, with each score colored by severity, so the operator can
see right away which component(s) brought the score down without leaving the applications list.

![Spring Boot Admin applications list with health overview](docs/images/sba-applications-health-overview.png "Health Report overlay for the heimdall instance (score 5.1): the full report as an indented tree - JVM (Memory, GC, File System, Other), Alerts, Issues, Logger - each item with its colored score and the value behind it")

Once an operator has narrowed the problem down to one instance, the badges and the overview aren't enough to
diagnose it — the "Health Report" tab closes that gap by embedding that instance's own Argus HTML report (the
same report described above, with the full group/item breakdown, logs and environment) directly in SBA, without
leaving the SBA UI or needing separate access to the monitored application.

The tab opens on the **Summary** fragment: one card per resource (here `Service` and `Server`), each with its
score, the score of every group with a trend sparkline, and a **Worst Items** table naming the items responsible
for the score.

![Spring Boot Admin instance Health Report tab – Summary](docs/images/sba-health-report-tab-overview.png "An instance's detail page in SBA with the 'Health Report' tab (Insights group) open: the Summary fragment with the Service (4.6) and Server (5.9) cards, their group scores and trends, and the Worst Items tables")

External dependencies registered as resources get their own card. The example below shows a `Database` resource,
scored on its queries, transactions, connections, InnoDB and replication groups, with the worst item
(`Queries / Temporary Disk Tables`, 36.4%) called out.

![Spring Boot Admin instance Health Report tab – Database resource](docs/images/sba-health-report-tab-overview-database.png "The Summary fragment scrolled down to the Database resource card (5.6): Queries, Transactions, Connections, InnoDB and Replication groups with scores and trends, and the Worst Items table showing Queries / Temporary Disk Tables at 36.4%")

The **Health** fragment is the drill-down view: every group can be expanded into its items, each with its score
and the value it was computed from (e.g. `GC → Eden: 4.6, 181ms`, `CPU → System: 5.9, 8.7%`).

![Spring Boot Admin instance Health Report tab – Health](docs/images/sba-health-report-tab-health.png "The Health fragment in the SBA Health Report tab: Service and Server cards with expandable groups - JVM/GC expanded to Eden 4.6 (181ms) and Tenured 10 (0ms), Server/CPU expanded to Total, User, System, Nice and I/O Wait")

The **Logger** fragment explains the Warnings/Errors counters seen on the applications list: trend charts of
warnings and errors over time, followed by the table of alerts (level, message, failure type, total count,
acknowledged state) the log events were aggregated into.

![Spring Boot Admin instance Health Report tab – Logger](docs/images/sba-health-report-tab-logger.png "The Logger fragment in the SBA Health Report tab: Warnings and Errors trend bar charts, followed by the Alerts table (created/modified at, level, message, failure type, acknowledged, total count)")

> These screenshots are only examples, cropped from a much larger report. The tab embeds the complete HTML
> report described in [HTML report](#html-report), so every fragment is available there: Summary, Health,
> Services, Meters, Performance, Tasks, Logger and Environment. You can move between them with the icon bar on
> the left. The resource cards depend on what the instance registers, so an application with a cache, broker
> or other dependency gets a card for each one, next to the `Service`, `Server` and `Database` cards shown
> here. Like the report screenshots above, these were taken from Heimdall in demo mode, so the scores and
> values are randomly generated.

Together these mean a Spring Boot application only needs `argus-spring-actuator` (and, for a fleet view,
`argus-spring-sba` on the Spring Boot Admin server) to get health scores and reports surfaced without any
custom integration code.

## Requirements

* Java 21
* Maven (multi-module reactor build, see `pom.xml`)

## License

Apache License, Version 2.0 — see [LICENSE](LICENSE).
