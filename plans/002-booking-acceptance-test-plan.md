# Booking system acceptance test plan

**Original baseline:** `6916ea9a7`.
**Coverage updated:** 23 September 2026, through `f778e3205`, including inherited Inventory permissions, dashboard/calendar filtering, scheduling and display timezones, and personal notification subscriptions.
**Current execution target:** https://booking-system-demo.researchspace.com/booking. Record the deployed build separately; local test results do not prove that the same build is deployed.

## Purpose and release gate

Validate that a user can discover, book, manage, and share bookable resources safely, and that administrators can configure their lifecycle and permissions. Acceptance requires all P0 cases to pass, no unresolved data-loss or authorization defect, and the performance thresholds below to hold in a production-shaped environment.

## Test environments and data

- Run against a clean MariaDB-backed deployment with the branch migrations applied, representative production build, and audit logging enabled.
- Use at least: sysadmin, booking owner, booking manager, ordinary authorised user, ordinary unauthorised user, and a user with a private calendar subscription.
- Seed: active and archived instruments, multiple locations, opening-hours rules, duration limits, overlapping and adjacent slots, confirmed/cancelled/maintenance events, DST boundary dates, users/groups with different roles, and 1,000+ catalogue items / 10,000+ bookings for scale runs.
- Test desktop Chromium plus Firefox/Safari-equivalent coverage, 320px mobile width, keyboard-only navigation, 200% zoom, and screen-reader smoke checks.
- Record initial settings, feature flags, account roles, subscription choices, and deployed version. Use uniquely named test instruments and bookings. Record all remaining fixtures and restore shared settings after each scenario.
- Verify captured email using a deployment email catcher or authorized test mailbox. An API response, in-app message, or backend unit test alone is not evidence of email receipt.
- Confirm authorization for shared deployment feature-flag changes, large fixture creation, sustained load and failure injection before executing them. Missing infrastructure or mailbox access is a blocked case, not a pass.

## Functional acceptance matrix

| ID | Priority | Scenario and expected result |
|---|---|---|
| F01 | P0 | Browse all bookable items; search/filter/sort/paginate; URL state survives reload and back/forward; empty, loading, and API-error states are understandable. |
| F02 | P0 | Open an item and see availability in the selected timezone; verify opening hours, maximum duration, existing bookings, maintenance, cancelled events, and unavailable periods are represented consistently. |
| F03 | P0 | Create a valid booking at minimum, maximum, adjacent, and boundary durations; confirmation shows target, time, timezone, purpose, requester, and resulting status. |
| F04 | P0 | Reject overlap, outside opening hours, over-duration, past-time, invalid timezone, and unavailable-target attempts without creating partial records. |
| F05 | P0 | Concurrent booking attempts for the same slot yield exactly one confirmed booking; loser receives a recoverable conflict and refreshed availability. |
| F06 | P0 | View booking details, edit permitted fields, save, discard, and recover from stale/concurrent modification; audit event is recorded once per committed change. |
| F07 | P0 | Cancel own booking where policy allows; verify idempotent retry, correct status, calendar visibility, and audit trail. Verify users cannot cancel others’ bookings without capability. |
| F08 | P0 | Owner/manager configure opening hours, duration and scheduling policy; sysadmin manages global defaults. Personal display preferences validate independently. Saved feedback clears on subsequent edits; Cancel and navigation guards preserve the expected saved/draft state. |
| F09 | P0 | Inherited Inventory access matrix: owner, editor/booker, reader, unrelated user and sysadmin receive the intended read/write controls and API status. Booking's Access tab is read-only and directs permission changes to Inventory. Revoke/restore sharing and transfer ownership; direct URL/API calls must recheck current permissions. |
| F10 | P0 | Archive active configuration: future confirmed bookings and maintenance events become cancelled atomically, historical rows remain readable, ordinary DELETE is retry-safe, and archived item cannot accept new bookings. |
| F11 | P0 | Restore archived configuration with permitted role; verify availability/configuration return correctly. Permanent delete is available only to sysadmin acting as self, one configuration at a time, retains audit history, and is not inferred from repeated ordinary DELETE. |
| F12 | P1 | My Bookings list: filters, columns, sorting, pagination, relation links, details navigation, cancellation, no-results and server-error states. |
| F13 | P1 | Calendar feed/download and private subscriptions: subscribe/unsubscribe/rotate with ETag preconditions, correct DTSTART/DTEND/timezone/status, current Inventory access and privacy shaping. Full-read users may see booking details; inaccessible item data must be redacted from retained personal history. Revoked item feeds stop working and require a new subscription after restored access. Check private/no-store and referrer headers and user-feed isolation. |
| F14 | P1 | Boundary dates: DST spring-forward/fall-back, midnight, month/year rollover, leap day, locale formatting, browser timezone differing from booking timezone. |
| F15 | P1 | API contract: OpenAPI resources, filtering/sorting, pagination, validation errors, authentication, optimistic concurrency, relationship redaction, and stable error codes match the MVC/contract tests. |
| F16 | P1 | Refresh, duplicate submit, browser back/forward, expired session, network timeout, 4xx/5xx, and retry do not duplicate bookings or lose committed changes. |
| F17 | P2 | Audit log exposes actor, timestamp, action, target, before/after-relevant values, archive/delete events, and is itself access-controlled. Respect completed-day snapshots: today's mutations appear after the next UTC day boundary, so test historical fixtures and record same-day visibility as pending. Deleted-configuration audit requires sysadmin and an available target Inventory item. |
| F18 | P0 | Feature flags: with Booking off for the deployment or current user, every booking route, including Settings and deep links, exits gracefully to /workspace; APIs deny access consistently. Test direct load, client navigation, cached data and a flag change during a session, then restore the flag. |
| F19 | P1 | Default entry /booking opens Dashboard and its menu item is present. Upcoming and other tables without filterable fields have no empty filter toolbar. Dashboard summaries, calendar and lists agree after create/edit/cancel. |
| F20 | P1 | Search coverage: test instrument name/global ID/location/owner on applicable catalogue and Settings pages; purpose, instrument and requester fields on My Bookings where supported. Use exact IDs, punctuation, spaces, diacritics, empty input and unknown values. Relationship pickers respect read permissions and do not omit eligible matches. |
| F21 | P1 | Shared TableList filters, relationship filters, sorting, pagination and saved views round-trip through the URL. My Items means instruments actually owned by the caller, including for sysadmin. Calendar ownership/focus filters and Available Now/Free Later Today use the same visible resource set. Changing filters clears inappropriate bulk selections. |
| F22 | P0 | Invalid date/query URLs, impossible dates, unsupported enum values and malformed filters produce a safe fallback or understandable error without crashing. Check Dashboard, Calendar and booking creation, reload and back/forward. |
| F23 | P0 | Scheduling timezone is selectable only when creating a bookable instrument. Omitted API value uses institution timezone; aliases such as Etc/UTC remain selectable. Invalid/blank zones fail validation. Single/bulk updates cannot change it; edit forms do not offer the field. |
| F24 | P1 | Browser, Institution and Custom display preferences produce explicit IANA-zone/UTC-offset labels. Test browser, institution and instrument zones all differing. Opening hours and booking rules still use instrument scheduling time; date boundaries and selected availability window follow display time. Invalid timezone/window preferences do not persist. |
| F25 | P1 | Instrument-local time tooltips appear on applicable timeline events, availability summaries, lists, booking details, form dates and conflict messages when instrument time differs from display or browser time. Hover and keyboard focus work without breaking clicks, expansion, links or date entry. Hidden/null timezone produces no tooltip or private metadata. DST crossings show the correct offset at each endpoint. |
| F26 | P0 | Compact and full booking forms handle click/drag selection, midnight ordering, minimum/maximum duration, DST gaps and repeated hours. Distinguish past start, invalid window, conflicts and policy warnings. Explain the actual display timezone and refresh conflicts after a rejected write. |
| F27 | P1 | Calendar focus/pagination/sticky resource controls retain correct row/date alignment. Item configuration opens in view mode. All calendar-list buttons/groups have consistent height and no redundant timezone column. |
| F28 | P1 | Read-only users can inspect permitted booking information and manage their personal notification choice without gaining booking/configuration rights. Inaccessible item relationships use an understandable fallback and suppress invalid item links/actions; permitted personal booking history remains usable. |
| F29 | P1 | Audit pagination and large snapshots remain bounded and readable; missing/unreadable audit files fail safely. Compare actor and before/after values for fixture mutations; access revocation must prevent subsequent audit reads. Include audit block-read behavior in S05 measurements. |

## Notification acceptance matrix

Instrument subscriptions are personal and separate from private calendar feed subscriptions. Delivery also depends on current instrument access, Booking availability, the matching My Profile event preference and the separate email preference.

| ID | Priority | Scenario and expected result |
|---|---|---|
| N01 | P0 | A newly bookable instrument initializes its actual owner's subscription from the owner's automatic preference. A non-owner starts Off; merely reading a setting must not create/reset it. Changing the automatic default affects future instruments only. |
| N02 | P0 | Any eligible reader can save their own On/Off choice; another user's subscription cannot be addressed through payload fields. Stale single-item version returns 409 and refreshes the saved choice. Missing/invalid boolean/version and unknown fields fail without mutation. |
| N03 | P1 | Item Save/Cancel and Preferences automatic On/Off radios have accessible labels, disabled/pending states and saved feedback. Editing clears Saved; Cancel restores the saved choice. Help text links directly to My Profile notification preferences and explains global delivery gates. |
| N04 | P0 | Bulk subscribe/unsubscribe applies to the selected instruments atomically, up to 100 IDs. Empty/oversized/unauthorized batches fail without partial writes; repeated/duplicate IDs behave consistently. Success count and cleared selection match the actual result. |
| N05 | P0 | Unsubscribe-all persists explicit Off for all existing subscriptions, including dormant access-lost rows; repeat is a no-op. Automatic default, global event preferences and email preference are unchanged. A later new instrument may still auto-subscribe when the default is On. |
| N06 | P0 | Another user's committed booking creation/cancellation produces exactly one in-app message per eligible subscriber and one email when enabled. Actor receives no self notification; maintenance and ordinary edits/rescheduling do not notify. Unsubscribed users receive neither channel. |
| N07 | P0 | Creation and cancellation global preferences gate their events independently. Email Off suppresses email only, preserving in-app messages. Instrument On remains saved when global preferences are Off. Disabled/inactive/ineligible recipients do not receive subsequent notifications. |
| N08 | P0 | Invalid booking, rejected overlap, stale mutation, rolled-back transaction and idempotent cancellation retry do not emit extra messages/emails. Concurrent winning booking produces one event delivery per recipient. Verify actual delivery after commit; a response alone is insufficient. |
| N09 | P0 | Sharing revocation suppresses later delivery; restoration resumes saved On. Ownership transfer initializes only a previously absent new-owner choice, retaining explicit Off. Former owner with On continues only if still readable. Archive/restore and replacement configuration preserve the instrument-level choice. |
| N10 | P1 | Eligibility is selected from committed preferences/access at the event snapshot. Changes before selection apply; an event already selected may still deliver after a concurrent unsubscribe/revocation. Subsequent events must use the new state. Exercise with controlled concurrency when instrumentation is available. |
| N11 | P1 | Email retains recipient-zone booking times at event generation; Browser mode email falls back to institution time. Dashboard and notification popup render in current recipient display preferences, using session browser timezone when available. Check DST offsets, old messages and escaped instrument names. |
| N12 | P1 | Notification badge, popup and dashboard list agree. Mark-as-read removes the correct message and updates the count; reload/pagination do not resurrect it. Message display and marking read never send email. |

## Deployment and shared controls

| ID | Priority | Scenario and expected result |
|---|---|---|
| D01 | P1 | Record deployment/build identity and enabled flags; confirm the new schema/API/UI are deployed. A missing new endpoint/control is a deployment gap, not evidence of acceptance. Development fixtures remain controlled by the deployment flag. |
| D02 | P2 | Authorized DevTools access works on React AppShell booking pages and Inventory/Gallery with the RSpace logo. Unauthorized users do not gain access. Production build inclusion and visibility follow the intended feature-flag policy. |
| D03 | P1 | Shared radio select works via visible labels and keyboard, honors disabled/read-only/option-disabled states, exposes group name/description, and leaves existing select/card widgets usable. |

## Usability and accessibility acceptance

1. A first-time user can find an item, understand why a slot is unavailable, and complete a valid booking without instructions; observe five moderated users and target ≥4/5 successful completion.
2. Labels, errors, status, timezone, duration, and destructive actions are explicit; focus moves into dialogs and returns to the invoking control; Escape and Cancel behave predictably.
3. Keyboard-only users can reach every control, operate calendars/time fields and relationship filters, inspect the read-only Access tab, and perceive validation/errors. Automated axe scan has no serious/critical violations; screen-reader smoke covers catalogue, booking form, details, settings, and access information.
4. At 320px and 200% zoom there is no clipped primary action, inaccessible horizontal overflow, or overlapping content. Tables provide a usable narrow layout and preserve essential fields.
5. Loading, empty, conflict, permission-denied, archived, and network-failure states explain the next action. Destructive actions require clear confirmation and success feedback is visible and non-duplicative.
6. Visual review checks consistent date/time formatting, status colour plus text/icon (colour is never the only signal), readable contrast, and no console errors or unexpected duplicate requests.

## Stress, resilience, and security testing

| Test | Load / method | Acceptance threshold |
|---|---|---|
| S01 Catalogue read | 100 concurrent users, 10k items, filtered/sorted/paginated reads for 15 min | p95 ≤2s, error rate <1%, stable DB connections, no pagination duplication. |
| S02 Availability read | 100 concurrent users querying overlapping 30-day windows | p95 ≤2s; results remain consistent with bookings committed during the run. |
| S03 Booking contention | 50–100 workers repeatedly attempt the same slots | one winner per slot, zero double-bookings, conflict responses are 4xx and retry-safe. |
| S04 Mixed workflow | 200 virtual users: 60% browse, 20% availability, 15% book/cancel, 5% configure | p95 interactive API ≤3s, no deadlocks/timeouts causing committed-state ambiguity. |
| S05 Large histories | 10k bookings per target; My Bookings, audit, calendar feed, and archive/restore | bounded response/page size, p95 ≤3s, no OOM or request amplification. |
| S06 Bulk calendar | 1,000 subscriptions/downloads over 10 min | correct isolation and status; p95 ≤3s; rate limiting returns documented response without service collapse. |
| S07 Failure injection | kill/restart app node, transient DB/network failures, delayed duplicate requests | no partial booking/configuration writes; retry or clear recovery; audit remains coherent. |
| S08 Authorization fuzz | mutate IDs, roles, relationship filters, feed tokens, archived/deleted URIs | no cross-user/resource data; consistent 401/403/404 policy; no sensitive data in errors/logs. |
| S09 Notification fan-out | Create/cancel against instruments with 1, 10 and 100 eligible subscribers; race explicit opt-out/access changes against event creation | exactly the documented eligible recipients, no duplicates, bounded requests and no pre-commit external delivery; record transaction and delivery latency separately. |

Record throughput, p50/p95/p99 latency, error classes, DB CPU/locks/connections, JVM heap/GC, and browser long tasks. Repeat the contention run after any booking transaction or index change.

## Execution order and evidence

1. Run existing backend booking tests and focused frontend tests/typecheck as a baseline: `mvn test -Dtest='*Booking*' -Dfast=true`, `pnpm test src/modules/booking`, `pnpm tsc`.
2. Execute P0 API/UI flows in a clean environment, then permission and lifecycle flows with separate users.
3. Run accessibility/responsive/usability sessions and capture screenshots, recordings, defects, and task completion times.
4. Run S01–S09 with authorized seeded data; retain load scripts, dashboards, server logs, database metrics, and result summaries. Reduced-load smoke runs must not be reported as passing the full stress thresholds.
5. Re-run failed cases after fixes; attach request/response IDs and database assertions for every P0 failure.

Release is blocked by any double-booking, permission bypass, private-calendar leak, archive data inconsistency, destructive-delete violation, unrecoverable duplicate submission, serious accessibility defect, or missed performance threshold. Maintain a traceability sheet mapping each case to automated tests (for example `MyBookingsPage.spec.tsx`, booking page tests, and booking MVC/contract tests) and to manual evidence; gaps become follow-up tickets rather than being silently waived.

For every F/N/D/S case record PASS, FAIL, PARTIAL, BLOCKED or NOT RUN, account/fixture IDs, observed result and evidence location. Compound cases pass only when all required subcases were exercised. Record browser/assistive-technology coverage and unavailable operational metrics explicitly. Notification delivery to an unauthorized recipient or delivery for a rolled-back booking is also a release blocker.
