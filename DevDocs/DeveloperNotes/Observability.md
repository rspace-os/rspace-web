# Observability (OpenTelemetry)

RSpace sends traces and metrics over OTLP to an OpenTelemetry Collector.
The Collector sends them to the deployment's observability backend.

Credentials and other secrets must never be placed in URLs. Browser telemetry
is attacker-controlled even when it comes from an authenticated RSpace page.
Sanitize it at the source and enforce its identity again at the Collector.

OpenTelemetry agents and libraries are third-party Apache-2.0 code. Follow the
AI and Third Party code policy in Drata before adding or upgrading them.

## Backend

Attach the OpenTelemetry Java agent to the JVM:

```text
-javaagent:/path/to/opentelemetry-javaagent.jar
```

Configure it with the standard `OTEL_*` environment variables. At minimum, set
`OTEL_SERVICE_NAME`, `OTEL_RESOURCE_ATTRIBUTES`,
`OTEL_EXPORTER_OTLP_ENDPOINT`, and the signal exporters. Never expose the
Collector's OTLP ports publicly.

The dev stack sets `OTEL_LOGS_EXPORTER=none`. For manual deployments, set this
explicitly: the Java agent defaults to exporting logs. Before opting in with
`OTEL_LOGS_EXPORTER=otlp`, review and redact application log statements, then
set access controls and retention appropriate for the data. The existing
slow-request interceptor remains active without the Java agent and writes only
request duration, endpoint path, and user identity—not request parameters.

The agent instruments Servlet, Spring MVC, Jetty, JDBC, Hibernate, Apache
HttpClient, Quartz, and Log4j2. JavaMelody can run alongside it. It also adds
`trace_id` and `span_id` to Log4j2 output.

The agent records server request query strings in `url.query`, and RSpace sends
secrets such as password-reset tokens there. Strip query strings from backend
spans in the Collector, as `transform/backend_redaction` does in
`docker/dev/otel/otel-collector-config.yaml`. The agent also exports JVM
arguments, which can carry `-D` secrets. Set
`OTEL_RESOURCE_DISABLED_KEYS=process.command_args,process.command_line`.

RSpace has no custom spans or metrics. Before adding `@WithSpan`, note that
the agent records exception messages on custom spans, and many RSpace
exceptions include usernames.

### Development tuning

The dev stack disables Hibernate spans because they largely duplicate JDBC
spans. Set `OTEL_INSTRUMENTATION_HIBERNATE_ENABLED=true` to enable them.

The dev image also includes the `opentelemetry-span-stacktrace` agent
extension, which adds `code.stacktrace` to spans slower than
`OTEL_JAVA_EXPERIMENTAL_SPAN_STACKTRACE_MIN_DURATION` (default `5ms`).

## Frontend

`src/main/webapp/ui/src/common/otel.ts` starts browser telemetry before each
instrumented React root mounts, waiting at most two seconds; a slower SDK load
misses the first page requests rather than delaying render. It covers the global app bar island and the
standalone Inventory, Gallery, Apps, About, and public identifier pages. Other
React islands and TinyMCE dialogs rely on their containing page.

The browser records page load, resource, fetch, XHR, and Core Web Vitals. User
interaction instrumentation is intentionally disabled pending a separate data
and cardinality threat model. It sends `traceparent` with RSpace API requests
so browser and backend spans share a trace.

Every browser-created `url.full` and `http.url` value is reduced to origin and
pathname before export. Query strings, fragments, and URL credentials are
removed; malformed values become a fixed sentinel. Reassess this sanitizer
before adding another browser instrumentation package. Core Web Vitals use the
bounded `UiTraceContext.page` label rather than the browser path.

Fetch and XHR spans include `rspace.ui.page`. Requests from an instrumented
React island also include `rspace.react.island`. Wrap the island root with
`UiTraceContextProvider`, and make requests through `useTracedRequest`. Do not
use the provider for a full-page root.

The `rs.otel.web.enabled` deployment property enables the SDK. The
`rs.otel.web.traceSamplingRatio` property sets trace sampling from `0.0` to
`1.0`. Invalid values disable trace sampling and log a browser warning. JSPs
expose these values through `common/otelConfig.jsp`. When telemetry is disabled,
the SDK bundle is not loaded.

### Browser OTLP endpoint

Browsers post OTLP/HTTP to `/otlp/v1/traces` and `/otlp/v1/metrics`. Forward
these paths at the edge only to a browser-specific Collector HTTP receiver. Do
not send them through RSpace or to the trusted backend receiver. The browser
pipelines have no log pipeline and overwrite `service.name` and
`service.namespace` while deleting backend-like deployment, host, and process
identity attributes.

For the Apache setup used by
[`rspace-docker`](https://github.com/rspace-os/rspace-docker), enable
`headers`, `proxy`, `proxy_http`, and `rewrite`. Add this inside the HTTPS
`VirtualHost`, before its existing `ProxyPass /` rule:

```apache
RewriteEngine On

RewriteCond %{REQUEST_URI} ^/otlp/v1/(traces|metrics)$
RewriteCond %{REQUEST_METHOD} !^POST$
RewriteRule ^ - [R=405,L]

RewriteCond %{REQUEST_URI} ^/otlp/v1/(traces|metrics)$
RewriteCond %{HTTP:Content-Encoding} !^$ [OR]
RewriteCond %{HTTP:Content-Type} !^application/(x-protobuf|json)([[:space:]]*;|$) [NC]
RewriteRule ^ - [R=415,L]

RewriteCond %{REQUEST_URI} ^/otlp/v1/(traces|metrics)$
RewriteCond expr "%{HTTP:Content-Length} -gt 524288"
RewriteRule ^ - [R=413,L]

<LocationMatch "^/otlp/v1/(traces|metrics)$">
    RequestHeader unset Authorization
    RequestHeader unset Cookie
    RequestHeader unset Proxy-Authorization
    RequestHeader unset Referer
</LocationMatch>

ProxyPassMatch "^/otlp/v1/(traces|metrics)$" "http://collector-host:4319/v1/$1" connectiontimeout=5 timeout=30
ProxyPass "/otlp/" "!"

# Keep the existing RSpace catch-all after the OTLP rules, for example:
ProxyPass "/" "ajp://rspace-app:8009/"
ProxyPassReverse "/" "ajp://rspace-app:8009/"
```

Set `max_request_body_size: 524288` under the browser receiver's
`protocols.http` configuration, as in `docker/dev/otel/otel-collector-config.yaml`.
Apache rejects oversized declared lengths with 413; the Collector also bounds
chunked bodies and returns 400 when they exceed the limit. `LimitRequestBody`
does not enforce this limit for proxied requests.

These method and content-type checks are not authentication. The public route
is forgeable. Production must keep the Collector private and configure both a
512 KiB request-body limit and a sustained/burst rate limit for `/otlp/` in its
reverse proxy or WAF. The separate `rspace-docker` deployment repository must
implement these controls before browser telemetry is enabled there.

## Development stack

Start the reference Collector and Elastic APM/ELK pipeline with:

```bash
./docker/dev/rspace-dev up --observability
```

The flow is:

```text
Browser -> Apache -> Collector browser receiver/pipelines --+
RSpace -----------> Collector backend receiver/pipelines ----+-> Elastic APM Server -> Elasticsearch
```

APM Server converts OTLP into the data streams used by Kibana's APM UI. The
configuration is in `docker/dev/otel/otel-collector-config.yaml`; setup and
usage are in `docker/dev/README.md`.

The checked-in development versions are Collector `0.153.0`, Java agent
`2.29.0`, Java contrib `1.58.0-alpha`, Elasticsearch/Kibana/APM Server `9.4.2`,
APM integration `9.3.5-preview-1779430085`, OpenTelemetry JS SDK `2.10.0`, and
JS instrumentation/exporters `0.221.0`. Keep this set pinned and review
compatibility before changing any member.

Telemetry stores require authentication and authorization in production,
bounded retention, and a documented incident-response path.

To use Grafana instead, replace the Elastic services with an OTLP-compatible
Grafana stack and point the Collector exporter at it. RSpace configuration does
not change.
