# Booking frontend

Booking separates display preferences from scheduling rules:

- The **display timezone** and **availability window** come from the current user's Booking
  preference, falling back to the global Booking defaults. They control displayed dates and times,
  route-level "today", booking-form wall clocks (until the user picks another zone; see the form
  timezone below), availability domains, and the Now marker.
- The **time format** chooses the 12- or 24-hour clock: Automatic (the default), 12-hour or
  24-hour. Automatic follows the browser region on booking pages. See
  [Notification times](BookingNotifications.md#notification-times) for notification formatting.
- The **scheduling timezone** remains on each `BookingConfiguration`. It controls opening-hour and
  slot-policy calculations and the timezone metadata in calendar feeds. Existing values are not
  rewritten.
- The **institution timezone** is the JVM default returned by the qualified
  `bookingInstitutionClock` bean. Booking does not define a deployment property and does not mutate
  the process timezone. Legacy RSpace code uses the same fallback when its browser/session zone
  is absent; a valid legacy session zone can still differ.

The opening-hours editor keeps each start/end pair on one row, including weekday
exceptions. On narrow screens, Open all day wraps onto a separate row.

The increment rules are defined in
[Typed scheduling increments](Booking.md#typed-scheduling-increments).

Booking forms keep the entered whole-minute start and snap the end
to the start plus the nearest whole number of increments (calendar days for Days),
announcing each adjustment. Both timelines use `creation/timelineRange.ts`: moves keep
the duration basis, resizes keep the other endpoint fixed, and every candidate is a
complete interval checked against increment, opening hours and maxima. An
endpoint on another day shows a striped continuation; double-click or Enter/Space moves
it to the displayed day's midnight when the result is valid, and a single click only
focuses it. Drag-to-create and the calendar's Add button produce proposals through the
same helper, so a Day proposal can extend past the displayed day.

Create and edit forms and the booking details page show a non-blocking note when an
event crosses a clock change in its scheduling timezone (`components/ClockChangeWarning.tsx`,
`domain/bookingClockChange.ts`). Detection finds the zone's offset transitions with
`start < transition <= end`, so it finds two changes even when both ends have equal offsets.
A change exactly at the end counts. Offsets are sampled every six hours and
bisected, because the Temporal polyfill's own transition search steps by two weeks and
skips a change that is reversed within that step, such as Gaza's Ramadan suspensions.
Bookings and maintenance share the 527,040-minute cap, so longer drafts are
rejected and get no note. A booking ending at its start's local time on a later date
reads as calendar days against elapsed
hours ("It spans 2 calendar days but lasts 47 hours"); maintenance and other bookings
show their local range and elapsed duration. A single shift is called a daylight-saving
change when the zone's own English names show
daylight or summer time on exactly one side; anything else, including a permanent
standard-time change or a zone with generic names, uses "clock change" wording. The
note never replaces a validation error and stores nothing. Events whose scheduling
timezone is withheld from the viewer show no note rather than a guessed one. A visually hidden live region announces a changed interval, or changed wording such as a new time
format, once.

Each weekday has its own hours. Open all day sets the selected weekday to `00:00`–`24:00`; a
partial day ending at midnight remains distinct from a full day. Show all day changes the visible
calendar window, not the item's availability.

Day timelines position events and drag selections by elapsed minutes from midnight in the display
timezone. Clock-change days therefore have 23 or 25 hours, with UTC offsets distinguishing repeated
local times. Availability-window preferences remain wall-clock values and are converted to the
timeline's coordinates. Converting a selection back to a booking draft preserves its DST occurrence.
Day bounds resolve the next local midnight separately. A preferred boundary inside a skipped hour
clamps to the first real instant after the gap. If both boundaries collapse to that instant, the
availability bar explains the empty window and neither quick filter matches.

Horizontal day-timeline scrolling stays outside React state. Related timelines share
`useDayTimelineScrollSync`, which aligns their elapsed-minute centers once per animation
frame and ignores scroll events from its own writes. `viewState` / `onViewStateChange`
remain for explicit positioning and zoom; scrolling does not call the state callback.
New synchronized timeline callers must use the shared scroll hook instead of updating
React state on each scroll. Hour labels and day bounds are cached by date and timezone.

Availability quick-filter counts load even when no filter is selected. They use today's preferred
display interval: before it starts, an item with a free segment is "Free later"; at or after
its end, it matches neither filter. Loading these counts does not block the unfiltered catalogue.
The server classifies items and counts them under the page's Search, type and item-property
filters, so counts describe the current item scope and no request pages through the catalogue. See
[Booking catalogue availability quick filters](Booking.md#booking-catalogue-availability-quick-filters). Availability bars load
the sourced booking intervals of the rows on the page only; under a quick filter they show today.
Both event and availability caches are scoped to the caller.

`AvailabilityBar` requires each occupied interval to carry its source and uses `timeZone` for its
display domain. `openingWindowsOnDate` supplies every opening window that intersects a display day.

Invalid preference fields are marked with `aria-invalid` and linked to their validation
message. A reversed availability window marks its end field; midnight remains a valid end.

Booking forms resolve wall-clock input and scheduling policy synchronously with
`validateBookingWindow`. The fields display validation results without reporting a resolved
window to the parent in an effect. Form state notifications use the latest callback without
turning callback identity changes into draft changes, so mutation errors remain visible until
the input changes.
The booking form's date and time fields use a **form timezone**: the display timezone unless the
user picks another. The globe beside the start time shows or hides a timezone field
under each endpoint's row. The end follows the start's zone until assigned its own. Picking the
start's zone for the end links them again. `resolveBookingWindow` and `validateBookingWindow` accept a
zone per endpoint for this. Changing a zone keeps the entered wall clock and moves the instants.
The quick-create dialog passes `fixedTimezone`, so it has no globe and stays in the display timezone.
`BookingForm` holds its draft in the form timezone and converts
drafts crossing its props (`initialWindow`, `windowAdjustment`, `onDraftChange`, `onStateChange`)
to and from the display timezone, so pages and the day timeline retain the display
timezone. When either endpoint's zone differs from the item's scheduling timezone, a note under
the end row gives the start and end in the scheduling timezone, with the date only when it differs
from the entered one, and the UTC offset on a clock-change day so the repeated hour is unambiguous.
Conflict times stay in the display timezone. `BookingFormState.enteredWindow` is set as soon as
both endpoints resolve with the end after the start, even when the times break the item's rules,
and draft availability checks use it to show conflicts immediately. Submission still needs
the rule-checked `window`.
Booking date and time displays retain the user's selected display timezone. When a booking's
instrument timezone differs from either that display timezone or the browser timezone, hovering
or focusing its timing shows the instrument-local date and time, UTC offset, and IANA timezone.
The booking read document supplies that timezone; a null or hidden timezone produces no tooltip.
The item information card's opening hours reuse the same `BookingInstrumentTimeTooltip`, passing
the hours already formatted in the item's timezone through its `time` prop.
Each independent weekday interval uses the selected clock format; omitted weekdays remain closed.
Opening-hour payloads keep `HH:mm` values and an end of `24:00`, regardless of the display format.

Numeric dates follow the browser's regional format, because native date and time inputs always
use it; words such as month names and durations follow the app language. The 12- or 24-hour clock
follows the browser region too unless the viewer chose an explicit time format. The booking form's
opening-hours summary uses that choice for uniform hours and windows converted to the viewer's timezone.
`bookingDateTimeLocale(format)` and `bookingHourCycle(format)` in `domain/bookingTime.ts` supply these, so pass
the resolved `preferences.timeFormat` explicitly to new `Intl.DateTimeFormat` calls and formatter helpers that show a
time. The helpers default to `AUTOMATIC` for standalone callers and are pure: display preferences are not stored in
module-level mutable state, and rendering one page cannot change another page's formatting. Browsers prevent pages from changing a native
`type="time"` input's clock, so these inputs keep the browser region's format; with an explicit
choice that differs from the region, text beside an input, such as the snapped-time note, can show
14:00 while the input shows 2:00 PM. The availability bar keeps a 24-hour clock for its compact axis. Time inputs
snap on blur as described above, and the form announces the adjusted value instead of changing
it silently.

The Time grid week view lays seven day columns on one wall-clock hour axis, using the viewer's selected
clock format. An overlap group shows at most two lanes (`WEEK_GRID_MAX_LANES`);
the rest collapse into a "+N more" slot that opens that
day in the Time grid day view. Duration-sized cards in the week grid and the booking form's day
schedule drop the booker badge, then the second title line, so the item name and time stay visible.

Global display defaults are stored on the audited `BookingConfigurationDefaults` singleton. The
initial values are `08:00`–`18:00`, Browser mode, no custom timezone, and the Automatic time
format, created with the singleton schema. A user override is one
versioned JSON document stored under `BOOKING_DISPLAY_PREFERENCES` in the existing
`UserPreference` system. Version 1 requires the complete stored shape, including
`timeFormat`. `BookingDisplayPreferencesManager` owns serialization, versioning,
validation, fallback, and preference access. Blank, corrupt, or unsupported stored documents fall
back to the current global values.

For current-subject preference routes, see
[Current concrete routes](RestApiV2Collections.md#current-concrete-routes).

PUT is a complete replacement, not a patch, so `timeFormat` is required. The
`timeFormat` field on this API and on `/api/v2/booking-settings` accepts exactly `AUTOMATIC`,
`H12` or `H24`: other strings, differently cased names and JSON numbers (which Jackson would
otherwise read as enum ordinals) receive `400 Bad Request` with `errors.api.v2.invalidRequest`.
DELETE removes the logical override. During run-as,
the subject owns the preference and the actor remains available for audit context.

The preference editor starts from the query cache and keeps unsaved input against the server
values loaded when editing began.
Background refreshes update a clean form, but preserve a dirty draft and its original baseline.
Successful Save or Reset adopts the returned server values and clears the draft, including Reset
when the server document is unchanged. Failed requests retain the draft. Reverting every field to
the editing baseline makes the form clean and adopts the latest cached server values.

Booking preferences and the sysadmin Settings page edit these values, including the Time format
radios (each labelled with an example time in that format), with the same
`BookingDisplaySettingsFields`. The custom timezone field appears only while Custom is
selected. Every other mode submits `customTimezone: null`, as the input schema requires, so the
component keeps the last custom zone in memory and restores it when the user switches back to Custom
before saving; Custom without a remembered zone starts at `UTC`. A hidden field has no validation
error, so an invalid custom zone blocks Save only while Custom is selected.

## Weekly scheduling defaults

The `openingHours` schema and weekday rules are defined in [Booking.md](Booking.md).

The settings editor keeps each day's custom hours independently. Closing and reopening a day
restores its draft during the same edit session. Open all day stores `00:00`–`24:00`; unchecking it
restores the day's last valid custom hours, or `09:00`–`17:00` if none exist. Native time inputs
display a `24:00` end as `00:00`. Accepted saves reset this editing history; failed saves retain it.
Callers that explicitly reset the form must also remount `SchedulingSettingsFields` to start a new
editing session. Availability-window, timezone, default sharing, granularity and buffer settings
remain separate from the weekly schedule.

Item creation copies the full institution schedule into its draft. Item edits submit the full
`openingHours` value, including unchanged weekdays. Cancelling restores all entries from the
configuration, while a failed or stale save keeps the current draft and its closed-day history.
Item details show every weekday independently, including closed days, in the scheduling timezone.

## Editing existing reserved times

Both the booking details editor and the calendar inline editor allow purpose edits when the
original reserved interval no longer meets the current duration, opening-hours or increment
rules. An informational status message explains this exemption. A current mismatch does not
establish when the rules changed. Maintenance uses the notes label and retains its existing
exemptions from opening-hours and configured maximum-duration rules.

Focusing and leaving a time field preserves unchanged off-grid endpoints. Changing either
endpoint validates the resulting interval against current rules. Invalid changes block saving
and show field errors. Restoring both original endpoints clears those blocking policy errors
and permits purpose edits again. A compliant reschedule can be saved normally.

Each editor sends only changed fields. A purpose-only PATCH omits `start` and `end`, so it
preserves the reserved interval. These rules do not change permissions, lifecycle checks or
optimistic concurrency. Failed saves retain the draft through the reconciliation described below.

Catalogue availability projections and cache signatures include the full `openingHours` schedule.
Changing any weekday recalculates the quick-filter category, including within the current minute.
Availability candidates and batched booking reads request `totals=true` for their pagination
and 1,000-item safety limits.

## Loading layouts

The Booking sidebar's Administration group lists Settings for sysadmins and Bookable Items for
users who can read at least one booking configuration or set up an eligible instrument
(`useCanOpenBookableItemsAdministration`: a one-row configurations count and the Add form's blank
target browse, both cached for 10 minutes); with neither, the group is omitted. The Bookable Items
page hides Add until an eligible target exists.

The dashboard at the Booking index uses
independent upcoming-list and monthly-overview queries for the authenticated requester.
The monthly calendar shows six
Monday-first weeks in the display timezone, including adjacent-month days. Booking
ends are exclusive, so a reservation ending at midnight is not counted on the next day.
Monthly reads follow `hasNextPage` without requesting totals, up to ten API pages
of 100 bookings. The caps count fetched pages and raw rows before deduplication;
exceeding either cap shows an explicit limit state instead of partial results. Offset
pagination remains best effort during concurrent changes.

Calendar badges show exact counts up to 99 and `99+` above that, while accessible
labels and popups retain the exact total. Day popups page the loaded bookings five
at a time, using previous/next controls without additional requests. Their hover
delays are zero, and the paginated list reserves space for five collapsed rows so
the controls remain stable on short final pages.

Date picker buttons show the selected date. Successful creation returns to the submitted
start date rather than the URL's original date. Item headers label the display timezone;
the Booking rules section retains the separate scheduling timezone.

All Bookable Items renders table and availability-detail locations from the catalogue item's
`location`, carried by the configuration adapter on `target.location`. It makes no per-item
instrument request. Instrument-backed configuration pages instead expand `parentContainer`
with `depth=2` and sparse fieldsets for `instruments` and `containers`.

All Bookable Items keeps `pageSize` in the URL for both normal and availability-filtered
results. Changing it resets to page one. My Bookings uses TableList cards on narrow
viewports and a table on wide viewports. Search inputs show their collection scope.
Pages own their main landmark; SidebarInset is only a layout container.

The catalogue's master filter is the complete RSQL `where` URL parameter. Quick buttons replace
only availability predicates; changing dates removes those predicates without discarding item or
ID rules. Existing `target` and `availability` links remain readable. A top-level availability
predicate is sent as the catalogue's `availability` parameter with today's window, and the server
applies it before paging (see [Booking catalogue availability quick filters](Booking.md#booking-catalogue-availability-quick-filters)); an availability predicate inside an OR group is reported as an
unavailable restored filter. `/api/v2/booking-catalogue?where=...` validates filters with the
shared collection parser and intersects them with visibility and active-item constraints before
database pagination. Do not fetch all catalogue pages to filter them in the browser.

Calendar has one Search input and separate Bookable items and Booking events filter groups.
The groups combine with AND; each group retains its own nested AND/OR expression.
Item identity uses `target` (for example `IN703`), never configuration or booking IDs.
Search matches item name, exact inventory ID, readable inventory description, or visible
booking purpose. Event filters require one visible event in the selected interval to match
the entire event expression. Without event filters, item text can retain an empty resource row.
This scope applies to Resources, Grid and Agenda. Availability and conflict checks retain
their independent complete scope. Resource-row slot proposals use a separate event query
without Search or event predicates when those filters are active; otherwise the query cache
is shared with displayed events. An availability failure disables row creation and offers retry
while filtered display results remain available.

Calendar Reset clears the date URL parameter, restoring today in the display timezone,
clears both filter groups and Search, and restores Day, Resources, and My calendar off.
View, layout, and the Mine/My items quick filters use nuqs-backed URL parameters.
Item filters and Search use
`calendar-resources.where` and `calendar-resources.q`;
event filters use `calendar-events.where`.
The page pauses event fetching until date navigation and display-state reset settle,
avoiding requests for an intermediate date/period.

Focus on calendar uses the event's saved start date in the display timezone and
its target item. It preserves the selected view and layout, clears calendar
where/q filters and Mine/My items, then focuses the matching event occurrence
identified by date and event ID. A week-grid event hidden in +N more falls back
to the Time grid day view; Resources falls back there when the target has no
enabled resource row. Multi-day events focus their start-date occurrence. The
focus request waits for an active event refresh before using cached results.
Focus is scheduled after the DOM commits and does not depend on animation
frames, which embedded or background browsers can suspend.
Its 15-second timeout runs only while the display and event reads are ready;
loading, refreshes, and read errors pause it. Focus or an unavailable result
removes the request. Further keyboard/pointer input cancels it, while browser
history navigation preserves the destination entry and cleans up pending work.
A missing or inaccessible event is reported in a mounted status region. Retry
clears that result, refreshes the events, and requests focus again; date, layout,
and filter changes clear the old result. Failed reads retain the calendar's
existing Retry control. Resources and week-overflow fallbacks announce the
view change, and consecutive focus requests remove the previous highlight.
On booking details, refreshed item permissions control the notice's calendar
action. Losing access or the target removes the action and retains the saved
success message.

Booking routes own their Suspense fallbacks so their preference and token reads
stay inside Booking once the app shell has mounted. Calendar event
reads retain the selected grid and known resource rows; detail fallbacks reserve
the content and facts columns. Keep placeholders aligned when changing a page layout.
Availability-filter counts reserve space before their values arrive
so they do not wrap the narrow-screen toolbar on completion.

Before mounting the Booking shell or sidebar, AppShell waits for feature flags. Disabled or
unavailable Booking flags redirect to `/workspace` without issuing Booking queries.
When Booking is enabled, the Inventory sidebar links to `/booking`; the legacy global AppBar
does not include a separate Booking link.

The resource-week skeleton shares its date headers and column sizing with the
loaded grid. It reserves four anonymous rows before the catalogue arrives, then
uses the authorized resource names while their events are still loading.

The Calendar, All Bookable Items, and Bookable Item browser specs check loading
geometry at 390px and 1440px. These checks bound shifts for controlled fixtures,
not arbitrary content. Dense calendars and long lists can still grow. Loading
markup must not contain previous-resource data or enable controls before the
relevant capability is known.

## Vertical schedule beside booking forms

The add and edit forms compose the same `VerticalDayTimeline` through
`BookingDayTimelineAside`. The timeline displays the day and owns temporary drag state.
`BookingForm` owns the committed booking window. `useBookingTimelineDraft` passes completed
adjustments back to the form
and prevents submission until the matching adjustment is acknowledged. Keep
that bridge shared when adding another form consumer.

Use `BookingDayTimelineAside`, `VerticalDayTimeline` and `VerticalDayTimelineCanvas` for responsive
layout and event presentation. Fetch raw confirmed events with `fetchDayBookings`; draft availability
checks cover the complete interval independently of the browsed date or sheet visibility, and opening
the sheet reuses the existing query. Retain projection, authorization and pagination safeguards;
incomplete reads are errors. Keep short or crowded events accessible through form fields and event
details. Pointer and keyboard adjustments share scheduling-zone slot calculations; positions use
elapsed time in the display zone. Cancelled gestures or lost pointer capture discard previews without
changing the form. Do not use padded availability intervals as visual event durations.
Reads follow `hasNextPage` and `nextPage` without totals, capped at ten pages or 1,000 raw bookings per target batch.

## Booking window reconciliation

The frontend checks
the clock again on submission and maps the server's window error to both rules.
After an uncertain create or edit response, the frontend invalidates booking queries
because the write may have committed. Edit forms retain their drafts during
reconciliation. The calendar editor keeps its base version until reconciliation
finishes, then uses the refreshed version for the next save without replacing the draft.
A precondition or concurrent-modification rejection keeps the calendar
draft and saves it against the refreshed version, unless the user discards it.

Authenticated Booking clients use `apiV2Headers` and `apiV2JsonHeaders` from
`modules/common/api/apiV2Headers.ts`. They preserve supplied headers and set Bearer
authorization and `X-Requested-With`; the JSON variant also sets `Content-Type`.
The shared transport helpers do not parse Booking-specific problem extensions.

Calendar, catalogue, and add-booking routes ignore malformed date parameters (including
non-string values) and use their normal display-timezone defaults.

## Settings access and draft stability

`BookingSettingsRoutePage` checks sysadmin access before requesting settings. A 403 produces
the not-permitted state. Settings and item queries use `retryUnlessClientError` from
`modules/common/queries/queryRetry.ts`: permanent 4xx responses do not retry, except 408/429;
network failures and 5xx responses retain three retries. Clean Settings forms adopt a
refreshed baseline. Dirty forms and failed saves retain input, including weekday editing history.
A successful save or explicit reload adopts server values and starts a new editing session.
Item configuration and notification editors likewise retain drafts through background refreshes.

The Booking API scheduling-timezone write contract is in
[Booking](Booking.md#scheduling-timezone-writes).

Bookable-item event tables cache totals separately from page requests under the bookings query prefix. Page navigation uses `hasNextPage`, and pagination shows "of many" until the total is available. The upcoming-bookings badge uses `/api/v2/bookings/count?max=100` and displays `99+` when the server marks the total as a lower bound.

## Notices across routes and on detail pages

Booking pages use `BookingNoticesProvider` for results that must cross a route change. The
destination registers with `useBookingNoticeHost("calendar", deliver, ready)`. Its callback pushes
through the table's `alertsRef` and returns `true` only after delivery. A callback ref updates
`ready` when the table's alert API attaches, so registration retries a queued result without polling.
Producers call `useBookingNotices().notify("calendar", alert)`. The provider holds one pending result
until delivery succeeds and discards it when navigation moves to a different host.
Use `useBookingLocalNotices` with `BookingLocalNotices` for a detail page without a table.
Each push has a fresh announcement identity; its semantic `id` still controls replacement and dismissal.
When dismissal removes the focused control, local notices focus a neighbouring notice or the
detail page's main landmark. Programmatic removal preserves focus elsewhere.

## Query factories and invalidation

Booking reads export `queryOptions` factories beside their fetchers. For example,
`dashboardBookings.ts` exports `upcomingDashboardBookingsQuery`, and
`bookableItemEvents.ts` exports `bookableItemEventsQuery`. Components spread the
factory options and add presentation or availability guards. Use the factory's
tagged `queryKey` for typed cache reads and writes. `domain/bookingKeys.ts` contains
only resource roots and prefixes for invalidation, and imports only the generic
API v2 roots helper. This lets factories land in the stack layer that owns the
fetcher without importing later pages into earlier consumers. Display preferences
retain their existing `bookingDisplayPreferencesQueryOptions` entry point.

API v2 keys start with one resource string, such as `rspace.api.v2.bookings` or
`rspace.api.v2.booking-configurations`. Table List rows share that resource root.
Booking catalogue projections that depend on bookings remain under
`["rspace.api.v2.bookings", "booking-catalogue"]`, while picker reads use
`["rspace.api.v2.booking-catalogue", "picker"]`. Configuration changes refresh both
families; booking changes refresh the booking-dependent projections.

The app-shell client defaults to a 30-second stale time and
`retryUnlessClientError`. Factory-specific stale times, retry policies and refetch
settings take precedence. Legacy QueryClient islands retain their own defaults.
Relevant factories retain explicit retry policies for standalone consumers too.

Successful mutations declare `meta.invalidates` as an array of factory keys.
The app-shell MutationCache awaits those invalidations before component success
callbacks, so navigation and subsequent cache reads see refreshed data.
`useApiV2Mutation` supplies the same behavior for standalone component clients.
Error recovery, conditional bulk refreshes and imperative actions retain explicit
invalidation. Calendar-link writes keep their cancellation and cache-update order.

Caller-sensitive reads use the existing current-user cache without fetching another
profile. Their identity scope is `[effectiveUserId, operatedAs ? effectiveUserId : null]`.
The API exposes the effective subject and the operated-as flag, but no originating
actor ID. Run-as redirects create a new app client; token refresh leaves the identity
scope unchanged. Generic adapters also preserve a supplied stable user or tenant
scope. Bearer tokens are fetch credentials, never query-key elements.


## Route prefetching

The app creates its router with the same QueryClient passed to QueryClientProvider.
Booking loaders first refresh stale credentials with the shared token factory, then
start `ensureQueryData` with the factories used by the components.
The shared Booking layout starts display preferences. Item details preload the
configuration for the route target; Add item starts defaults and any supplied target
search; Settings checks the current user's sysadmin role before starting admin settings.
Booking details validate the numeric route ID before fetching, and Add booking
starts the enabled target lookup when the search includes a target.

Loaders launch these promises without awaiting navigation and catch rejections.
Existing Suspense fallbacks and component error boundaries keep responsibility for
loading and failed requests. Query metadata, including navigation progress metadata,
stays in the factories. Query owns freshness; the router's preload stale time is zero.
Calendar and catalogue tables retain their component-driven reads because their
keys depend on restored table state and selected resources. Dashboard and My bookings
reads also depend on component clocks or state, so their queries remain in components.
