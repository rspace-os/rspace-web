# HTTP-only document conversion replaces Aspose with Gotenberg and JODConverter

The target architecture removes Aspose (the CLI jar and the hosted microservice). The first
release keeps the existing Aspose path as an explicitly enabled, deprecated fallback so
deployments can migrate; new conversion code never fails over to it after a request has started.
RSpace sends all document conversion over HTTP to one conversion sidecar selected by
`conversion.url`:

- **PDF role**: the sidecar proxies its fixed LibreOffice route to private Gotenberg, which makes
  PDFs for Office Previews and Document Thumbnails. Thumbnails are made by converting to PDF, validating the result, then
  rendering the first page to PNG with the local `PDFToImageConverter`.
- **Word role**: the sidecar's private JODConverter implementation handles Word
  Import (DOCX, ODT, or OTT to sanitized HTML) and Word Export (HTML with authorized images
  inlined to `.docx`).

Gotenberg and the conversion sidecar are stateless containers deployed only as a co-located pair
on the same machine. Docker Compose or an equivalent container tool connects them through a
private container network. The sidecar has no supported non-container mode, and Gotenberg is never
a remote or independently scaled service. One pair provides both roles and may serve multiple
RSpace deployments. RSpace reaches the sidecar's HTTP interface only through a private container
network or secure tunnel. The sidecar accepts valid requests without an Authorization header and
applies separate service-wide PDF and Word capacity limits. Deployments must not publish the
sidecar to an untrusted network. Gotenberg is private to its paired sidecar and is never an RSpace
configuration target. An empty `conversion.url` selects the legacy Aspose fallback when Aspose is
enabled. Conversion is unavailable only when `conversion.url` is empty and Aspose is disabled.

The sidecar is a small, owned HTTP wrapper around `jodconverter-local-lo`, not the upstream
sample REST application. It also exposes Gotenberg's fixed LibreOffice route and
proxies it to the Gotenberg container on its private same-host network. Owning this seam is
necessary to enforce
fixed routes, archive validation, one-shot profiles, per-role capacity limits, safe
errors, and request isolation for untrusted documents without creating a general-purpose proxy.

All uploaded and generated documents are untrusted. Gotenberg and each LibreOffice worker have
no outbound network access. The sidecar HTTP process may connect only to the paired
`gotenberg:3000` service on its private container network. Neither backend has persistent storage or unnecessary
Linux capabilities, and both have bounded request, expanded-archive, output, CPU, memory,
process, queue, and execution limits. RSpace validates
the returned media type and structure before persisting or caching a result. Imported HTML is
allowlist-sanitized and extracted images are content-checked before they become Gallery items.

## Considered options

- **Reusing the LibreOffice inside Gotenberg for Word Import/Export**: rejected. Gotenberg's API only outputs PDF (and screenshots). Reaching its internal LibreOffice means maintaining a fork forever. We accept that Gotenberg and JODConverter both ship a copy of LibreOffice.
- **JODConverter as the only backend** (it can also make PDF and PNG): rejected for the PDF role. Gotenberg is purpose-built as a conversion API and easier to operate.
- **Pure Java libraries (Mammoth, docx4j-ImportXHTML), no second container**: rejected.
  Mammoth only reads DOCX and does not cover the accepted OpenDocument formats; the export
  path would still require a separate implementation.
- **Embedding jodconverter-local in the webapp (zero containers)**: rejected even though it covers every needed conversion. It puts crash-prone LibreOffice processes, their memory use, and the risk of parsing hostile documents on the application host, inside the webapp's lifecycle. It also brings back a host-installed binary (the same problem as the Aspose jar). The backends exist for isolation, not features.
- **Keeping a host-local CLI mode**: rejected. The sidecar is supported only as one member of the
  container pair. Deployments that cannot run containers are not supported.
- **Running Gotenberg remotely or scaling it independently**: rejected. Same-host private
  networking keeps Gotenberg unreachable from RSpace and makes deployment, health, and capacity
  ownership explicit. The complete sidecar/Gotenberg pair is the scaling unit.

## Consequences

- Word Export now produces `.docx` (was `.doc`).
- CSV, Markdown, and plain-text files are converted to PDF for preview. Markdown is submitted to
  LibreOffice with a `.txt` extension so that its text filter handles the content consistently.
- Word conversion quality is lower than Aspose (LibreOffice engine everywhere). Accepted.
- Properties are named for the capability (`conversion.*`), not the product. This breaks with the existing `aspose.*` convention on purpose: the vendor name had leaked into about 30 files across every layer.
- Images cross the Word backend as base64 data URIs (+37% transfer, on import/export only).
  Responses are streamed to disk; HTML parsing and image extraction have explicit byte, count,
  and pixel budgets so heap use remains bounded.
- Keep RSpace WAR dependencies within their existing major lines, applying available stable
  minor and patch upgrades: Spring Framework 6.2.19, Apache HttpClient 5.6.4, PDFBox 3.0.8,
  Tika 3.3.2, jsoup 1.23.1, Commons IO 2.22.0, and Commons Compress 1.28.0. The conversion
  sidecar is built and shipped separately and uses the latest versions selected by its own
  Spring Boot dependency management.
- Two HTTP dialects exist on the sidecar: its constrained Gotenberg-compatible LibreOffice route
  and its two `/v1/convert` Word routes. RSpace never reaches Gotenberg directly.
