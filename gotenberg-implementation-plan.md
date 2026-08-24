# Implementation plan: Gotenberg and JODConverter with temporary Aspose support

Include the update to `docs/adr/0001-gotenberg-plus-libreoffice-sidecar-for-document-conversion.md`
in the same PR. Edit the ADR before the code so reviewers have the decision context. See
`CONTEXT.md` for shared terms.

Use one conversion sidecar as RSpace's only conversion interface. Behind it, use Gotenberg for
Office-to-PDF conversion and JODConverter for Word Import and Export.

Keep Aspose working for deployments that set `aspose.enabled=true`. Aspose is deprecated and will be removed later. It stays disabled by default. New deployments and new conversion roles must use Gotenberg and JODConverter.

Accepted scope also includes preview formats, `.docx` export, PDF-to-PNG thumbnails, endpoint
limits, and the data-URI image flow.

Implement this plan in one PR. The numbered sections give the order of work and review; they are
not separate PRs or merge points. Run each section's checks before moving on, and merge only after
the final end-to-end verification passes. The conversion evidence below predates the PR and must
be reproduced on the versions selected here.

### Security and dependency baseline

Treat every uploaded office file, converter response, archive member, HTML element, image, and
PDF object as hostile. Authentication to a backend does not make its response trusted. Validate
before conversion, isolate conversion, then validate and sanitize the result before persistence
or cache publication.

Keep the Java 17 RSpace WAR on the existing major versions, but apply available stable minor and
patch upgrades before implementing the clients: Spring Framework 6.2.19, Apache HttpClient
5.6.4, PDFBox 3.0.8, Tika 3.3.2, jsoup 1.23.1, Commons IO 2.22.0, and Commons Compress 1.28.0.
Do not add another HTTP client, PDF parser, MIME detector, HTML parser, or archive library. If
implementation imports a currently transitive library directly, declare it directly at the
repository-managed version and keep Maven dependency convergence passing. Do not perform a major
WAR dependency upgrade as part of this work. The separately built and shipped sidecar remains on
the latest stable dependency versions.

Use four separate deadlines rather than one overloaded timeout: connection acquisition,
TCP connection, conversion wall-clock, and response-idle. Conversion POSTs are not
idempotent and are never retried after transmission begins.

---

## Existing conversion evidence

The conversion check passed on 10 July 2026 with `PowerPasteTesting_RSpace.docx`. The file has
three embedded images and matching expected HTML under
`src/test/resources/TestResources/word2rspace/powerpaste/`.

### JODConverter result

Use this pinned image. It contains JODConverter 4.4.7 and LibreOffice 25.2.3.2. The image is amd64-only, so arm64 hosts must enable Docker emulation.

```sh
docker run --rm --platform linux/amd64 -p 14080:8080 \
  ghcr.io/jodconverter/jodconverter-examples@sha256:3213ae4931b8274139436ab10daaba1bceb7ab7ce46e68a8700fccb8b8a69be6
```

The verified endpoint is `POST /lool/convert-to/{format}`. Upload the input through the multipart field `data`.

DOCX-to-HTML needs no extra options. All three images became base64 data URIs. Browser automation loaded all three images at their natural sizes.

```sh
curl -F "data=@PowerPasteTesting_RSpace.docx" \
  http://localhost:14080/lool/convert-to/html -o out.html
```

HTML-to-DOCX must force LibreOffice's Writer HTML filter. Without this parameter, the REST image returns HTTP 500 with `Unsupported conversion`. A custom format registry alone does not fix it.

```sh
curl -F "data=@with-data-uri-image.html;type=text/html" \
  -F "lFilterName=HTML (StarWriter)" \
  http://localhost:14080/lool/convert-to/docx -o out.docx
```

The output DOCX contained one PNG under `word/media/`. Converting it back to PDF showed the image, confirming that LibreOffice can render it.

### Gotenberg result

Use this pinned Gotenberg image. It resolved to Gotenberg 8.34.0 with LibreOffice 26.2.4.2 on arm64.

```sh
docker run --rm -p 13000:3000 \
  gotenberg/gotenberg@sha256:67097317623a503ba2a6a7e9ae8db6929a1f7e1bbd88077bacf2d325fbdab923 \
  gotenberg --api-timeout=180s

curl -F "files=@PowerPasteTesting_RSpace.docx" \
  http://localhost:13000/forms/libreoffice/convert -o out.pdf
```

Gotenberg returned a valid three-page PDF. Browser automation rendered the first page and showed all three source images.

The full Docker dev stack also passed its smoke check. Browser automation logged in as `user1a` and reached the Workspace page at `http://localhost:8196/workspace`.

**Result:** passed. The sidecar must use `HTML (StarWriter)` for HTML-to-DOCX requests.

Before implementing ODT and OTT support, extend this check with two committed
fixtures beside `PowerPasteTesting_RSpace.docx`: an image-heavy ODT derived from that fixture
and an OTT template containing representative text, styles, and an image. Record their source
and expected HTML so the fixtures are reproducible.

---

## 1. Add the sidecar and backend clients

### Stateless conversion sidecar interface

Do not use the upstream JODConverter sample REST interface in production. Build a small conversion
sidecar under `docker/conversion-sidecar/`. Package and support it only as a container image; do
not provide a host-process or executable-JAR deployment mode. Deploy it with one Gotenberg
container on the same machine and connect the two through a private container network managed by
Docker Compose or an equivalent container tool. This pair is the deployment and scaling unit.
One sidecar owns both Word conversion and the Gotenberg proxy route used by RSpace:

```text
POST /v1/convert/html
POST /v1/convert/docx
POST /forms/libreoffice/convert
GET  /v1/capabilities
```

Preserving Gotenberg's route gives the RSpace PDF client a stable sidecar interface while keeping
Gotenberg private. RSpace never connects directly to Gotenberg. The proxy route is a constrained
adapter, not a general HTTP proxy: it accepts exactly the same single `files` part that RSpace
sends, uses the code-owned upstream origin and route, and exposes no
caller-selected Gotenberg options.

### HTTP server stack

Build the converter as an independently built Maven application. Do not add it as a module of the
RSpace WAR and do not copy the upstream sample application.

| Layer | Choice |
| --- | --- |
| Runtime | Java 25 |
| Application framework | Spring Boot 4.1.0 |
| HTTP server | Embedded Tomcat 11 from Spring Boot |
| HTTP implementation | Spring Web MVC |
| Outbound HTTP | Apache HttpClient 5.6.4 through Spring `RestClient` |
| Conversion library | `jodconverter-local-lo` 4.4.11 |
| Health | Spring Boot Actuator health groups only |
| Build | Standalone `docker/conversion-sidecar/pom.xml`; executable JAR packaged only in the sidecar image |
| Archive validation | Apache Commons Compress 1.28.0 |
| Tests | Boot-managed JUnit Jupiter 6.0.3, MockMvc, and Docker smoke tests |

Use Spring Boot 4.1.0 dependency management for its framework, Tomcat, logging, Jackson, and
test graph. Pin the Boot release, JODConverter 4.4.11, HttpClient 5.6.4, Commons Compress 1.28.0, build-plugin
versions, Maven builder image digest, and runtime image digest; do not independently pin every
Boot-managed transitive dependency. Spring Boot 4.1 and Tomcat 11 support Java 25. Use the
LibreOffice-specific JODConverter module because the container contains LibreOffice, not Apache
OpenOffice. This newer Java runtime applies only to the sidecar image; it does not change
the RSpace WAR's Java 17 baseline.

Do not use `jodconverter-spring-boot-starter`. Its normal job is to create and retain an
Office manager. This converter needs direct control of each one-shot LibreOffice process and
profile. Use `jodconverter-local-lo` through a small internal conversion runner instead.

Use only these Spring Boot starters:

- `spring-boot-starter-web` for MVC, multipart requests, embedded Tomcat, JSON, and
  `ProblemDetail` responses.
- `spring-boot-starter-actuator` for liveness and readiness.
- `spring-boot-starter-test` in test scope.

Do not add Spring Security, a database, ORM, sessions, messaging, a template engine, a UI,
an application cache, or an OpenAPI runtime. The sidecar accepts valid requests without
application-level authentication.

Configure Tomcat and Spring MVC as follows:

```properties
server.port=8080
spring.servlet.multipart.file-size-threshold=0
spring.servlet.multipart.max-file-size=200MB
spring.servlet.multipart.max-request-size=201MB
spring.mvc.async.request-timeout=185s
server.error.include-message=never
server.error.include-stacktrace=never
management.endpoints.web.exposure.include=health
```

The proxy adapter owns the fixed upstream origin `http://gotenberg:3000`. Do not expose a property
that can change the scheme, host, port, or route. Compose-compatible deployments must give the
paired Gotenberg container the `gotenberg` network alias on a private network unique to that pair.
The deployment definition, not runtime discovery, guarantees that the alias belongs to a container
on the same machine. Gotenberg is never an independently located or independently scaled upstream.

Configure Tomcat to accept exactly one file part on every conversion route. Reject excessive
part or header counts and oversized part headers, cap swallowed bodies, and use the same 201 MB request ceiling at the
connector. A request with an extra file or form field is invalid. Set the conversion wall-clock
deadline to 180 seconds and keep the asynchronous response deadline slightly longer so cleanup
can complete.

The sidecar serves HTTP only on the private endpoint carried by the secure tunnel and must not have
a public listener or route.
Map Actuator health groups to the existing `/health/live` and `/health/ready` paths and do
not expose other Actuator endpoints.

Set the multipart threshold to zero so Tomcat writes uploads to the temporary volume instead
of heap. Move the upload into its request directory with `MultipartFile.transferTo`. Return
the completed output with `StreamingResponseBody`; delete the request directory in that
stream's `finally` block so disconnects also clean up.

Generate a correlation ID for each Gotenberg conversion. Apply the admission limit before starting
a converter and release its permit after synchronous or asynchronous response handling finishes.

Use these classes and responsibilities:

- One MVC controller owns the two Word routes, validates the file extension, calls the local
  conversion runner, and returns the streamed file.
- One Gotenberg proxy controller owns only `/forms/libreoffice/convert`; one internal adapter
  forwards its validated temporary file to the fixed paired-container route and streams the result.
- One conversion runner owns temporary files, the sandbox, the one-shot Office manager, and
  JODConverter options.
- One admission module owns separate PDF and Word global limits.
- One `@RestControllerAdvice` maps safe error categories to `ProblemDetail`.
- Health indicators check the sandbox launcher, LibreOffice, temporary volume, and capacity.

Do not add Java interfaces for these classes unless a second implementation is introduced.
The sidecar's three-route HTTP interface is the seam used by RSpace and end-to-end tests.
Gotenberg routing and response handling remain inside the proxy adapter.

Before accepting the stack, rerun the baseline image tests with Java 25, Spring Boot 4.1.0,
and JODConverter 4.4.11. The original check used Java 17 and JODConverter 4.4.7, so the new stack must
prove the same data-URI image behavior and Writer filter.

Use a multi-stage Dockerfile. Build the executable JAR with Maven and Java 25 in the first
stage, but do not publish or document the JAR as a runnable distribution. Base the final stage on
the official Ubuntu 26.04 LTS image and install Java 25,
`libreoffice-core-nogui`, `libreoffice-writer-nogui`, `libreoffice-common`,
`libreoffice-java-common`, the fixed font set, and the sandbox launcher from Ubuntu's
architecture-matched repositories. It runs as a non-root user with no shell-based
entrypoint. Do not copy Maven caches, source files, or test output into the final image.

The package-manager build is required. A native ARM64 check started OpenJDK 25.0.3 and
LibreOffice 26.2.4.2 successfully. Installing TDF's tarball on a minimal Java image instead
failed first on `libXinerama.so.1` and then on `libssl3.so`, showing that it would leave us
responsible for a fragile native dependency list.

### LibreOffice version selection

This sidecar uses local headless LibreOffice through `jodconverter-local-lo`. It does not use
LibreOffice Online or `jodconverter-remote`.

Use LibreOffice 26.2.5, the latest stable release in the maintained 26.2 line, from an
architecture-matched package source validated by the baseline check. It is
available for both amd64 and arm64. LibreOffice 25.8 reached end of life on 12 June 2026, and
26.2 reaches end of life on 30 November 2026. Treat 25.8 and the baseline version 25.2.3.2 as
comparison inputs only, not production fallbacks.

Run DOCX-, ODT-, and OTT-to-HTML plus HTML-to-DOCX with the baseline DOCX fixture and the new
ODF fixtures. Require all embedded images, the Writer HTML filter, correct DOCX media files,
bounded memory, request cleanup, and sandbox isolation to pass. Also open the produced files
in LibreOffice and render them through Gotenberg for a visual comparison.

Run the suite on native amd64 and arm64 hosts. Use the same locale, timezone, and font package
versions on both. Equivalent structure and visual layout are required; byte-identical output
is not. Publish the multi-architecture manifest only after both platforms pass.

Pin the Ubuntu base digest and record the resolved Java, LibreOffice, font, and OS package
versions in the image SBOM. Rebuild and rerun the suite for security updates. If the current
maintained LibreOffice line fails, stop the implementation and record the incompatibility.
Do not silently ship an end-of-life version.

See `DevDocs/DeveloperNotes/LibreOfficeJodConverterArm64Research.md` for the source-backed
packaging and compatibility investigation.

All three routes accept `multipart/form-data` and return the converted file as the response
body. There are no job IDs, status calls, sessions, callbacks, or server-side cache.

- `/v1/convert/html` accepts `.docx`, `.odt`, and `.ott`. It returns
  `text/html; charset=UTF-8`.
- `/v1/convert/docx` accepts HTML. It returns the standard DOCX content type.
- `/forms/libreoffice/convert` accepts one part named `files` and returns `application/pdf`.
  Reject every additional form
  field, file part, query parameter, and caller-supplied `Gotenberg-*` header. The adapter sends
  only the generated correlation ID and validated file to the fixed upstream LibreOffice route.
  Accept only the PDF backend's format allowlist and replace the caller filename with a
  generated name retaining only the validated extension. Do not forward caller authorization,
  cookies, proxy headers, filenames, or tracing baggage.
- The two Word routes accept one part named `file`.
- Before DOCX import, use Commons Compress 1.28.0 to validate the package. Confirm the ZIP
  contains `[Content_Types].xml` and `word/document.xml`, reject encrypted members, duplicate
  names, absolute paths, `..`, backslash traversal, links, nested archives, macro projects, and
  external OOXML relationships.
- Before ODT or OTT import, require `mimetype` to be the first ZIP member, stored without
  compression or an extra field, and to contain the exact ASCII media type:
  `application/vnd.oasis.opendocument.text` for ODT or
  `application/vnd.oasis.opendocument.text-template` for OTT. Require `content.xml` and
  `META-INF/manifest.xml`, and require the manifest root entry to use the same media type.
  Parse the manifest with DTDs, external entities, XInclude, and external schema access
  disabled. Reject invalid or renamed files before starting LibreOffice.
- Apply one shared archive budget while reading, not from attacker-supplied ZIP metadata:
  maximum entry count, per-entry expanded bytes, aggregate expanded bytes, XML depth and bytes,
  and compression ratio. Stop on the first exceeded limit and add highly compressed, duplicate,
  traversal, malformed-size, and oversized-XML fixtures. Apply the same validator in RSpace
  before upload and in the sidecar before LibreOffice as defense in depth.
- The service chooses all LibreOffice options. For HTML-to-DOCX, it always uses `HTML (StarWriter)`. RSpace does not send filter names or other LibreOffice settings.
- Use the uploaded filename only to select an allowed input format. Store it under a generated local name; never use a client path.
- Limit input to 200 MB. Return `413` before conversion when the limit is exceeded.
- Limit generated output to 300 MB and return `413` without streaming a partial response when
  the limit is exceeded. Keep the temporary-volume quota larger than one bounded request but
  small enough that concurrent requests cannot exhaust the node.

Each request gets a server-generated ID and its own temporary directory. Create the directory
with `0700` permissions and generated filenames. Stream the upload to an input file, convert
to an output file, then stream that file to the same HTTP response. Never expose an endpoint
that can list or fetch a previous result. Return `Cache-Control: no-store`.

Run every conversion in a fresh LibreOffice process with a fresh user profile. The process
must run in a sandbox that can see only that request's directory and the read-only files
needed by LibreOffice. Give it no network access. Stop the process before deleting the
directory in a `finally` block. Do not reuse a LibreOffice process or profile for another
request, even when both requests came from the same RSpace instance.

Seed each fresh profile with macro execution disabled at the highest security level and update
links disabled. Pass load properties that prevent external-link updates. Test DOCX, ODT, and OTT
fixtures containing macros, remote images, `file:` links, external templates, OLE objects, and
DDE fields; the service must neither execute nor fetch them, and must not copy active content
into the returned HTML or DOCX.

The HTTP process may stay alive and manage a bounded number of these one-shot workers. It
must keep no document, user, or job state. Any request can go to any deployed pair. Mount no
persistent volume. Give the container a read-only filesystem with a size-limited temporary
volume. On startup, remove any request directories left by a terminated process.

Run the service and worker as non-root, drop every Linux capability, set
`no-new-privileges`, use a restrictive seccomp/AppArmor profile, and set CPU, memory, PID,
open-file, temporary-storage, and execution limits. Mount request storage `nodev`, `nosuid`, and
`noexec`. A resource-limit failure is a safe `422`, `503`, or `504` category and never causes an
unsandboxed retry.

Use a rootless Linux process sandbox inside the sidecar container for each worker. The sidecar
must not launch sibling containers, run as a host process, or mount the Docker socket. Before
implementing the HTTP handler, prove the chosen sandbox can convert the baseline fixture while
being unable to read
a sibling request directory or open a network connection. If isolation cannot start, mark
the converter unready and reject work; never fall back to an unsandboxed conversion.

Multiple RSpace instances may share all three routes. Each deployment reaches the sidecar only
through its private network or secure tunnel link. Do not accept a caller or tenant ID in form data
or identity headers.

Put the shared sidecar on a private application network and expose its HTTP interface only through
the secure tunnel.
Use bounded PDF-proxy and Word-conversion concurrency so requests cannot exhaust local workers or
upstream Gotenberg slots. Keep the Gotenberg connection pool and proxy queue bounded independently
from the local LibreOffice workers. These counters are memory-only and contain no document data.
They apply per sidecar/Gotenberg pair. Deployments requiring per-client quotas must enforce them at
the tunnel ingress or load-balancer layer. Scale by adding complete pairs
and route work only to a sidecar whose paired Gotenberg container is healthy; never scale either
member independently.

Place Gotenberg beside the sidecar on the same machine and on a private container network reachable
only by that sidecar. Do not publish Gotenberg's port to the host, tunnel, RSpace, or public
clients. Use only the code-owned `http://gotenberg:3000` origin and pin its private container DNS
address. Disable redirects, cookies, retries, ambient proxy settings, and transparent compression.
Never forward caller authorization headers. Apply the same acquisition, connect, wall-clock, idle,
input, output, and problem-body
limits as the RSpace client. Validate Gotenberg's status, content type, PDF signature, and bounded
response before returning it. Delete all partial files on upstream error, timeout, or downstream
disconnect. Do not retry after the request body may have reached Gotenberg.

### Cache ownership and isolation

Keep the existing checksum cache for Office previews in RSpace. Word Import and Export stay
uncached. Do not add a cache to either shared conversion backend. A backend must not store
results, advertise cache hits, accept cache keys, or combine two requests that have the same
content. Two RSpace instances that upload the same bytes must cause two independent
conversions on their first request.

Each RSpace deployment owns its converted-file cache. Multiple application nodes from the
same deployment may share it, but different RSpace deployments must not. If deployments use
the same storage platform, give each one a separate bucket or root and storage credentials
that cannot read another deployment's area. A filename prefix alone is not isolation.

Keep the existing checksum filename and `convertedDocs-<format>` categories inside that
deployment-local cache. Perform the record permission check before every cache lookup and
download. Never send the checksum, cache path, cache-hit state, or storage identity to the
converter.

The expected behavior for an identical file is:

1. The first request from RSpace A misses A's cache, converts, and stores only in A's cache.
2. The first request from RSpace B misses B's cache, converts again, and stores only in B's
   cache.
3. Later requests hit only the cache belonging to their own RSpace deployment.

This preserves repeat-preview caching without creating a cross-instance content-presence or
timing signal.

Use these responses:

| Result | Status |
| --- | --- |
| Converted file | `200` |
| Missing file or invalid route format | `400` |
| Unsupported input format | `415` |
| Input or generated output too large | `413` |
| Valid input that LibreOffice cannot convert | `422` |
| Conversion timeout | `504` |
| This client's queue is full | `429` with `Retry-After` |
| Global capacity full or converter not ready | `503` with `Retry-After` |
| Invalid or unavailable upstream Gotenberg response | `502` |
| Unexpected service error | `500` |

Errors use `application/problem+json`. Include the logical error code and a generated request ID.
Do not return local paths, document content, client identity, or LibreOffice command output.

Add `GET /health/live` for the HTTP process, `GET /health/ready` for local sidecar readiness,
and role-specific `GET /health/ready/word` and `GET /health/ready/pdf` checks. Word readiness
checks the sandbox launcher, LibreOffice executable, temporary space, and local worker capacity.
PDF readiness checks the paired Gotenberg container and PDF-proxy capacity. The aggregate readiness
check requires both roles because every supported pair provides both. A Gotenberg outage leaves
`/health/ready/word` available for diagnosis but marks `/health/ready` and
`/health/ready/pdf` unready so the complete pair is removed from routing.

`GET /v1/capabilities` accepts an unauthenticated request and returns only a fixed sidecar
protocol identifier, protocol version, and the declared PDF and Word roles. It returns no
upstream URL, credential, capacity, or deployment details. RSpace verifies this interface at
startup whenever `conversion.url` is set, so a direct Gotenberg URL fails closed. Require both
roles from that one sidecar; fail startup if the protocol identifier, version, or either declaration
is absent. Runtime readiness remains independent from this configuration check.

Use a bounded number of one-shot workers, a bounded queue for the sidecar, and a
180-second conversion timeout. Do not retry conversions inside the service. Log only input and
output formats, byte count, duration, status, and a safe error category. Do not log filenames or
document content.

This design removes application-level paths for data to cross between RSpace instances. A
shared container cannot promise protection from a container-runtime or kernel escape. If
that is part of the threat model, use a separate converter deployment for each RSpace
instance.

Test the service through its HTTP interface:

- Cover DOCX-, ODT-, and OTT-to-HTML plus HTML-to-DOCX.
- Confirm that `doc`, `dot`, `dotx`, and `rtf` are rejected before a worker starts.
- Check that the Writer filter is applied inside the service.
- Check data-URI images in both directions with the baseline fixture.
- Check every status in the table, including queue full and timeout.
- Check that temporary files are removed after success, failure, and client disconnect.
- Reject extra parts, query parameters, control headers, unsupported formats,
  redirects, oversized bodies, malformed responses, and every safe upstream error mapping.
- Use a recording fake Gotenberg to confirm the sidecar sends a generated filename and one `files`
  part, and never retries.
- Send concurrent files with different canary text from two deployments. Confirm
  that neither response, problem body, log, profile, nor temporary directory contains the
  other client's canary.
- Preview the exact same file from two RSpace deployments. Confirm that the PDF backend
  performs two conversions, each deployment stores its own result, and later cache hits
  never contact the backend.
- Import the exact same DOCX from both deployments. Confirm that the Word backend performs
  two isolated conversions and stores neither result.
- Delete or replace one deployment's cached result and confirm that the other deployment's
  cache is unchanged.
- Confirm that each conversion uses a new process and profile, and that the worker cannot
  access another request directory or the network.
- Restart the HTTP process during conversion and check that startup cleanup removes the
  abandoned directory.
- Exercise the sidecar PDF route against a recording fake and the pinned Gotenberg image; require
  the same validated output and safe failure mapping.
- Send simultaneous PDF and Word work from two deployments. Confirm bounded work,
  no starvation between route groups, no authorization-header forwarding to Gotenberg, and no
  cross-deployment response, log, or temporary-file leakage.
- Start two complete sidecar/Gotenberg pairs and send unrelated requests to either pair to confirm
  there is no affinity or shared document state. Verify and document that quotas are per pair
  unless ingress supplies fleet-wide enforcement. Confirm neither sidecar can reach the other
  pair's Gotenberg container.

### Hardened Gotenberg deployment

Run Gotenberg 8.34.0's LibreOffice-only image, pinned by a separately tested multi-architecture
manifest digest. Run exactly one Gotenberg container beside each sidecar container on the same
machine and private container network. A pair may serve one or multiple RSpace deployments, but
Gotenberg is never remote from, shared across, or scaled separately from its sidecar. RSpace has
no direct Gotenberg route or network access. The sidecar's `/forms/libreoffice/convert` interface
filters the request and enforces conversion limits. This requirement applies to both
pairs dedicated to one RSpace deployment and pairs shared by several deployments, including local
Docker development. The physical topology does not change when the pair is shared. The baseline
full-image digest is compatibility evidence, not the production pin.

Set and test these Gotenberg controls (using the corresponding current flags/environment
variables):

- `api-body-limit=200MB`, `api-timeout=180s`, a bounded LibreOffice queue, and a generated
  correlation ID sent as `Gotenberg-Trace`.
- Disable `downloadFrom`, webhooks, Chromium routes, PDF-engine routes, and the debug route.
  Only the sidecar sends one `files` part to `/forms/libreoffice/convert`.
- Deny both public and private LibreOffice outbound destinations and enforce deny-all egress at
  the container/orchestrator network layer. Do not inherit proxy environment variables.
- Run non-root with a read-only root filesystem, all capabilities dropped,
  `no-new-privileges`, a restrictive seccomp/AppArmor profile, a size-limited `noexec` temporary
  volume, and explicit CPU, memory, PID, open-file, queue, and wall-clock limits.
- Use the random request filename; never send `Gotenberg-Output-Filename`, watermark, stamp,
  metadata, password, webhook, or remote-download controls.

Gotenberg publishes security fixes only on its latest release. The update job must build and
test a candidate digest promptly, verify its provenance/signature, scan its SBOM, run the full
hostile-document corpus, and promote the digest rather than a mutable `:8` tag. Do not suppress
a security update by keeping an older digest indefinitely. Gotenberg 8.34.0 fixed LibreOffice
external/local resource resolution, while its API defaults still leave remote downloads and
outbound filtering permissive unless configured: [8.34.0 release](https://github.com/gotenberg/gotenberg/releases/tag/v8.34.0),
[security policy](https://github.com/gotenberg/gotenberg/security), and
[configuration](https://gotenberg.dev/docs/configuration).

Test Gotenberg with macro-bearing documents, external HTTP and `file:` references, path
traversal filenames, archive bombs, huge page dimensions, concurrent canaries, timeouts, client
disconnects, and container restarts. Confirm no outbound connection, local-file read,
cross-request output, partial cache entry, or abandoned temporary file occurs.

Add these classes under `com.researchspace.documentconversion.ext`.

- **`DocumentConversionHttpClientFactory`**
  - Build Spring Framework 6.2.19 `RestClient` instances on
    `HttpComponentsClientHttpRequestFactory` and Apache HttpClient 5.6.4.
  - Validate the sidecar URL once at startup: absolute `http`, no user info, query, fragment,
    wildcard host, or path traversal. Reject every other scheme. Build route paths from constants;
    no request value may influence the origin.
  - Accept any host in the validated HTTP origin. Enforce allowed destinations at the secure
    tunnel and deployment firewall, which are the network security boundary.
  - Disable redirects, cookies, automatic retries, and transparent response compression. Send
    `Accept-Encoding: identity`; bound the connection pool to conversion capacity.
  - Configure separate connection-acquisition, TCP-connect, conversion wall-clock, and
    response-idle deadlines. Do not reuse `SimpleResilienceFacade`: it retries HTTP 5xx POSTs.
  - Stream success bodies through a counting input stream into a `0600` file under a `0700`
    request directory. Cap success at 300 MB and problem bodies at 64 KB. Delete partial and
    completed temporary files in `finally` on success, error, timeout, and disconnect.

- **`PdfConversionClient`**
  - Implement `DocumentConversionService` and extend `AbstractDocumentConversionService`.
  - POST multipart `files=` to `{conversion.url}/forms/libreoffice/convert`.
  - Support PDF output for the existing Gallery preview formats. Send Markdown through
    LibreOffice's text filter because Gotenberg does not accept `.md` as an Office extension.
  - Return PDF input unchanged for PDF-to-PDF requests.
  - Use the configured `RestClient`; never load a response into `byte[]` and never retry.
  - Require `200`, `application/pdf`, a PDF signature, and a bounded body. Validate with the
    PDFBox 3.0.8 using temp-file-backed parsing before atomically publishing to cache:
    reject encryption, embedded files, JavaScript, launch actions, external-file actions,
    unreasonable page counts/dimensions, and malformed cross-references.

- **`JodConverterClient`**
  - POST one `file` part to `/v1/convert/html` or `/v1/convert/docx`.
  - Convert DOC, DOCX, ODT, OTT, RTF, and TXT to HTML.
  - Convert HTML to DOCX. The service owns the Writer filter setting.
  - Stream all responses.
  - Map problem responses to bundle-based failed `ConversionResult` values.
  - Do not retry a request after its body may have reached the service.
  - Require the expected content type and validate returned HTML or DOCX structure within the
    response budget. For DOCX, rerun the archive validator and reject macros, external
    relationships, traversal names, or malformed package metadata.

- **`PdfThenPngConverter`**
  - Convert Office files to a temporary PDF with the configured PDF backend.
  - Convert that PDF to PNG with the existing `PDFToImageConverter`.
  - Register this converter before the raw PDF clients so it handles Office-to-PNG requests.
  - Use PDFBox 3.0.8; update `PDFToImageConverter` to use try-with-resources, temp-file-backed
    parsing, the PDF validation limits above, and bounded first-page dimensions before render.
    Always delete the intermediate PDF.

- **`DataUriExtractor`**
  - This name is flexible. The class may live in `com.researchspace.document.importer`.
  - Use jsoup 1.23.1 to parse converted HTML, extract strict base64 image data
    URIs, then allowlist-sanitize the complete document before persistence.
  - Remove scripts, forms, frames, objects, embeds, event handlers, `meta` refresh, active
    URLs, external images, `file:` URLs, CSS `url()`/`@import`, and unsupported schemes while
    preserving only the formatting needed by the import fixtures.
  - Decode with per-image, image-count, pixel-dimension, and aggregate-byte limits. Use Tika
    3.3.2 to verify magic bytes against an allowlist of raster image types; do not accept SVG
    or trust the data URI's declared media type or extension.
  - Write each accepted image under a generated `0600` filename in the request directory,
    replace its `src` with the generated local reference, and pass it through the existing
    Gallery image-ingestion checks.
  - Reject HTML larger than 50 MB before jsoup parsing and reject malformed, non-base64,
    mixed-alphabet, oversized, or
    trailing-data URIs with a bundle-based failed `ConversionResult`.

- **`Base64ImageInliner`**
  - Make this a focused helper because authorization and path safety are security boundaries.
  - Write export HTML to a temporary file.
  - Resolve only image records already authorized for the exporting user. Reject remote URLs,
    absolute paths, `..`, symlinks, non-regular files, and any resolved path outside the
    request directory.
  - Verify the raster type with Tika, enforce the same image and aggregate budgets, then stream
    each image through `Base64.getEncoder().wrap(fileOutputStream)`.
  - Send the completed file as the request body.

Update configuration as follows.

- **`DocConverterProdConfig`**
  - Add `conversion.url` and `conversion.use.dummy.converter`.
  - The one URL must identify a conversion sidecar that provides both the Gotenberg-compatible
    PDF route and JODConverter Word routes. Verify its protocol identifier and both
    capabilities at startup; never infer sidecar identity from a URL.
  - Use Aspose only when `conversion.url` is empty and `aspose.enabled=true`.
  - Never fail over to Aspose after a Gotenberg or JODConverter request fails.
  - Order delegates as: `PDFToImageConverter`, `PdfThenPngConverter`, PDF client, Word client.

- **`DocConverterBaseConfig`**
  - Keep the beans required by the temporary Aspose path.
  - Mark them for removal with Aspose.

- **Timeouts**
  - Add separate connection-acquisition, connect, conversion, and response-idle properties.
    Defaults are 2 seconds, 5 seconds, 180 seconds, and 185 seconds respectively.
  - Set Gotenberg's `--api-timeout` and the Word worker timeout to the 180-second conversion
    value. The outer response-idle timeout is longer only to permit final streaming and cleanup.

- **Backend network boundary**
  - Both clients use the same private-network or secure-tunnel origin.
  - Do not publish the sidecar endpoint. Fail startup on an invalid URL or incompatible sidecar.

Keep these Aspose classes for compatibility: `AsposeAppInvoker`, `AsposeWebAppClient`, `AsposeConversionChecker`, `AsyncDocConverterService`, `AsyncDocumentConverterService`, and `CustomerIDSupplier`.

Keep their supporting configuration. Mark the compatibility code deprecated and do not add new behavior to it.

Write new RSpace tests with the repository's JUnit 5 setup and `MockRestServiceServer` bound to
the `RestClient.Builder`. The standalone Boot service uses its managed JUnit Jupiter 6.0.3.

- Add `PdfConversionClientTest` and `JodConverterClientTest`.
- Test unauthenticated access with valid requests, fixed request origins/routes, redirect rejection,
  tunnel-host validation, protocol/capability validation that rejects direct Gotenberg, separate deadlines,
  no retries, bounded
  success/error bodies, cleanup, and each failed
  `ConversionResult` response.
- Add `DataUriExtractorTest` for multiple images, active HTML, remote/CSS URLs, spoofed MIME,
  SVG, pixel and byte limits, and malformed data URIs.
- Test that inlined image bytes survive a round trip.
- Keep the existing Aspose tests.
- Update `DocConverterConfigTest` for a sidecar URL, no URL, dummy mode, and Aspose-only mode.
- Test that the sidecar URL beats Aspose settings and that startup requires both capabilities.

**Check:** run `./mvnw -f docker/conversion-sidecar/pom.xml test`, `mvn test -Dfast=true`,
then `mvn spotless:apply`.

---

## 2. Update properties, flags, and backend callers

Add these defaults to `defaultDeployment.properties`.

```properties
conversion.url=
conversion.cacheConverted=true
conversion.connectionRequestTimeoutMs=2000
conversion.connectTimeoutMs=5000
conversion.responseTimeoutMs=185000
conversion.maxInputBytes=209715200
conversion.maxOutputBytes=314572800
conversion.maxHtmlBytes=52428800
conversion.use.dummy.converter=false
```

Keep the existing `aspose.*` properties under a clear deprecation comment. Keep `aspose.enabled=false` as the default.

Mirror the new properties in `deployments/dev/deployment.properties`. Leave the URLs empty and add a comment that points to the Docker dev stack.

Do not add `conversion.*.type`. Each role has one target product.

- **`IPropertyHolder` and `PropertyHolder`**
  - Add `isConversionEnabled()` and return true when `conversion.url` is set or Aspose is
    explicitly enabled.
  - Add `isConversionCachingEnabled()`.
  - Use `conversion.cacheConverted` for new backends and `aspose.cacheConverted` for Aspose.
  - Keep old Aspose helpers only where the compatibility path still needs them.
  - Check `IMutablePropertyHolder` for matching setters.

- **Controllers**
  - Update `BaseController:436` to use the new helpers.
  - In `WorkspaceController` and `NotebookEditorController`, replace `asposeEnabled` with
    `conversionEnabled`.
  - In `DeploymentPropertiesController:164,248`, add one `conversion.enabled` property. Both
    frontend capabilities derive from it because one validated sidecar always provides both roles.
  - Keep `aspose.enabled` only for legacy callers. Mark it deprecated and remove active frontend callers.

- **`FileDownloadController`**
  - Rename the Aspose cache helper.
  - Accept only `outputformat=pdf` in `convertFile`.
  - Return a bundle-based 400 error for other formats.
  - Keep the existing checksum cache and `convertedDocs-` categories.
  - Keep cache lookup and download after the existing record permission check.
  - Do not expose cache-hit state or cache identifiers to the conversion backend.
  - Validate the complete PDF before writing a cache entry. Publish through an atomic rename
    from a deployment-private temporary file so failures and disconnects never leave a partial
    hit. Include the conversion-policy version in the cache namespace so results created before
    output hardening are not silently reused.

- **Word Import and Export**
  - Preserve Word Import support for `.doc`, `.docx`, `.odt`, `.rtf`, and `.txt`, and add `.ott`.
    Reject other inputs before calling the conversion backend.
  - In `MSWordProcessor`, change `doc` output to `docx`.
  - Replace the image-file and ZIP flow with the streaming inliner.
  - End export filenames with `.docx`.
  - In `MSWordImporter`, run `DataUriExtractor` after conversion.
  - Write the extracted images where `HTMLContentProvider` expects them.
  - Remove the Aspose CLI workaround that forbids spaces in folder names.

- **Errors and tests**
  - Add bundle keys for unavailable conversion, failed conversion, invalid output format,
    unsupported Word import format, and oversized imports.
  - Test every accepted Word Import extension and reject `.dot` and `.dotx` without calling the
    conversion backend.
  - Update `FileDownloadControllerTest` for the new properties and mocks.

**Check:** run `mvn test -Dfast=true`, then `mvn clean test`.

---

## 3. Update frontend flags and file lists

- In `workspace.jsp` and `notebookEditor.jsp`, replace the Aspose data flag with
  `data-conversion-enabled`.
- In `global.js`, replace `RS.asposeEnabled` with `RS.conversionEnabled`.
- Preserve the existing "From Word" and preview extension lists. Add `.ott` to Word Import and
  `.odp` to preview. Convert Markdown through the sidecar's text-filter compatibility path.
- Rename `CallableAsposePreview.tsx` to `CallableDocumentPreview.tsx`.
- Rename `supportedAsposeFile` to `supportedPreviewFile` and remove Aspose from error text.
- Update `primaryActionHooks.ts` to use the new names, raw text URL, and conversion flag.
- Rename `asposeEnabled` props in `ToolbarCreateMenu.tsx` and the Workspace and Notebook toolbars
  to `conversionEnabled`.
- Use `conversionEnabled` to control "From Word" and Word export.
- Update names in `Carousel.tsx` and `PdfPreviewDialogEntrypoint.tsx`.
- Update `FormatChoice.test.tsx` and `PdfPreviewDialogEntrypoint.test.tsx`.
- Update related test IDs and strings.

**Check:** run `pnpm run tsc`, `pnpm run test`, and `pnpm run lint`.

---

## 4. Update the dev stack and documentation

- Add the tested Gotenberg 8.34.0 LibreOffice-only image to
  `docker/dev/docker-compose.yml`, pinned by multi-architecture manifest digest with a version comment. Configure
  the body, timeout, queue, route-disable, download-disable, webhook-disable, and outbound-deny
  controls from section 1. Never use mutable `:8` in release
  or development Compose files.
- Build and run the sidecar in `docker/conversion-sidecar/` as `conversion-sidecar`. The image is
  the only supported sidecar runtime; do not add host startup scripts or a non-container runbook.
- Define `conversion-sidecar` and `gotenberg` as a co-located pair on a private Compose network
  used only by that pair. Give the Gotenberg container the `gotenberg` network alias so the dev
  stack exercises the proxy route. Do not publish Gotenberg's port or attach its private network to the
  RSpace container. Apply the same topology in production with Docker Compose or an equivalent
  same-host container tool.
- Use the HTTP server stack and pinned Java, Spring Boot, Tomcat, and JODConverter versions
  from section 1. Pin the LibreOffice and runtime base image versions by digest.
- Build native `linux/amd64` and `linux/arm64` images from Ubuntu 26.04. Run the conversion
  suite on native hosts and publish one multi-architecture manifest only when both pass.
- Include the LibreOffice notices, matching source-package link, package inventory, and SBOM
  in the released image metadata.
- Do not add a custom document-format registry. Set the Writer filter inside the service.
- Run both converters without a persistent volume and with the least-privilege container settings
  from section 1. Deny all Gotenberg and LibreOffice-worker egress. Permit the sidecar HTTP process
  to reach only the paired `gotenberg:3000` service. Use separate size-limited temporary
  volumes for request files and LibreOffice profiles.
- Pass `conversion.url=http://conversion-sidecar:8080` to the app.
- In production, set `conversion.url` to the HTTP endpoint assigned by the secure tunnel. Do not
  expose or route the sidecar outside that tunnel.
- Follow the existing property flow through `entrypoint-app.sh`.
- Update `docker/dev/README.md` and the deployment documentation in this PR. Do not start the
  stack automatically. Document the single supported topology: a same-host sidecar/Gotenberg
  container pair on a private network. Explain that the pair may be dedicated to one RSpace
  deployment or shared by several, plus secure-tunnel setup, pair-wise scaling, and conversion
  limits. Every RSpace deployment routes through a private sidecar; Gotenberg is always private
  behind that sidecar. Do not put private keys or passwords in the image, repository, logs, or
  environment examples.
- Generate and publish an SBOM, provenance, vulnerability report, and signature for every
  platform image. Verify digest and signature before deployment and document the security-update
  promotion and rollback process.
- State that converted-file caches belong to one RSpace deployment. Shared object storage
  requires separate roots and storage-enforced credentials for each deployment. Identify
  Gotenberg and JODConverter as the target conversion stack and Aspose as deprecated but
  temporarily supported.

Run `rg -i "aspose"` over `src/`, `docker/`, and `DevDocs/`.

Aspose may remain only in compatibility code, tests, config, migration docs, historical changesets, and fixtures.

Delete stale test resources only when no test uses them. Keep the `word2rspace` fixtures.

**Check:** run `mvn clean package -DgenerateReactDist -DskipTests=true`. Confirm the full WAR builds and the search results are clean.

---

## 5. Verify the complete PR

Run the backend and frontend against real containers. Use the `rspace-dev-stack` skill to start the Docker dev stack.

1. Upload DOCX, XLSX, PPTX, and ODT files. Check that previews render as PDF.
2. Preview the same file again. Confirm it uses the cache and creates no conversion log entry.
3. From a second RSpace deployment, preview the exact same file. Confirm it performs its own
   conversion through the same shared sidecar, then uses only its own cache on the next
   preview.
4. Check that an uploaded DOCX gets a thumbnail through the PDF-to-PNG chain.
5. Preview TXT, Markdown, and CSV fixtures as PDFs through the sidecar.
6. Import DOC, DOCX, ODT, OTT, RTF, and TXT fixtures through Workspace → Create → From Word. For
   image-heavy fixtures, check the document, image order, and Gallery items.
7. Export a document with images and a table. Check that the `.docx` opens in LibreOffice and contains the images.
8. Clear `conversion.url` and disable Aspose. Restart, then check that previews, thumbnails,
   Word Import, and Word Export are disabled. Confirm `POST /api/v1/import/word` returns 422.
9. Test a legacy Aspose setup with `conversion.url` empty. Check that preview, DOCX import, and DOCX export still work.
10. Add the sidecar URL. Check that Gotenberg and JODConverter take priority over Aspose.
11. Send valid requests without an Authorization header, then send malformed and oversized
    requests and confirm validation still rejects them. Confirm an invalid production URL or
    tunnel-host configuration prevents startup and no client follows a redirect or retries a
    conversion POST. Confirm the sidecar never forwards caller authorization headers to
    Gotenberg. Point `conversion.url` directly at Gotenberg and confirm the sidecar
    protocol check prevents startup.
12. Start the conversion stack only through its container definition. Confirm no setting can
    replace the fixed `http://gotenberg:3000` origin, Gotenberg has no published host port, and
    stopping the paired Gotenberg container makes its sidecar unready. If testing multiple pairs,
    start two complete pairs and confirm each sidecar can reach only its own Gotenberg container.
13. Submit the archive-bomb, traversal, macro, external-link, active-HTML, spoofed-image,
    oversized-output, huge-page, timeout, disconnect, and cross-request canary fixtures. Confirm
    fail-closed responses, no unauthorized egress, no active imported content, no partial cache entries, and
    complete temporary-file cleanup.
14. Verify image digests/signatures, SBOM/provenance publication, and that vulnerability scanning
    has no unreviewed high or critical runtime finding.
15. Run `mvn clean verify -Denvironment=drop-recreate-db`.

The PR is ready to merge only when every section above is implemented and all checks pass. Do
not defer code, configuration, deployment files, documentation, or tests to a follow-up PR.

---

## Out of scope

- Importing `dot` or `dotx` files.
- Removing Aspose in this change.
- Building a streaming data-URI splitter before the size limit is reached in practice.
- Database or Liquibase changes.
