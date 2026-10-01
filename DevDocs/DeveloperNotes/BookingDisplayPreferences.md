# Booking display preferences

Booking keeps presentation choices separate from scheduling rules:

- The **display timezone** and **availability window** come from the current user's Booking
  preference, falling back to the global Booking defaults. They control rendered dates and times,
  route-level “today”, booking-form wall clocks (until the user picks another zone; see the form
  timezone below), availability domains, and the Now marker.
- Booking owner notifications use the recipient's display timezone in the dashboard. Browser mode
  uses the timezone captured for the current session and falls back to the institution zone if it is
  missing; email notifications use the institution zone for Browser mode.
- The **scheduling timezone** remains on each `BookingConfiguration`. It controls opening-hour and
  slot-policy calculations and the timezone metadata in calendar feeds. Existing values are not
  rewritten.
- The **institution timezone** is the JVM default returned by the qualified
  `bookingInstitutionClock` bean. Booking does not define a deployment property and does not mutate
  the process timezone. This agrees with legacy RSpace code paths when their browser/session zone
  is absent; a valid legacy session zone can still differ.

Opening hours are scheduling settings on each `BookingConfiguration`, copied from
`BookingConfigurationDefaults` when an item is configured. `openingStart` / `openingEnd` is the
shared daily interval, `openDays` lists the open ISO weekdays (1 = Monday), and
`openingExceptions` gives an open weekday its own `start` / `end` instead of the shared interval.
`BookingSchedulingSettings.effectiveHours(dayOfWeek)` is the single rule for a day's effective
hours: closed if the day is not in `openDays`, its exception if it has one, otherwise the shared
interval. The weekday comes from the scheduling timezone, never the display timezone.
`BookingSchedulingPolicyImpl` requires every instant of a regular booking's `[start, end)` to fall in
its day's effective hours, walking calendar dates so a closed middle day rejects a multi-day booking
and an end at midnight consumes no time on the next day; failures use the `errors.api.v2.booking.openingHours` problem
code. Maintenance keeps its exemption. Changing any of these fields advances the configuration
version and needs the same `EDIT_CONFIGURATION` capability as other scheduling settings; booking
creation and time edits validate against the locked current configuration, so a concurrent
settings change is serialized with them. Existing bookings are not moved or cancelled when the
schedule changes.

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
display interval: before it starts, an item with a free segment is “Free later”; at or after
its end, it matches neither filter. Loading these counts does not block the unfiltered catalogue.
The server classifies items and counts them under the page's Search, type and item-property
filters, so counts describe the current item scope and no request pages through the catalogue. See
"Booking catalogue availability quick filters" in `RestApiV2Collections.md`. Availability bars load
the booking intervals of the rows on the page only; under a quick filter they show today. Both
event and availability caches are scoped to the caller.

The preferences editor derives its initial input from the query cache. Once a user edits a field,
it keeps that local draft across background refetches. Successful Save or Reset clears the draft
and displays the saved query document.
Invalid preference fields are marked with `aria-invalid` and linked to their validation
message. A reversed availability window marks its end field; midnight remains a valid end.

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
at most two lanes (`WEEK_GRID_MAX_LANES`); the rest collapse into a “+N more” slot that opens that
day in the Time grid day view. Duration-sized cards in the week grid and the booking form's day
schedule drop the booker badge, then the second title line, so the item name and time stay visible.

Global display defaults are stored on the audited `BookingConfigurationDefaults` singleton. The
initial values are `08:00`–`18:00`, Browser mode, and no custom timezone. A user override is one
versioned JSON document stored under `BOOKING_DISPLAY_PREFERENCES` in the existing
`UserPreference` system. `BookingDisplayPreferencesManager` owns serialization, versioning,
validation, fallback, and preference access. Blank, corrupt, or unsupported stored documents fall
back to the current global values.

The current-subject REST API is:

```text
GET    /api/v2/users/me/booking-preferences
PUT    /api/v2/users/me/booking-preferences
DELETE /api/v2/users/me/booking-preferences
```

PUT is a complete replacement, not a patch. DELETE removes the logical override. During run-as,
the subject owns the preference and the actor remains available for audit context.

## Loading layouts

The Booking index shows the dashboard. Its upcoming list and monthly overview use
independent queries for the authenticated requester. The monthly calendar shows six
Monday-first weeks in the display timezone, including adjacent-month days. Booking
ends are exclusive, so a reservation ending at midnight is not counted on the next day.
Monthly reads follow at most ten API pages of 100 bookings; an interval exceeding
1,000 bookings shows an explicit limit state instead of partial results. Offset
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

All Bookable Items keeps `pageSize` in the URL for both normal and availability-filtered
results. Changing it resets to page one. My Bookings uses TableList cards on narrow
viewports and a table on wide viewports. Search inputs show their collection scope.
Pages own their main landmark; SidebarInset is only a layout container.

The catalogue's master filter is the complete RSQL `where` URL parameter. Quick buttons replace
only availability predicates; changing dates removes those predicates without discarding item or
ID rules. Existing `target` and `availability` links remain readable. A top-level availability
predicate is sent as the catalogue's `availability` parameter with today's window, and the server
applies it before paging (see "Booking catalogue availability quick filters" in
`RestApiV2Collections.md`); an availability predicate inside an OR group is reported as an
unavailable restored filter. `/api/v2/booking-catalogue?where=...` validates filters with the
shared collection parser and intersects them with visibility and active-item constraints before
database pagination. Do not fetch all catalogue pages to filter them in the browser.

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
card appears above the timeline on wide add and edit layouts.
An Event details disclosure below the grid keeps very short or crowded events
inspectable without a second calendar view. Pointer and keyboard
adjustments share scheduling-zone slot calculations while positions
use elapsed time in the display zone. Cancelling a gesture or losing pointer
capture discards its preview without changing the form.

## REST API compatibility

The `timezone` field on `/api/v2/booking-configurations` is the item's scheduling timezone. Single
and bulk creates may set it to an IANA zone ID; blank or unknown zones receive `400 Bad Request`
from the entity validation. Creates that omit it are assigned the JVM-backed institution timezone.
It is immutable afterwards: patch and bulk-update requests that include it receive the collection
framework's normal `400 Bad Request` response. The Add bookable item form offers it as a searchable
select that defaults to the institution timezone; the edit form omits it.

## Item filters on the dashboard

The dashboard's Bookable items controls scope both Upcoming bookings and the monthly
summary. `dashboard.where` stores the expression in the URL. Predicates reach the server
before the five-row upcoming limit and the 1,000-event monthly limit. Both widgets wait
while saved custom-field definitions load. Missing or invalid definitions retain the saved
expression and require an explicit reset; a failed definition request offers retry.
