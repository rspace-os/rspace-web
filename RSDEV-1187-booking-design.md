# Booking and scheduling decisions

This document records future booking decisions for RSDEV-1187. It is not the REST API v2
implementation guide.

Use `DevDocs/DeveloperNotes/RestApiV2Collections.md` for current REST API v2 behavior. Use
`/api/v2/openapi.json` for the public HTTP contract.

## Current state

The branch implements the generic REST API v2 engine. It also implements the
`booking-configurations` collection.

`BookingConfiguration` stores these values:

- An enabled flag.
- A lowercase `timezone` field in API responses.
- A typed target reference.
- Audit dates and audit user relationships.

The current target type is `INSTRUMENT`. The database enforces one configuration for each complete
target identity. The resource uses the existing RSpace audit infrastructure.

The branch does not implement bookings, recurrence, availability rules, or calendar feeds.

## Future domain decisions

### BookingConfiguration

Keep one configuration for each bookable target. Use the configuration row as the lock row for
booking changes.

Add a configuration version when availability rules can change. Increment the version when a
change can affect booking validity.

### TimeSlotBooking

Store each booking occurrence as one row. Store start and end times as UTC instants.

Use `CONFIRMED` and `CANCELLED` states. Use soft deletion. Derive completion from the end time.

Require full inventory read permission to create a booking. Permit the requester, the item owner,
or a system administrator to change a booking.

### BookingSeries

Store a recurrence definition on a series. Materialize each occurrence as a `TimeSlotBooking` row.
Extend the materialized horizon with a scheduled job.

Store a skipped occurrence when the service cannot create an occurrence. Store the reason and the
conflicting booking ID when one exists.

### AvailabilityRule

Use explicit `AVAILABLE` and `CLOSED` rules. Expand these rules when the service reads availability.
Do not materialize availability windows.

Use an IANA timezone for each rule. Limit the query range, occurrence count, duration, and horizon.
Return 400 for malformed recurrence input. Return 422 when valid input exceeds a limit.

## Concurrency

Use this sequence when the service creates a booking:

1. Read the configuration and its version.
2. Expand and validate recurrence outside the database lock.
3. Lock the configuration row.
4. Read the configuration again.
5. Retry when an input to expansion changed.
6. Check enabled state, availability, and overlap.
7. Insert all rows in one transaction.

Use the same lock sequence for the horizon job. Lock configuration IDs in a stable order for a
bulk operation.

## Privacy

Apply read authorization before count, pagination, and projection. A page can contain full rows and
restricted rows.

Show restricted bookings as busy time. Do not show the purpose or requester. Apply the same rule to
calendar feeds.

Do not let a caller filter or sort by a hidden field.

## Audit

Use Envers for earlier row values. Use audit-trail events for actions and actors. Neither system
replaces the other.

Audit booking configurations, bookings, series, and availability rules. Do not audit calendar feed
tokens. Do not audit a series horizon watermark.

Publish audit events after commit. Do not publish an event for rolled-back work. Do not put a raw
calendar token or token hash in an audit event.

Use a system actor for scheduled horizon work. Do not attribute scheduled work to the requester.

## Calendar feeds

Use a random bearer token in each calendar feed URL. Store only a hash of the token. Permit the
owner to revoke or rotate the token.

Serve calendar feeds outside the API-key and OAuth route group. Resolve the token to its owner and
scope. Apply the owner's current permission when the service builds the feed.

Return the raw token only when the service creates it. Never log the token.

## REST API v2 use

Add future booking resources to the existing REST API v2 catalog. Do not create a second generic
engine.

Use `/api/v2` routes. Use `where` for filters. Use the Payload-style envelopes from the current
implementation guide.

Use `ApiV2Problem` for errors. Put user-facing text in message bundles. Use OpenAPI 3.1 and JSON
Schema 2020-12 rules.

Add a concrete controller only when an operation is not collection CRUD. Examples include calendar
token issue and calendar feed delivery.

## Dependencies

Use the Blaze Persistence and RSQL dependencies that the REST API v2 engine already uses. Do not
add QueryDSL unless a measured need appears.

Upgrade and test ical4j before recurrence work starts. Review all transitive dependency exclusions.

Do not install sibling RSpace artifacts into the local Maven repository. Push the sibling change,
wait for its remote build, and then update the pinned commit.

## Delivery order

1. Add booking persistence and authorized reads.
2. Add availability and recurrence limits.
3. Add locked create and cancel operations.
4. Add series horizon processing and skipped occurrences.
5. Add calendar feed tokens and feed output.
6. Test privacy, locking, audit behavior, query performance, and generated clients.

## Deferred work

The following work is not part of the first booking release:

- Quantity requests.
- Approval workflows.
- Partial acceptance of a conflicting series.
- External or guest booking.
- Usage billing.
- Performer scheduling.
- “This and future” series edits.
