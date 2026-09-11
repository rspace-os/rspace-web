# Observability (OpenTelemetry)

RSpace sends traces, metrics, and logs over OTLP to an OpenTelemetry Collector.
The Collector sends them to the deployment's observability backend.

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

The agent instruments Servlet, Spring MVC, Jetty, JDBC, Hibernate, Apache
HttpClient, and Log4j2. JavaMelody can run alongside it. It also adds
`trace_id` and `span_id` to Log4j2 output.

Use `@WithSpan` and `@SpanAttribute` for custom spans. Use
`GlobalOpenTelemetry.get()` for custom metrics. These APIs do nothing when the
agent is absent. `RecordManagerImpl#doCreateDocument` is the current example.

Add `@WithSpan` to new scheduled or asynchronous entry points so their work is
grouped into one trace.

### Development tuning

The dev stack disables Hibernate spans because they largely duplicate JDBC
spans. Set `OTEL_INSTRUMENTATION_HIBERNATE_ENABLED=true` to enable them.

The dev image also includes two agent extensions:

- `opentelemetry-span-stacktrace` adds `code.stacktrace` to spans slower than
  `OTEL_JAVA_EXPERIMENTAL_SPAN_STACKTRACE_MIN_DURATION` (default `5ms`).
- `opentelemetry-inferred-spans` uses async-profiler to create child spans for
  otherwise uninstrumented code. Disable it with
  `OTEL_INFERRED_SPANS_ENABLED=false`.

Inferred spans use platform threads and need an executable temporary directory.
Increase their sampling interval if profiling overhead is too high.

## Frontend

`src/main/webapp/ui/src/common/otel.ts` starts browser telemetry before each
instrumented React root mounts. It covers the global app bar island and the
standalone Inventory, Gallery, Apps, About, and public identifier pages. Other
React islands and TinyMCE dialogs rely on their containing page.

The browser records page load, fetch, XHR, user interaction, and Core Web
Vitals. It sends `traceparent` with RSpace API requests so browser and backend
spans share a trace.

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
these paths at the edge directly to the Collector's HTTP receiver. Do not send
them through RSpace.

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

<LocationMatch "^/otlp/v1/(traces|metrics)$">
    LimitRequestBody 5242880
    RequestHeader unset Authorization
    RequestHeader unset Cookie
    RequestHeader unset Proxy-Authorization
</LocationMatch>

ProxyPassMatch "^/otlp/v1/(traces|metrics)$" "http://collector-host:4318/v1/$1" connectiontimeout=5 timeout=30
ProxyPass "/otlp/" "!"

# Keep the existing RSpace catch-all after the OTLP rules, for example:
ProxyPass "/" "ajp://rspace-app:8009/"
ProxyPassReverse "/" "ajp://rspace-app:8009/"
```

Keep the Collector private and rate-limit `/otlp/` with the deployment's
firewall or web application firewall.

## Development stack

Start the reference Collector and Elastic APM/ELK pipeline with:

```bash
./docker/dev/rspace-dev up --observability
```

The flow is:

```text
Browser -> Apache -> OpenTelemetry Collector
RSpace -----------> OpenTelemetry Collector -> Elastic APM Server -> Elasticsearch
```

APM Server converts OTLP into the data streams used by Kibana's APM UI. The
configuration is in `docker/dev/otel/otel-collector-config.yaml`; setup and
usage are in `docker/dev/README.md`.

To use Grafana instead, replace the Elastic services with an OTLP-compatible
Grafana stack and point the Collector exporter at it. RSpace configuration does
not change.
