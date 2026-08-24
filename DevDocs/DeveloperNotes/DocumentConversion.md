# Document conversion

RSpace uses a conversion sidecar for document preview, Word import, and Word export. The sidecar
sends Office-to-PDF work to a private Gotenberg container and runs
JODConverter with a new LibreOffice process and profile for each Word conversion.

```text
RSpace --> conversion sidecar --> Gotenberg --> PDF
                    |
                    +--> JODConverter/LibreOffice --> HTML or DOCX
```

RSpace does not connect to Gotenberg directly. The sidecar accepts only the routes and multipart
parts used by RSpace:

| Route | Input | Output |
| --- | --- | --- |
| `POST /forms/libreoffice/convert` | One `files` part | PDF |
| `POST /v1/convert/html` | One `file` part | Self-contained HTML |
| `POST /v1/convert/docx` | One `file` part | DOCX |
| `GET /v1/capabilities` | No body | Protocol and role declaration |

Every Word conversion starts LibreOffice through a rootless bubblewrap sandbox. The process gets a
private network namespace, a read-only view of the LibreOffice runtime, and access only to its own
request directory plus the local UNO pipe directory. Sandbox startup failure rejects the request;
there is no unsandboxed fallback.

## Supported RSpace workflows

- Gallery and ELN preview converts CSV, DOC, DOCX, Markdown, TXT, XLS, XLSX, PPT, PPTX, ODT, ODS,
  ODP, and RTF to PDF. PDF files pass through without conversion. Markdown uses LibreOffice's text
  filter because Gotenberg does not accept `.md` as an Office extension.
- Word import accepts DOC, DOCX, ODT, OTT, RTF, and TXT. Imported images become RSpace Gallery
  images after the sidecar returns self-contained HTML.
- Word export converts one RSpace document or notebook entry to DOCX.
- Office thumbnails convert through PDF before the existing PDF-to-PNG renderer runs.

Legacy Aspose properties remain as a compatibility fallback. A non-empty `conversion.url` takes
precedence, so a deployment cannot send the same request to both systems.

## Configuration

Set these properties in the deployment properties file:

```properties
conversion.url=http://conversion-sidecar:8080
conversion.cacheConverted=true
conversion.connectionRequestTimeoutMs=2000
conversion.connectTimeoutMs=5000
conversion.responseTimeoutMs=185000
conversion.maxInputBytes=209715200
conversion.maxOutputBytes=314572800
conversion.maxHtmlBytes=52428800
```

`conversion.url` must be an absolute HTTP origin. Host filtering belongs to the deployment firewall
and secure-tunnel configuration rather than application DNS checks. RSpace disables redirects and
retries, and checks the sidecar protocol and protocol version at startup. The sidecar does not
authenticate callers, so keep it on a private container network and do not publish it to the host or
internet. Gotenberg is attached only to the internal `conversion` network.

The sidecar defaults live in `application.properties`. Spring Boot also accepts each setting as an
environment variable, for example `converter.max-output-bytes` becomes
`CONVERTER_MAX_OUTPUT_BYTES`. Docker deployments can use environment variables for every sidecar
setting and keep the properties file as the defaults.

The sidecar property `converter.max-concurrent-office-conversions` defaults to `2`. It is a hard
per-process limit with no waiting queue. When every LibreOffice slot is occupied, the sidecar
returns HTTP 429 with `Retry-After: 1`, and RSpace displays its localized service-busy error.
Actuator records active and rejected work as `rspace.conversion.office.active` and
`rspace.conversion.office.rejected`.

## Error contract

The sidecar does not return internal diagnostic text to RSpace. Each failed request returns an
`application/problem+json` response and an `X-RSpace-Conversion-Error` header. The `code`, `title`,
and `detail` fields contain the same logical error code. The problem body also includes a generated
`requestId` for future OpenTelemetry integration.

| Code | Meaning |
| --- | --- |
| `conversion.failed` | Conversion failed without a more specific result. |
| `conversion.input-invalid` | The source file or request is invalid. |
| `conversion.input-too-large` | The source file exceeds an input or expansion limit. |
| `conversion.output-invalid` | A converter returned missing, malformed, or unexpected output. |
| `conversion.output-too-large` | Converted output exceeds its size limit. |
| `conversion.service-busy` | Every conversion slot is occupied. |
| `conversion.service-unavailable` | A converter or required sidecar resource is unavailable. |
| `conversion.timeout` | Conversion exceeded its time limit. |
| `conversion.unsupported` | The requested input or output format is unsupported. |

RSpace accepts only codes in `DocumentConversionError`. It maps an unknown code from an older or
incompatible sidecar to a general error based on the HTTP status. Before returning an error to an
RSpace client, the controller resolves the code's `errors.documentConversion.*` key with the
request locale.

The container runtime must permit unprivileged user namespaces and the mount syscalls used by
bubblewrap. The development Compose service uses an unconfined seccomp profile for that reason,
while retaining a read-only root filesystem, `no-new-privileges`, and an empty capability set. A
production runtime can instead use a custom seccomp profile that permits only the bubblewrap
syscalls.

## Input and output boundaries

The sidecar rejects unexpected multipart fields, query parameters, caller-supplied Gotenberg
controls, unsupported extensions, and oversized files. Before Word import, it also rejects unsafe
DOCX, ODT, and OTT packages, including nested archives, macro artifacts, and external OOXML
relationships. It caps archive entries, expanded bytes, response bytes, and conversion time.
Temporary request directories are removed after a response, a disconnect, or a conversion failure.
For PDF conversions, the sidecar generates a correlation ID and sends it to Gotenberg as
`Gotenberg-Trace`. This keeps the tracing seam without accepting caller-supplied Gotenberg headers.

RSpace validates the returned media type and size before publishing an atomic output file. It
opens generated PDFs with PDFBox and checks generated DOCX files as ZIP packages. Word import
removes active HTML, external image references, and invalid or oversized data-URI images before it
creates Gallery records.

## Tests

- `docker/conversion-sidecar/src/test/java`: request-shape, capability, and archive validation
  tests.
- `PdfConversionClientTest`, `JodConverterClientTest`, and `DataUriExtractorTest`: RSpace response
  validation and import sanitation tests.
- `documentConversion.e2e.ts`: Docker-backed PDF preview, DOCX import with three images, and DOCX
  export.

Run the browser test from the repository root after `./docker/dev/rspace-dev up --e2e`:

```bash
RSPACE_BASE_URL=http://localhost:<app-port> pnpm run test-e2e \
  src/__tests__/e2e/specs/gallery/documentConversion.e2e.ts \
  --project=chromium --reporter=list
```
