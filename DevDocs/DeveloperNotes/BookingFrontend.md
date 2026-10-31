# Booking frontend

Booking keeps presentation choices separate from scheduling rules:

- The **display timezone** and **availability window** come from the current user's Booking
  preference, falling back to the global Booking defaults. They control rendered dates and times,
  route-level "today", booking-form wall clocks (until the user picks another zone; see the form
  timezone below), availability domains, and the Now marker.
- The **scheduling timezone** remains on each `BookingConfiguration`. It controls opening-hour and
  slot-policy calculations and the timezone metadata in calendar feeds. Existing values are not
  rewritten.
- The **institution timezone** is the JVM default returned by the qualified
  `bookingInstitutionClock` bean. Booking does not define a deployment property and does not mutate
  the process timezone. This agrees with legacy RSpace code paths when their browser/session zone
  is absent; a valid legacy session zone can still differ.

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
Keep new synchronized timeline callers on the shared scroll hook, not a per-scroll
React state update. Hour labels and day bounds are cached by date and timezone.

Availability quick-filter counts load even when no filter is selected. They use today's preferred
display interval: before it starts, an item with a free segment is "Free later today"; at or after
its end, it matches neither filter. Loading these counts does not block the unfiltered catalogue.
Candidate discovery uses the catalogue's Search, type and item-property predicates before applying
the 1,000-item availability limit. Local availability comparisons become logical true when deriving
that candidate predicate, preserving AND/OR grouping. Counts therefore describe the current item
scope. Today's availability bars reuse the same booking intervals; other dates load their own
intervals. Both event and availability caches are scoped to the caller.

Booking forms resolve wall-clock input and scheduling policy synchronously with
`validateBookingWindow`. The fields display validation results; they do not report a resolved
window to the parent in an effect. Form state notifications use the latest callback without
turning callback identity changes into draft changes, so mutation errors remain visible until
the input changes.
The booking form's date and time fields use a **form timezone**: the display timezone unless the
user picks another. The globe beside the start time shows or hides a timezone field
under each endpoint's row. The end follows the start's zone until it is given its own; picking the
start's zone for it links them again. `resolveBookingWindow` and `validateBookingWindow` accept a
zone per endpoint for this. Changing a zone keeps the entered wall clock and moves the instants.
The quick-create dialog passes `fixedTimezone`, so it has no globe and stays in the display timezone.
`BookingForm` holds its draft in the form timezone and converts
drafts crossing its props (`initialWindow`, `windowAdjustment`, `onDraftChange`, `onStateChange`)
to and from the display timezone, so pages and the day timeline keep working in the display
timezone. When either endpoint's zone differs from the item's scheduling timezone, a note under
the end row gives the start and end in the scheduling timezone, with the date only when it differs
from the entered one, and the UTC offset on a clock-change day so the repeated hour is unambiguous.
Conflict times stay in the display timezone. `BookingFormState.enteredWindow` is set as soon as
both endpoints resolve with the end after the start, even when the times break the item's rules,
and the draft availability checks use it, so conflicts show straight away; submission still needs
the rule-checked `window`.
Booking date and time displays retain the user's selected display timezone. When a booking's
instrument timezone differs from either that display timezone or the browser timezone, hovering
or focusing its timing shows the instrument-local date and time, UTC offset, and IANA timezone.
The booking read document supplies that timezone; a null or hidden timezone produces no tooltip.
The Time grid week view lays seven day columns on one wall-clock hour axis. An overlap group shows
at most two lanes (`WEEK_GRID_MAX_LANES`); the rest collapse into a "+N more" slot that opens that
day in the Time grid day view. Duration-sized cards in the week grid and the booking form's day
schedule drop the booker badge, then the second title line, so the item name and time stay visible.

Global display defaults are stored on the audited `BookingConfigurationDefaults` singleton. The
initial values are `08:00`–`18:00`, Browser mode, and no custom timezone. A user override is one
versioned JSON document stored under `BOOKING_DISPLAY_PREFERENCES` in the existing
`UserPreference` system. `BookingDisplayPreferencesManager` owns serialization, versioning,
validation, fallback, and preference access. Blank, corrupt, or unsupported stored documents fall
back to the current global values.

For current-subject preference routes, see
[Current concrete routes](RestApiV2Collections.md#current-concrete-routes).

PUT is a complete replacement, not a patch. DELETE removes the logical override. During run-as,
the subject owns the preference and the actor remains available for audit context.

The preference editor keeps unsaved input against the server values loaded when editing began.
Background refreshes update a clean form, but preserve a dirty draft and its original baseline.
Successful Save or Reset adopts the returned server values and clears the draft, including Reset
when the server document is unchanged. Failed requests retain the draft. Reverting every field to
the editing baseline makes the form clean and adopts the latest cached server values.

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

The Booking index shows the dashboard. Its upcoming list and monthly overview use
independent queries for the authenticated requester. The monthly calendar shows six
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

Date picker buttons show the selected
date, and successful creation returns to the submitted start date rather than
the date originally supplied in the URL. Item headers label the display timezone;
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
ID rules. Existing `target` and `availability` links remain readable. Availability predicates resolve
to target-ID predicates before the request. `/api/v2/booking-catalogue?where=...` validates filters
with the shared collection parser and intersects them with visibility and active-item constraints
before database pagination. Do not fetch all catalogue pages to filter them in the browser.

Calendar has one Search input and separate Bookable items and Booking events filter groups.
The groups combine with AND; each group retains its own nested AND/OR expression.
Item identity uses `target` (for example `IN703`), never configuration or booking IDs.
Search matches item name, exact inventory ID, readable inventory description, or visible
booking purpose. Event filters require one visible event in the selected interval to match
the entire event expression. Without event filters, item text can retain an empty resource row.
The same scope applies to Resources, Grid and Agenda; availability and conflict checks keep
their independent complete scope. Resource-row slot proposals use a separate event query
without Search or event predicates when those filters are active; otherwise the query cache
is shared with displayed events. An availability failure disables row creation and offers retry
while filtered display results remain available.

Calendar Reset clears the date URL parameter, restoring today in the display timezone,
clears both filter groups and Search, and restores Day, Resources, and My calendar off.
Layout and period remain local React state. Item filters and Search use
`calendar-resources.where` and `calendar-resources.q`;
event filters use `calendar-events.where`.
The page coordinates date navigation and display-state reset, pausing event
fetching until they settle to avoid requests for an intermediate date/period.

Booking routes own their Suspense fallbacks so their preference and token reads
stay inside Booking once the app shell has mounted. Calendar event
reads retain the selected grid and known resource rows; detail fallbacks reserve
the content and facts columns. Keep these placeholders aligned when changing a
page layout. Availability-filter counts reserve space before their values arrive
so they do not wrap the narrow-screen toolbar on completion.

Before mounting the Booking shell or sidebar, AppShell waits for feature flags. Disabled or
unavailable Booking flags redirect to `/workspace` without issuing Booking queries.
When Booking is enabled, the Inventory sidebar links to `/booking`; the legacy global AppBar
does not include a separate Booking link.
Calendar, catalogue, and add-booking routes ignore malformed date parameters (including
non-string values) and use their normal display-timezone defaults.

The resource-week skeleton shares its date headers and column sizing with the
loaded grid. It reserves four anonymous rows before the catalogue arrives, then
uses the authorized resource names while their events are still loading.

The Calendar, All Bookable Items, and Bookable Item browser specs check loading
geometry at 390px and 1440px. These checks bound shifts for controlled fixtures,
not arbitrary content: dense calendars and long lists can still grow. Loading
markup must not contain previous-resource data or enable controls before the
relevant capability is known.

## Vertical schedule beside booking forms

The add and edit forms compose the same `VerticalDayTimeline` through
`BookingDayTimelineAside`. The timeline owns visual day presentation and
temporary drag state; `BookingForm` remains the owner of the committed booking
window. `useBookingTimelineDraft` bridges completed adjustments back to the form
and prevents submission until the matching adjustment is acknowledged. Keep
that bridge shared when adding another form consumer.

The browsed-day query uses raw confirmed bookings from `fetchDayBookings`.
It follows `hasNextPage` and `nextPage` without requesting totals, and rejects a target
batch exceeding ten pages or 1,000 bookings rather than displaying a partial schedule.
Availability checks stay in the owning page and cover the entire committed
draft, independently of the browsed date or whether the narrow-layout schedule
sheet is open. On narrow layouts, a floating Day schedule button appears at
the right edge while the form is in view and opens a right-side sheet. Its tab
stays attached to the sheet's left edge as it slides in and out, and closes the
sheet when pressed. The wide layout keeps
the timeline below the item information card. Opening the
sheet does not start a second day-schedule query. Do not use padded
availability intervals as visual event durations.
Both fetch paths retain the projection, authorization scope and pagination cap;
an incomplete schedule is an error, never evidence that an instrument is free.

Existing events use deterministic overlap lanes. The draft spans the event
area, with an opaque theme-derived tint that stays unchanged during gestures.
The presentation follows the Codex Vertical Day Timeline: a compact card with
a centered date between previous/next buttons, timezone and instrument context,
continuous hour lines and thin
draft resize edges. The full zoned day remains scrollable at 56 pixels per hour;
it is not restricted to the prototype's sample hours. Short intervals remain
editable through the booking form's date and time fields. The item information
card appears above the timeline on wide add and edit layouts. Event item names
use a single-line ellipsis; hover text and event details retain the full name.
An Event details disclosure below the grid keeps very short or crowded events
inspectable without a second calendar view. Pointer and keyboard
adjustments share scheduling-zone slot calculations while positions
use elapsed time in the display zone. Cancelling a gesture or losing pointer
capture discards its preview without changing the form.

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

The Booking API scheduling-timezone write contract is in
[Booking](Booking.md#scheduling-timezone-writes).

Bookable-item event tables cache totals separately from page requests under the bookings query prefix. Page navigation uses `hasNextPage`, and pagination shows "of many" until the total is available. The upcoming-bookings badge uses `/api/v2/bookings/count?max=100` and displays `99+` when the server marks the total as a lower bound.
