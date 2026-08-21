# LibreOffice for the JODConverter service

Research date: 2026-07-11.

## Recommendation

Publish one OCI image tag with native `linux/amd64` and `linux/arm64` manifests. Build the runtime image from the Ubuntu 26.04 LTS official image and install these packages from `resolute-updates`:

- `openjdk-25-jre-headless`
- `libreoffice-core-nogui`, `libreoffice-writer-nogui`, `libreoffice-common`, and `libreoffice-java-common`
- `fontconfig`, `fonts-opensymbol`, `fonts-crosextra-carlito`, `fonts-crosextra-caladea`, `fonts-liberation2`, `fonts-dejavu-core`, `fonts-noto-core`, `fonts-noto-cjk`, and `fonts-noto-color-emoji`

Ubuntu 26.04 currently supplies LibreOffice 26.2.4 updates and Java 25 for both target architectures. Its `-nogui` packages are intended for server or command-line use, so X11 or Xvfb is unnecessary. The official Ubuntu image also publishes both architectures. [Ubuntu LibreOffice 26.2 packages](https://packages.ubuntu.com/resolute-updates/editors/libreoffice), [Ubuntu Java 25 package](https://packages.ubuntu.com/resolute-updates/java/openjdk-25-jre-headless), [Ubuntu official image](https://hub.docker.com/_/ubuntu/tags)

A native `linux/arm64` build check confirmed this path: `ubuntu:26.04` successfully installed and started `openjdk-25-jre-headless`, `libreoffice-writer-nogui`, and the fixed font set. It reported OpenJDK 25.0.3, LibreOffice 26.2.4.2 Build 2, and `aarch64`. A final `dpkg-query` formatting mistake made the overall shell command exit unsuccessfully after those checks; it does not invalidate the successful package installation or Java and LibreOffice startup.

Use JODConverter `jodconverter-local-lo:4.4.11`. Configure an explicit writable working directory and one private LibreOffice profile per worker. Set a fixed locale, timezone, and font set, run `fc-cache` while building, and test the resulting image natively on both architectures.

This is preferable to assembling TDF tarballs manually: the OS package manager resolves native libraries, provides headless package splits, and supplies security updates. TDF binaries remain a useful compatibility-test input.

## Current LibreOffice releases and ARM64 availability

The only maintained upstream line is 26.2, currently at 26.2.5 and scheduled to reach end of life on 2026-11-30. The 25.8 line ended on 25.8.7 and reached end of life on 2026-06-12. Although old 25.8 archives may remain downloadable and some TDF page wording still calls it the previous or mature branch, it must be treated only as an obsolete comparison, not as a maintained fallback. [TDF 26.2 release plan](https://wiki.documentfoundation.org/ReleasePlan/26.2), [TDF 25.8 release plan](https://wiki.documentfoundation.org/ReleasePlan/25.8), [TDF release notes](https://www.libreoffice.org/release-notes/), [TDF stable download index](https://download.documentfoundation.org/libreoffice/stable/)

TDF now publishes official Linux ARM64 binaries. LibreOffice 26.2.4 has both `deb/aarch64` and `rpm/aarch64` downloads, alongside the x86-64 packages. Therefore ARM64 is no longer limited to source builds or distribution packages. [LibreOffice download selector](https://www.libreoffice.org/download/), [26.2.4 ARM64 deb](https://download.documentfoundation.org/libreoffice/stable/26.2.4/deb/aarch64/LibreOffice_26.2.4_Linux_aarch64_deb.tar.gz), [26.2.4 ARM64 rpm](https://download.documentfoundation.org/libreoffice/stable/26.2.4/rpm/aarch64/LibreOffice_26.2.4_Linux_aarch64_rpm.tar.gz)

An empirical native-arm64 check verified the TDF 26.2.4 archive's SHA-256 and successfully started Java 25. On a minimal Temurin Jammy base, `soffice` first failed because `libXinerama.so.1` was absent. A second attempt added common X, font, D-Bus, and CUPS libraries, progressed past Xinerama, and then failed because NSS's `libssl3.so` was absent. The archive is therefore a valid ARM64 artifact but not a self-contained headless runtime; using it requires discovering, installing, and maintaining its native shared-library closure explicitly.

Distribution-native choices also exist:

- Ubuntu 26.04 updates publish LibreOffice 26.2.4 for amd64 and arm64. [Ubuntu package metadata](https://packages.ubuntu.com/resolute-updates/editors/libreoffice)
- Debian 13 stable publishes security-patched 25.2.3 packages for both architectures; `trixie-backports` currently has 26.2.4. Debian warns that backports are less extensively tested and are provided as-is. [Debian stable package](https://packages.debian.org/trixie/libreoffice), [Debian backports package](https://packages.debian.org/trixie-backports/libreoffice-writer-nogui), [Debian Backports policy](https://backports.debian.org/)

Do not use LibreOffice Online for this service. JODConverter's local module drives a locally installed, headless desktop LibreOffice process through UNO; LibreOffice Online is a separate remote architecture.

## Java, JODConverter, and architecture compatibility

JODConverter 4.4.11 requires Java 8 or later and recommends the latest stable LibreOffice. Java 25 is therefore within its declared runtime range, although the exact Java 25 plus LibreOffice 26.2 combination still needs the Phase 0 conversion suite because the project does not publish a tested-version matrix for every future JVM and LibreOffice release. [JODConverter requirements](https://jodconverter.github.io/jodconverter/latest/getting-started/system-requirements/), [4.4.11 release notes](https://jodconverter.github.io/jodconverter/latest/release-notes/release-notes-4.4.11/)

JODConverter itself is architecture-neutral Java bytecode. The `jodconverter-local-lo` module depends on JODConverter local code plus LibreOffice's Java API artifact; it does not bundle a native LibreOffice executable. The installed `soffice` process and its native libraries must match the container architecture. Communication and conversion properties use the LibreOffice UNO API, normally over a loopback socket or named pipe. [4.4.11 local-lo build](https://github.com/jodconverter/jodconverter/blob/v4.4.11/jodconverter-local-lo/build.gradle.kts), [JODConverter configuration overview](https://jodconverter.github.io/jodconverter/latest/configuration/), [LocalConverter configuration](https://jodconverter.github.io/jodconverter/latest/configuration/local-converter/)

Version 4.4.11's LibreOffice Java dependency is 24.8.4, but the installed office process may be newer. UNO's client boundary avoids CPU-specific JNI coupling; it does not guarantee that every filter behaves identically across LibreOffice releases. Validate Word-to-HTML, HTML-to-DOCX with `HTML (StarWriter)`, embedded images, malformed inputs, timeouts, and worker cleanup on both architectures before pinning an image. [JODConverter dependency versions](https://github.com/jodconverter/jodconverter/blob/v4.4.11/gradle/libs.versions.toml), [JODConverter supported conversions](https://jodconverter.github.io/jodconverter/latest/)

## RSpace integration dependency baseline

The RSpace WAR remains on Java 17 and its existing dependency major lines. Apply available stable
minor and patch upgrades to Spring Framework 6.2.19, Apache HttpClient 5.6.4, PDFBox 3.0.8,
Tika 3.3.2, jsoup 1.23.1, Commons IO 2.22.0, and Commons Compress 1.28.0. The clients must reuse
these managed dependencies rather than introduce another HTTP, PDF, MIME-detection, HTML-parsing,
or archive library. In particular, use Spring's synchronous `RestClient` over the explicitly
configured HttpClient 5 transport; do not copy the legacy Aspose client's default `RestTemplate` and
retrying `SimpleResilienceFacade` pattern.

The sidecar container is built and shipped separately from the RSpace WAR. Keep its
dependencies at their latest stable validated versions. Import the Spring Boot 4.1.0 dependency
management and override a managed dependency only for a documented security or compatibility
reason. Use Apache HttpClient 5.6.4 for the required constrained Gotenberg proxy. Spring Boot
4.1.0 supports Java 25 and manages JUnit Jupiter 6.0.3, so tests should use
the managed Jupiter API instead of pinning a second JUnit version. [Spring Boot system
requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Spring Boot managed
dependencies](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html)

## Packaging and update policy

The supported runtime is the sidecar container image, deployed beside one Gotenberg container on
the same machine and private container network. The executable JAR is an image build artifact, not
a supported host-process distribution. Build, test, deploy, and scale the sidecar and Gotenberg as
one pair; do not configure a remote Gotenberg upstream.

Use a native multi-platform build rather than QEMU-built release artifacts. Build the application once per target architecture, run the conversion tests on native amd64 and arm64 runners, and publish a manifest list only after both pass. Pin the Ubuntu base by digest in release builds and record resolved package versions and an SBOM in image metadata.

Generate provenance and a vulnerability report for each platform image, sign the manifest, and
promote only the tested digest. Fail release publication on an unresolved critical or high
vulnerability in a runtime component unless a time-bounded, reviewed exception records why the
finding is not exploitable. Verify the signature and digest in deployment configuration; never
deploy a mutable major-version tag.

Do not freeze LibreOffice indefinitely at a package version. Rebuild on base-image, Java, LibreOffice, or font security updates, then promote the new digest after the cross-architecture conversion suite passes. This trades byte-for-byte rebuild reproducibility for timely security fixes while preserving a rollback digest. For strict reproduction, retain the package repository snapshot used by each release.

TDF tarballs offer exact upstream parity between architectures but require manual dependency installation and a separate update watcher; the native ARM64 attempts exposed successive Xinerama and NSS dependencies even after adding common runtime libraries. Debian stable offers strong conservative patching but remains on an older upstream feature line. Debian backports is current but has weaker testing/support guarantees. Ubuntu 26.04 LTS currently gives the best combination of a maintained LibreOffice line, native headless packages, Java 25, ARM64 parity, and normal security updates, and the native ARM64 installation/startup check succeeded.

## Fonts and headless runtime

Font availability changes pagination, line wrapping, and export appearance. Carlito is metric-compatible with Calibri and Caladea with Cambria; LibreOffice's Debian/Ubuntu metadata also recommends Liberation, DejaVu, and Noto families. Keep the same font package versions on both architectures and fail image verification if the expected `fc-list` inventory differs. [Debian LibreOffice font recommendations](https://packages.debian.org/trixie/arm64/libreoffice), [Carlito package](https://packages.debian.org/trixie/fonts-crosextra-carlito), [Caladea package](https://packages.debian.org/trixie/fonts-crosextra-caladea)

Use the `-nogui` Writer packages, `SAL_USE_VCLPLUGIN=svp`, a read-only root filesystem, and request-private writable directories for `HOME`, the LibreOffice profile, and conversion files. Do not install desktop integration, help packs, dictionaries, Base, Calc, or Impress unless a tested input requires them. Debian describes `libreoffice-core-nogui` specifically as the server/command-line build and makes fontconfig and OpenSymbol hard dependencies. [LibreOffice core-nogui](https://packages.debian.org/trixie/amd64/libreoffice-core-nogui), [LibreOffice writer-nogui](https://packages.debian.org/trixie/libreoffice-writer-nogui)

Treat every office file as hostile. Start each conversion with macro execution disabled, link
updates disabled, an empty private profile, no network namespace access, and no visibility of
another request's files. Run as a non-root user with all Linux capabilities dropped,
`no-new-privileges`, a read-only root, a restrictive seccomp/AppArmor profile, and explicit
CPU, memory, PID, temporary-storage, queue, and wall-clock limits. The test corpus must include
macro-bearing files, external and local links, malformed XML, path traversal names, and archive
bombs; success means the content is rejected or converted without executing code, fetching a
resource, or reading another path.

## Licensing and redistribution

JODConverter 4.4.11 is Apache-2.0 licensed. LibreOffice is distributed under MPL-2.0 and includes components under other open-source licenses. Redistributing it inside an image is permitted, but the image must retain notices and provide recipients a reasonable way to obtain the corresponding source for the exact executable version. Preserve `/usr/share/doc/*/copyright`, publish the image's license inventory/SBOM, link the matching Ubuntu source package, and include the LibreOffice MPL notice. [JODConverter license](https://github.com/jodconverter/jodconverter/blob/v4.4.11/LICENSE), [LibreOffice licenses](https://www.libreoffice.org/licenses/), [LibreOffice legal information](https://api.libreoffice.org/share/readme/LICENSE.html)

The LibreOffice name may identify an unmodified component of the service, but the image and service must not imply TDF endorsement. [TDF trademark policy](https://wiki.documentfoundation.org/TradeMark_Policy)

## Acceptance gate

Before adopting the image, verify on native amd64 and arm64:

1. Java 25 starts JODConverter 4.4.11 and detects the packaged LibreOffice installation.
2. The Phase 0 DOCX converts to HTML with all embedded images intact.
3. HTML converts to DOCX using `HTML (StarWriter)`, and its images survive a PDF render.
4. Representative output has equivalent structure and visual layout across architectures; byte identity is not required.
5. Malformed and oversized documents cannot escape the worker sandbox or leave profiles and temporary files behind.
6. Macro-bearing and externally linked documents cannot execute a macro, fetch a URL, or read a local file.
7. Archive-entry, expanded-byte, compression-ratio, output-byte, CPU, memory, PID, queue, and timeout limits fail closed.
8. Image metadata records the Java, LibreOffice, JODConverter, OS-package, font-package, base-image, and per-platform image digests, plus the SBOM and provenance references.
