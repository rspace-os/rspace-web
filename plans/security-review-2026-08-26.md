# Product security review

Date: 2026-08-26  
Baseline commit: `cd30de982`  
Scope: the full RSpace application, frontend, backend, conversion sidecar, Docker development deployment, dependencies, and CI workflows. The review included uncommitted worktree changes present on the review date.

## Outcome

The review retained 1 critical, 9 high, and 6 medium risks after source-level verification. The first remediation work should address hostile imports, SQL injection, missing record authorization, password and token storage, and the LDAP findings where LDAP is enabled.

This was a broad code review with bounded testing against the Docker development stack. It was not a production penetration test. Production proxy rules, identity-provider configuration, cloud IAM, backup controls, and secret-store policies were not available.

Severity reflects the product attack path supported by the source. Development-only behavior, disabled features, scanner severity without reachability, and general hardening advice are not counted as product findings.

## Confirmed and conditional weaknesses

| # | Severity | Finding and evidence | Mitigation |
|---:|---|---|---|
| 1 | High | **Imports can write outside their temporary directory.** ZIP entries are extracted without checking the normalized destination. Imagine a ZIP file with two entries: `documents/experiment.xml` and `../../shared-overwrite.txt`. If the extraction root is `/tmp/rspace-import-123`, the first entry stays below that directory. The second entry resolves to `/tmp/shared-overwrite.txt`, so the extractor writes archive-controlled bytes outside its assigned directory. Uploaded archive filenames and Evernote attachment filenames are also joined directly to temporary directories. This establishes an authenticated arbitrary-file-write path within the application account's filesystem permissions. It does not by itself establish code execution. Evidence: `src/main/java/com/researchspace/service/archive/ArchiveParserImpl.java:254`, `src/main/java/com/researchspace/service/impl/ExportImportImpl.java:361`, `src/main/java/com/researchspace/document/importer/EvernoteEnexImporter.java:63`, and `rspace-core-util:2.0.0` `ZipUtils.unzipFolder`. | Generate server-side filenames. Normalize every destination and require it to remain below the extraction root. Reject absolute paths, traversal components, and links. Limit entry count, individual size, total expanded size, and expansion ratio. Patch or replace the affected parsing dependencies. |
| 2 | High | **Evernote imports can disclose local files and use an unsafe second XML parse.** Imported note markup preserves attacker-controlled marked links. The importer opens their `href` value as a local file. This local-file path is established. The dependency's second-stage `SAXBuilder` does not explicitly disable DTDs or external entities, although an XXE payload was not run. Evidence: `src/main/java/com/researchspace/document/importer/EvernoteEnexImporter.java:82` and `rspace-evernote-parser:2.0.0` `LinkUpdater`. | Associate attachments only through parser-generated digest records. Never open a path from note markup. Disable DTDs and external entities, set external DTD and schema access to empty, and require all attachment paths to remain below the extraction directory. |
| 3 | Critical, conditional | **LDAP fallback login permits shell command injection.** When fallback DN calculation is enabled, the unauthenticated username is interpolated into an `ldapsearch` command executed through `/bin/sh -c`. Evidence: `src/main/java/com/researchspace/ldap/impl/LdapSearchCmdLineExecutor.java:35`. | Remove the shell fallback and use Spring LDAP. If it must remain temporarily, invoke an argument array without a shell, RFC4515-escape the filter, restrict usernames, clear the child environment, and enforce a short timeout. |
| 4 | High | **Legacy form sorting permits SQL injection.** Request-controlled `orderBy` passes a small character blacklist and is appended to native SQL. A bounded check confirmed that `name,rand()` reached the database and returned HTTP 200. An unknown column produced HTTP 500. The typed Forms REST API uses a sort allowlist and is not part of this finding. Evidence: `src/main/java/com/researchspace/webapp/controller/RSFormController.java:432`, `src/main/java/com/researchspace/dao/hibernate/FormDaoHibernate.java:202`, and `rspace-core-util:2.0.0` `IPagination.ORDERBYBLACKLIST`. | Map an external sort enum to hard-coded SQL column fragments. Reject unknown fields with HTTP 400. Do not sanitize arbitrary SQL identifiers with a blacklist. |
| 5 | High | **The autosave endpoint lacks record authorization.** It retrieves fields for any record ID without passing a user or checking permission. The controller is also mounted below the anonymous public-view prefix. An anonymous request returned HTTP 200, although no content was present because the selected record had no active autosave. Evidence: `src/main/java/com/researchspace/webapp/controller/StructuredDocumentController.java:698`, `src/main/java/com/researchspace/service/impl/FieldManagerImpl.java:55`, and `src/main/webapp/WEB-INF/security.xml:44`. | Require the requesting user at the service boundary and check `READ` permission before loading fields. Separate public-view and authenticated editing controllers. Add two-user and anonymous-public-link tests. |
| 6 | High, conditional | **The LDAP dependency permits empty-password authentication bypass.** The product uses Spring LDAP 2.3.4 and passes blank credentials to the bind operation. Evidence: `src/main/java/com/researchspace/auth/LdapRealm.java:56`, `src/main/java/com/researchspace/ldap/impl/UserLdapRepoImpl.java:123`, and [CVE-2026-41720](https://github.com/advisories/GHSA-jrv5-8w28-4265). | Reject null, empty, and blank LDAP passwords before lookup or bind. Upgrade to a supported fixed Spring LDAP release. Add empty-password and whitespace-password regression tests. |
| 7 | High | **Archive imports bypass rich-text sanitization.** Normal autosave sanitizes text fields. Archive import assigns HTML directly, and the journal view later renders text-field data as HTML. The cited Inventory API path stores HTML but its relevant browser renderer applies DOMPurify, so it is not part of this finding. Evidence: `src/main/java/com/researchspace/service/impl/RecordManagerImpl.java:624`, `src/main/java/com/researchspace/service/archive/AbstractImporterStrategyImpl.java:464`, and `src/main/java/com/researchspace/webapp/controller/JournalController.java:233`. | Apply sanitization at a mandatory ELN text-field write boundary used by autosave and archive import. Contextually encode non-HTML output. Review existing imported content before any stored-content migration. |
| 8 | High, conditional | **Enabled webhook integrations permit server-side request forgery.** A regular user can store a webhook URL when the integration is available. The sender checks only URI syntax before making a server-side request. Evidence: `src/main/java/com/researchspace/extmessages/base/AbstractExternalWebhookMessageSender.java:48` and `src/main/java/com/researchspace/webapp/controller/IntegrationController.java:197`. | Allow only the exact HTTPS hosts needed by each integration. Reject userinfo, IP literals, loopback, private, link-local, and metadata destinations. Revalidate DNS results and redirects. Use a managed Spring HTTP client with timeouts and enforce network egress policy. |
| 9 | Medium | **The CSRF origin check accepts lookalike origins.** Origin and Referer values are checked with string-prefix matching. Any localhost port is trusted. The check is defective, but the report did not establish a complete cross-site request with a sensitive effect. Evidence: `src/main/java/com/researchspace/webapp/filter/OriginRefererCheckerImpl.java:56`. | Parse the URI and compare exact scheme, normalized host, and effective port. Remove production localhost trust. Use synchronizer CSRF tokens, with exact origin checks and SameSite cookies as secondary controls. |
| 10 | High | **Passwords use single-round salted SHA-256.** Evidence: `src/main/java/com/researchspace/auth/ShiroRealm.java:37` and `src/main/java/com/researchspace/core/util/CryptoUtils.java:28`. | Use a versioned Spring `PasswordEncoder`. Prefer Argon2id, with bcrypt or PBKDF2 as alternatives. Rehash on successful login and force-reset dormant accounts. |
| 11 | High | **Stored integration tokens use a fixed deployment key by default and AES-ECB without authentication.** Evidence: `src/main/resources/deployments/defaultDeployment.properties:311`, `src/main/java/com/axiope/service/cfg/BaseConfig.java:883`, and `rspace-core-model:3.3.1` `SymmetricTextEncryptor`. The credential value is intentionally omitted. | Fail startup when the default or a missing key is used. Store keys in a secret manager or KMS. Use a versioned AES-GCM or ChaCha20-Poly1305 envelope with a random nonce and a documented rotation process. |
| 12 | Medium, conditional | **Slack callbacks use obsolete shared verification tokens.** They do not verify a request signature, timestamp, or replay. The OAuth exchange also places credentials in the query string. OAuth `state` is already generated and checked. Evidence: `src/main/java/com/researchspace/webapp/integrations/slack/SlackController.java:164` and `src/main/java/com/researchspace/webapp/integrations/slack/SlackController.java:377`. | Verify the HMAC over the raw body, check timestamp freshness, block replays, validate callback types and response hosts, and send OAuth credentials in a POST body. |
| 13 | Medium, conditional | **Enabled signup CAPTCHA fails open on verifier failure or misconfiguration.** A missing user response is rejected. If a response is present, missing keys, verifier exceptions, and non-2xx responses permit signup. Evidence: `src/main/java/com/researchspace/service/impl/SignupCaptchaVerifierImpl.java:43`. | Validate the CAPTCHA configuration at startup with `@Validated @ConfigurationProperties`. If CAPTCHA is enabled, return a retryable error when verification is unavailable. |
| 14 | Medium, conditional | **SMTP transport permits plaintext operation.** STARTTLS is off by default and is not required when enabled. This may expose SMTP credentials, password-reset links, and notifications on deployments that configure mail without protected transport. Evidence: `src/main/resources/deployments/defaultDeployment.properties:157` and `src/main/java/com/researchspace/service/impl/EmailBroadcastImpl.java:278`. | Enable STARTTLS by default, require it, and enable server identity checks. Fail startup when credentials are configured without protected transport. |
| 15 | Medium | **Dependency scans identify packages that need remediation and reachability review.** `pnpm audit --prod` reported 1 critical, 4 high, and 2 moderate advisories for `websocket-driver`, `fast-uri`, DOMPurify, and NanoID. Maven resolves confirmed affected versions of Spring LDAP and Commons FileUpload. Other old Maven packages listed by the original scan do not constitute verified product vulnerabilities without an applicable advisory and reachable code path. | Upgrade the confirmed affected dependencies. Record the dependency path and reachable application feature for each advisory. Remove unused transitive packages and run dependency checks in CI. |
| 16 | Medium | **GitHub Actions use mutable version tags.** This includes third-party actions and the privileged `pull_request_target` CLA workflow. Evidence: `.github/workflows/cla.yml:5` and the workflows below `.github/workflows/`. | Pin every action to a reviewed full commit SHA. Minimize workflow and PAT permissions. Use automated pull requests to update pinned SHAs. |

## Reviewed areas without a product finding

| Area | Result |
|---|---|
| Native office and PDF conversion | The development conversion sidecar runs as non-root with a read-only filesystem, Bubblewrap isolation, disabled LibreOffice macros and networking, seccomp, a private Gotenberg network, and container resource caps. These controls apply to the inspected Docker deployment. |
| Public links | Public link values use a cryptographically secure random generator. The shared anonymous principal still increases the impact of finding 5, but no weakness was found in link generation. |
| Development cookies and headers | Direct Jetty responses lacked explicit session-cookie attributes and several browser security headers. Production containers and the TLS proxy control these settings, so the development observation is not counted as a product finding. |
| API abuse controls | API and upload-rate throttling default off. Individual multipart uploads retain a 50 MB default limit. Enabling rate limits is secure-default work, not evidence of a standalone vulnerability. |

## Runtime evidence

- An authenticated request confirmed that the harmless `name,rand()` form sort expression reached SQL and returned HTTP 200. An invalid column produced HTTP 500.
- An anonymous request to the public autosave endpoint returned HTTP 200. It returned no fields because the selected record had no active autosave.
- JavaScript could read the development session cookie, and direct Jetty responses lacked the reviewed security headers. These observations remain development-only and are not findings.
- The conversion sidecar was healthy and its end-to-end conversion path was operational.

## Tests deliberately excluded

The review did not execute any test likely to cause high memory or CPU use. It did not test archive bombs, oversized uploads, concurrency floods, expensive parser inputs, or unbounded data generation.

The review also did not execute the LDAP shell injection, arbitrary filesystem writes, metadata-service SSRF, unsafe external callbacks, or local-file disclosure payloads. Source inspection provided enough evidence without altering the environment or risking data outside the test account.

## Recommended remediation order

1. Fix findings 1, 2, 4, 5, 7, 10, and 11 before the next production release.
2. Fix findings 3 and 6 immediately on deployments that enable LDAP. Disable the affected LDAP paths until the fixes ship.
3. Fix finding 8 on deployments that enable user-configured webhooks. Then address findings 9 and 12.
4. Address findings 13 through 16 as secure-default and dependency-maintenance work.
5. Add regression tests at each affected trust boundary.

Dependencies between fixes:

- Central ELN text-field sanitization in finding 7 should land before stored-content migration.
- The URL-validation component for finding 8 should be shared with external filestore and callback validation where their policies match.
- Password migration in finding 10 needs a versioned stored format before rehash-on-login can start.
- Dependency upgrades in finding 15 should follow focused compatibility tests for LDAP, archive import, HTTP clients, and JSON parsing.

## External references

- [NIST SP 800-63B password guidance](https://pages.nist.gov/800-63-4/sp800-63b.html)
- [OWASP CSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
- [OWASP SSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)
- [Slack request verification](https://api.slack.com/docs/verifying-requests-from-slack)
- [GitHub Actions secure-use guidance](https://docs.github.com/en/actions/reference/security/secure-use)
- [Spring LDAP CVE-2026-41720](https://github.com/advisories/GHSA-jrv5-8w28-4265)
- [websocket-driver advisory](https://github.com/advisories/GHSA-xv26-6w52-cph6)
- [DOMPurify advisory](https://github.com/advisories/GHSA-55q2-fjhq-7xh7)
