---
status: proposed
---

# Argon2id password hashing with a login verification concurrency limit (RSDEV-894)

## Context

RSpace stores login passwords as a single round of SHA-256. `ShiroRealm` and
`UsernamePasswordCredentialsMatcher` both construct Shiro's `HashedCredentialsMatcher` with
no iteration count, so Shiro's default of one round applies, and `UserManagerImpl.changePassword`
writes `Sha256Hash(plainText, salt)` with a per-user 16-byte salt. The salt stops rainbow tables
and cross-account matching. It does nothing to slow guessing: a commodity GPU tries on the order
of ten billion salted SHA-256 candidates per second, so an eight-character password falls in
hours once the hash table is read. This is CWE-916, insufficient computational effort.

Not every stored hash is salted. The production seed in `initial-seed-run.sql` creates
`sysadmin1`, `admin` and the anonymous guest with `salt = NULL` and an unsalted hash, and the
dev seeds do the same with uppercase hex. Both verify paths already branch on a null salt.

The weakness was first raised in 2019 (RSPAC-1914). It became urgent in August 2026 when
RSDEV-1328 gave any authenticated user a SQL read primitive over the `User` table, including the
`password` and `salt` columns. RSDEV-1328 is fixed in 1.124.3 and 1.126.0, so the live chain is
closed, but a fast hash turns the next read primitive into immediate credential recovery. This
change is defence in depth against that next one.

Two facts about the deployment shape drove the decision. Instances are small, a few hundred
users and at most around 500. And RSpace upgrades already apply irreversible Liquibase changes
(RSDEV-444 discarded Spring Batch history and reseeded `hibernate_sequences`), so customers who
roll back already restore a pre-upgrade database backup.

A memory-hard hash creates a new problem that SHA-256 never had. Argon2 allocates its configured
memory block on the Java heap for every verification and holds it for the duration, tens of
milliseconds. The login form is unauthenticated, usernames used to leak from several public
routes (`sysadmin1` ships with every install, the signup page confirms whether a username
exists, and the login page answered unknown names faster than known ones; RSDEV-1558 closes the
remaining routes), and the account lockout in `DefaultLockoutPolicy` only
registers after a failed verification completes. A burst of concurrent login requests therefore
forces concurrent allocations bounded only by the servlet thread pool, around 200 threads. At
64 MiB per verification that is 12.8 GB of transient heap, enough to stall or kill the JVM for
every user, not just the attacker.

## Decision

**Algorithm.** Login passwords and the SSO and Community verification password are hashed with Argon2id through Spring Security's
`Argon2PasswordEncoder`, wrapped in a `DelegatingPasswordEncoder` so every stored value carries
an `{id}` prefix and a future change of algorithm or parameters is configuration, not a format
migration. Parameters are 19 MiB of memory (19456 KiB), 2 iterations, parallelism 1, a 16-byte
salt and a 32-byte hash. This is the OWASP Password Storage Cheat Sheet's stated minimum for
Argon2id. The encoder id is RSpace's own (`{argon2@rspace_v1}`), not Spring's default `{argon2}`,
so a later retune is distinguishable from the first. The wrapper, `RSpacePasswordEncoder`,
registers only this id, the legacy id below and `{bcrypt}` for verification passwords set before
this change, so a stored value with an unknown or missing prefix fails closed rather than
matching. `{bcrypt}` only reads, never encodes, and `UsernamePasswordCredentialsMatcher` refuses
it for login passwords.

**Migration.** Every existing hash is upgraded at rest, once, by a Liquibase custom change
(`WrapLegacyPasswordHashes_RSDEV894`) that runs on the first application start after upgrade.
For each row with a non-null `password` that does not already start with `{`, the change decodes
the stored SHA-256 hex to its 32 bytes, hashes the Base64 of those bytes with Argon2id, and
stores `{argon2-legacy-sha256@rspace_v1}<base64 salt>$<argon2>` (an empty salt for unsalted
rows). The salt now lives inside the wrapped value and the `salt` column is set to null.
Verification for a legacy row recomputes the SHA-256 exactly as Shiro did (salt bytes first when
present, then the UTF-8 password, one round) and feeds the Base64 of the digest to Argon2.
Because Argon2 sees the decoded bytes and not the hex string, the case of the stored hex cannot
matter, just as it did not under Shiro. Both the salted and the unsalted seed variants are
covered. A row whose password is not 64 hex characters, or whose salt is not valid Base64, is
left unchanged and logged at ERROR by username; that user needs an administrator password reset.
Rows with a null password are not touched. Rows already carrying a prefix are skipped, so a rerun
changes nothing, and at 500 rows and roughly 50 ms each the change completes in well under a
minute. The wrapped form is permanent. A password check never writes, so a legacy row stays
`{argon2-legacy-sha256@rspace_v1}` until its owner changes their password, and Argon2 over the
old SHA-256 is as hard to guess as Argon2 over the password itself. The change logs its row count
and Argon2 time at INFO, so every upgrade leaves a timing data point in the server log.

Verification passwords were bcrypt. A second custom change,
`PrefixBcryptVerificationPasswords_RSDEV894`, runs after the login wrap and prefixes every bare
bcrypt `verificationPassword` (`$2a`, `$2b`, `$2y`) with `{bcrypt}`, skipping values that already
carry a registered id. Any other non-blank value could never verify, so it is cleared and logged
at ERROR by username, and that user sets a new verification password. A `{bcrypt}` value is
permanent, like the wrapped login hash: it is read through the shared encoder and never rewritten,
and only a new verification password is stored as Argon2id. The change logs its row counts at
INFO.

**Password verification concurrency limit.** Login password verification, at Shiro login and
at default-realm reauthentication (signing, witnessing, password change, API key and OAuth client
management, the OAuth password grant, sysadmin actions and operate-as), goes through a
`BoundedPasswordVerifier` bean that wraps the shared encoder bean with a fair
`java.util.concurrent.Semaphore`. The verifier refuses an input longer than `User.MAX_PWD_LENGTH`
as a wrong password before taking its lock or a permit. Permits and wait are deployment properties,
`login.passwordVerification.maxConcurrent` (default 8) and `login.passwordVerification.waitSeconds`
(default 5). At the default, peak Argon2 heap is 8 times 19 MiB, about 152 MiB, regardless of
request volume. A request that cannot get its turn before the wait elapses fails with the same
generic failure the user sees for a wrong password, but through a distinct exception type,
`LoginVerificationBusyException`. The login filter (`StandaloneShiroFormAuthFilterExt`) and
`ReauthenticatorImpl` catch it before any failure is recorded, so a flood cannot lock legitimate
users out. A check waiting for a permit holds its request thread for up to `waitSeconds`, so under
a flood the thread pool, not the heap, is the next limit, and a refused check costs the attacker
nothing; lowering `waitSeconds` trades honest users' waits for thread capacity. Encoding new passwords is not bounded at the encoder; instead the anonymous routes
that reach it (sign-up, Google sign-up on Community, LDAP first-login auto-signup, and the login
and verification password-reset replies) take a permit from one shared pool in front of it (`password.anonymousEncode.maxConcurrent`, default 4), held
through the hash and the save and refused immediately when none is free, with a reset token left
usable. The reset replies also accept only an unused, unexpired token. Anonymous Argon2
allocation therefore cannot exceed 4 x 19 MiB, about 76 MiB, or about 228 MiB with the login
verifier's permits. The default is small because each permit is also a busy core. Authenticated and sysadmin encodes (password change,
user creation, CSV and archive import) remain unbounded by choice, since they require an account
and a bound there would have to either refuse or block every caller of the shared encoder. The
at-rest migration runs serially at startup and is not bounded either.

SSO and Community verification password checks go through the same verifier and pool, so the
rules below apply to them too. Changing the verification password checks the current one through
`IReauthenticator.reauthenticateWithVerificationPassword`, which applies the busy handling but
never substitutes an operating-as sysadmin. Only LDAP users, who reauthenticate against the
directory, skip the verifier; that path is as it was on main.

One further rule stops a single account from monopolising the pool, and it writes nothing to the
database: verifications are serialised per username ahead of the semaphore. One username has at
most one verification in flight, so one account can hold at most one permit, and a burst of
parallel requests for a known username hashes one at a time. One deadline covers both the
per-username lock and the permit, so the total wait never exceeds the configured seconds. The
login-form lockout in `DefaultLockoutPolicy` is unchanged; failed reauthentication is not counted
toward it, and there is no other per-account rate limit on reauthentication (see Consequences).
Without the per-username rule, an authenticated low-privilege user scripting wrong passwords at
the sign endpoint could hold every permit indefinitely and deny login to the whole instance.

Only login and reauthentication degrade under attack. Authenticated sessions and the rest of the
application are unaffected.

## Considered options

- **Upgrade on login instead of at rest.** Rehash each account when its owner next logs in and
  force a reset for accounts that never do. Keeps rollback clean and avoids a startup migration.
  Rejected: at 500 users the at-rest migration costs seconds, so the only remaining benefit is
  rollback safety, which RSpace upgrades do not offer anyway. The price is fast SHA-256 hashes
  left at rest for every user who has not logged in since the upgrade, plus a user-facing reset
  campaign to retire them. The chosen design also keeps a legacy verify format until every user
  has logged in once, but every value in that format is already behind Argon2, so it needs no
  reset campaign.
- **Hybrid: upgrade on login, then a scheduled job wraps stragglers.** Rejected for the same
  reason once the at-rest cost was shown to be trivial at this scale.
- **bcrypt.** Already in the tree for the SSO verification password and needs no BouncyCastle.
  Rejected: bcrypt caps input at 72 bytes, which held `User.MAX_PWD_LENGTH` at 50, and it is
  not memory-hard. Argon2id is the current OWASP first choice.
- **Shiro's own Argon2 hash** (Shiro is at 3.0.0 on main since RSDEV-1291). RSDEV-921 assumed
  this route, which is why Shiro 2.x was called a blocker. Shiro 2 onwards does make Argon2id the
  `DefaultPasswordService` default and stores it in a parameter-carrying `$shiro2$` format, so it
  is a genuine alternative, not a strawman.
  Rejected because the deciding part of this ticket is the legacy wrapper, not the algorithm.
  Shiro's hash-format registry expects standard hashes, so Argon2-over-SHA-256 with an external
  salt needs a custom `HashFormat` or a custom `CredentialsMatcher` in front of `PasswordMatcher`
  anyway. Spring's delegating encoder makes the legacy format an ordinary `PasswordEncoder` under
  its own id.
  The `shiro-hashes-argon2` artifact would also be new to the tree, needs BouncyCastle just the
  same, and `spring-security-crypto` is already a dependency used for the verification password.
- **OWASP's higher memory profiles (46 MiB or 64 MiB).** Stronger per hash. Rejected: the
  memory parameter multiplies directly into the login flood exposure, and the 19 MiB, 2 iteration
  profile is OWASP's baseline, not a weaker fallback.
- **Per-IP rate limiting instead of a concurrency limit.** Rejected as the primary control:
  it needs the real client address, which behind a customer's reverse proxy means trusting
  `X-Forwarded-For`, and it does nothing against a distributed source. It does not cap the heap.
  The semaphore does, and is blind to addresses. The two are complementary, not alternatives.
- **Per-window rate limiter on the anonymous routes** (10 per 5 seconds, one each for signup
  and the reset replies, none on Google sign-up). Implemented and replaced: it counts admissions on a clock, not hashes
  in flight, so admissions either side of a refresh overlap and it bounds rate, not heap.
- **Rely on the servlet thread pool.** Rejected: 200 threads times 19 MiB is 3.8 GB, above
  the heap most customer instances run with.
- **Re-encode a wrapped hash as plain Argon2id on the next successful login.** Tidier at rest,
  and Spring's `upgradeEncoding` invites it. Rejected after implementation: a password check must
  not write. The write had to run in its own `REQUIRES_NEW` transaction so a failed upgrade could
  not poison the caller's, and under MariaDB's default snapshot isolation (11.6 onwards) that
  committed write made the caller's later locking read of the same row fail with "Record has
  changed since last read", breaking the CSV user import on startup. The wrapped form is as strong
  as plain Argon2id, so there is nothing to gain.
- **Count failed reauthentications on the `User` row** with the login failure counter, refusing
  reauthentication after four failures in two minutes. Rejected for the same reason: saving the
  row from inside the caller's transaction failed the `@Version` check in the OAuth password-grant
  controllers and changed the operate-as wrong-password path. Setting `accountLocked` was ruled out
  earlier still, because API and SSO logins treat that flag as a disabled account and the OAuth
  password grant reaches reauthentication before the client is validated, so four bad grants could
  disable any user.
- **Hold the account for one second after a wrong guess**, in memory, as the stateless
  replacement for the lockout above. Rejected after review: the delay slept on the servlet worker
  while holding the per-username lock, so an anonymous burst of wrong guesses for one account could
  park one Jetty worker per request for up to the five-second wait and starve requests unrelated
  to authentication. Refusing instead of sleeping would have let one wrong guess per second deny an
  account's correct logins. Neither is worth the one property it bought, a per-account guess cap
  at endpoints without lockout, so reauthentication keeps only the per-username rule.
- **Bound new-password encoding inside the shared encoder**, with its own semaphore, either
  refusing or blocking when full. Raised in review: anonymous signup and the password-reset reply
  reach `encode()` outside the verifier's permits. Rejected: a refusing bound turns into exception
  handling in every caller of the encoder (signup, two reset replies, two password-change
  endpoints, CSV upload, API user creation, archive import) and partial failures in bulk imports; a
  blocking bound parks servlet workers under flood, the same shape as the one-second delay above.
  The anonymous routes are capped in front of the encoder instead (the signup limit and the
  single-use reset token under Decision), and the authenticated routes are left unbounded because
  they require an account.

## Consequences

The user-visible effect of every rule in this ADR, scenario by scenario with the exact messages,
is tabulated in [PasswordHashingScenarios.md](../DeveloperNotes/PasswordHashingScenarios.md).
That table must change in the same commit as any limit, default, message or route it describes.

- The upgrade is irreversible. A release downgraded past this change cannot read
  `{argon2@rspace_v1}`, `{argon2-legacy-sha256@rspace_v1}` or `{bcrypt}` values, so nobody can
  log in and SSO and Community users cannot sign or witness. Downgrade requires restoring the
  pre-upgrade database. This is in the release notes.
- First start after upgrade is slower by the wrap time, under a minute at 500 users.
- Login password rows the migration cannot parse are left as they were and logged at ERROR by
  username. Those users cannot log in until an administrator resets their password. Unusable
  verification passwords are cleared instead, and those users set a new one.
- Three encoder ids exist permanently: two in `password` and `{bcrypt}` in `verificationPassword`.
  A legacy row stays as it is until its owner changes that password. The legacy verify path,
  including Shiro's exact byte ordering for the salted hash, was pinned by tests against fixtures
  generated with the old Shiro code and then hard-coded, before that code was removed. The salted
  `CryptoUtils.hashWithSha256inHex` helper is gone.
- `Argon2PasswordEncoder` requires BouncyCastle. Two `bcprov` lines are already on the compile
  classpath: `bcprov-jdk15on` 1.70, a direct dependency since the initial commit and the final
  release of that line, and `bcprov-jdk18on` 1.84, transitive through Shiro 3's
  `shiro-crypto-hash`. Both ship the same `org.bouncycastle` packages, so which copy of
  `Argon2BytesGenerator` loads is a classpath-order accident. Two `bcpkix` versions (1.52 via the
  Box SDK, 1.81.1 via Tika) sit on top. This change declares `bcprov-jdk18on` 1.84 explicitly,
  pinned to Shiro's version, which leaves the resolved artifact set unchanged. Retiring
  `jdk15on` and consolidating onto one BouncyCastle line is RSDEV-1544.
- With bcrypt gone from the write path, `User.MAX_PWD_LENGTH` rises from 50 to 128 for both
  passwords, including the sysadmin user creation API (`ApiUserPost`). The verification password
  migration shipped as a second, stacked PR under the same ticket, keeping the login path, whose
  failures surface within minutes, apart from the verification path, whose failures would surface
  as SSO customers unable to sign or witness documents.
- Under a login flood, legitimate users see slow or failed logins for the duration. That is the
  intended failure mode, replacing an out-of-memory JVM.
- The reauthentication path, verification passwords and sysadmin operate-as share the
  `BoundedPasswordVerifier` and so share the permit pool with login, so a login flood also slows
  document signing. Both are authenticated and low volume.
- Reauthentication, including verification password checks, has no per-account rate limit beyond
  one check in flight at a time. The anonymous OAuth password grant (`/oauth/token`) checks the
  user's password before validating the client, so an unregistered client can try on the order of
  10 to 40 passwords per second against one account, bounded only by Argon2 cost and the
  per-username lock, and can tell a right password from a wrong one by the error it gets. On
  `main` the same route hashed every guess with no limit. Validating the client before the
  password closes it and is a separate ticket against `main`.
- Usernames must not leak from unauthenticated endpoints. Argon2's cost would make the login
  page answer an existing username measurably slower than an unknown one, so `ShiroRealm` runs
  an unknown name (and, when LDAP is off, an LDAP-source user) through `SentinelPasswordCheck`, an
  Argon2 check against a random hash made at startup. On LDAP installs `LdapRealm` pads an
  internal user's early exit the same way; parity with a directory bind is best effort. All
  padded checks share one per-username lock, so they hold at most one permit. Other public routes
  that still confirm a username are RSDEV-1558.
- Deferred to follow-on tickets:
  - RSDEV-1558: username existence still leaks from the sign-up form, the reset and reminder
    timing, the disabled-account redirect and the API token route's account-state messages; this
    change closes only the login page.
  - RSDEV-1559: `User.salt` is write-only after the wrap and is dropped in a later release.
  - RSDEV-1560: `RequestUtil.remoteAddr` trusts `X-Forwarded-For`, so address-based throttling of
    busy refusals (and of the API token route, see RSDEV-1557) waits on a trusted-proxy list.
