# Booking display preferences

Booking keeps presentation choices separate from scheduling rules:

- The **display timezone** and **availability window** come from the current user's Booking
  preference, falling back to the global Booking defaults. They control rendered dates and times,
  route-level “today”, booking-form wall clocks (until the user picks another zone; see the form
  timezone below), availability domains, and the Now marker.
- Booking owner notifications use the recipient's display timezone in the dashboard. Browser mode
  uses the timezone captured for the current session and falls back to the institution zone if it is
  missing; email notifications use the institution zone for Browser mode.
- The **time format** chooses the 12- or 24-hour clock: Automatic (the default), 12-hour or
  24-hour. Automatic keeps the existing behaviour: booking pages follow the browser region, in-app
  notifications follow the region named by the request's `Accept-Language`, and emails, which have
  no browser, use the usual clock of the message language. An explicit 12- or 24-hour choice
  replaces all three. `BookingNotificationMessageFormatter` receives it as a `BookingTimeFormat`
  (from `DashboardController` and from the `BookingNotificationRecipient` snapshot for email) and
  turns it into the ICU `hc` keyword (`h12`, or `h23` so midnight is 00:00).
- The **scheduling timezone** remains on each `BookingConfiguration`. It controls opening-hour and
  slot-policy calculations and the timezone metadata in calendar feeds. Existing values are not
  rewritten.
- The **institution timezone** is the JVM default returned by the qualified
  `bookingInstitutionClock` bean. Booking does not define a deployment property and does not mutate
  the process timezone. This agrees with legacy RSpace code paths when their browser/session zone
  is absent; a valid legacy session zone can still differ.

Opening hours are scheduling settings on each `BookingConfiguration`, copied from
`BookingConfigurationDefaults` when an item is configured. `openingHours` contains independent
`dayOfWeek`, `start` and `end` entries. Omitted ISO weekdays are closed. A supplied schedule replaces
the complete week; omission preserves it on update and copies the complete defaults on create.
`BookingSchedulingSettings.effectiveHours(dayOfWeek)` resolves that day's interval. The weekday
comes from the scheduling timezone, never the display timezone.
`BookingSchedulingPolicyImpl` requires every instant of a regular booking's `[start, end)` to fall in
its day's effective hours, walking calendar dates so a closed middle day rejects a multi-day booking
and an end at midnight consumes no time on the next day; failures use the `errors.api.v2.booking.openingHours` problem
code. Maintenance keeps its exemption. Changing the schedule advances the configuration
version and needs the same `EDIT_CONFIGURATION` capability as other scheduling settings; booking
creation and time edits validate against the locked current configuration, so a concurrent
settings change is serialized with them. Existing bookings are not moved or cancelled when the
schedule changes.

The opening-hours editor keeps each start/end pair on one row, including weekday
exceptions. On narrow screens, Open all day wraps onto a separate row.

The time increment is stored as `slotGranularityMinutes` plus `incrementUnit`
(`MINUTES`, `HOURS` or `DAYS`) and `alignToIncrement`. The unit is persisted, so 24 hours
and 1 day stay distinct after a save. All arithmetic goes through a typed rule: the
frontend's `domain/bookingIncrement.ts` and `BookingSchedulingSettings` on the server
implement the same contract, pinned by the shared vectors in
`src/test/resources/booking/incrementScenarios.json`, which both the JUnit and Vitest
suites read.

- Minutes and hours are elapsed time. A booking's elapsed length must be a positive whole
  multiple of the increment, so a 90-minute booking across the spring change still lasts
  90 minutes.
- Days are calendar days in the item's timezone and must be whole numbers;
  `slotGranularityMinutes` holds the day count times 1440 as a nominal value only. A Day
  booking ends at the same local time as it starts, a whole number of increments of local
  dates later, so noon to noon may last 23, 24 or 25 hours. When that same local time does
  not exist on the end date, the booking is rejected with
  `errors.api.v2.booking.dayEndMissing` instead of being moved through the gap. Day
  increments require every weekday to be open from 00:00 to 24:00.
- With "Start on clock intervals" on, the start's local minute of day must also be a
  multiple of the increment, counted from midnight in the item's timezone
  (`errors.api.v2.booking.alignment`). The end only follows the duration rule, so a
  17-minute aligned booking from 23:48 ends at 00:05. Earlier versions required both
  endpoints on the grid; that is the one semantic change for existing aligned items.
- Alignment is effective only below 1440 minutes and never for Days. The stored flag is
  not rewritten, so returning to a shorter increment restores the user's choice.

The fallback for new configurations is 15 minutes, aligned. Maintenance is exempt from
the increment and alignment, as it is from opening hours and the maximum duration.

Settings validation checks the structure of the weekly schedule. An unaligned increment
must fit one continuous opening period; touching periods merge across midnight and
across Sunday into Monday. An aligned increment also needs a grid start inside such a
period with room for a whole increment, so a 60-minute aligned increment cannot fit
09:15 to 10:15. Actual bookings must still fit the opening periods on their dates.

The maximum booking duration is always elapsed minutes. For minutes and hours it is 0
or a whole multiple of the increment. For Days it is 0 or at least the nominal increment
(`days × 1440`) with no divisibility check, because a calendar day across the autumn
change lasts 25 hours; a 1440-minute maximum therefore rejects that booking, and 1500
accepts it. The absolute cap is 527,040 elapsed minutes (8,784 hours), which some
366-calendar-day intervals exceed.

Booking forms snap an aligned start to the scheduling-zone grid on blur and snap the end
to the start plus the nearest whole number of increments (calendar days for Days),
announcing each adjustment. Both timelines use `creation/timelineRange.ts`: moves keep
the duration basis, resizes keep the other endpoint fixed, and every candidate is a
complete interval checked against increment, alignment, opening hours and maxima. An
endpoint on another day shows a striped continuation; double-click or Enter/Space moves
it to the displayed day's midnight when the result is valid, and a single click only
focuses it. Drag-to-create and the calendar's Add button produce proposals through the
same helper, so a Day proposal can extend past the displayed day.

Create and edit forms and the booking details page show a non-blocking note when an
event crosses a clock change in its scheduling timezone (`components/ClockChangeWarning.tsx`,
`domain/bookingClockChange.ts`). Detection finds the zone's offset transitions with
`start < transition <= end`, so two changes with equal offsets at both ends are still
found and a change exactly at the end counts. Offsets are sampled every six hours and
bisected, because the Temporal polyfill's own transition search steps by two weeks and
skips a change that is reversed within that step, such as Gaza's Ramadan suspensions.
Bookings and maintenance share the 527,040-minute cap, so a longer draft is already
rejected and gets no note. A booking
ending at its start's local time on a later date reads as calendar days against elapsed
hours ("It spans 2 calendar days but lasts 47 hours"); maintenance and any other booking
show their local range and elapsed duration. A
single shift is called a daylight-saving change when the zone's own English names show
daylight or summer time on exactly one side; anything else, including a permanent
standard-time change or a zone with generic names, uses "clock change" wording. The
note never replaces a validation error, nothing is stored, and an event whose scheduling
timezone is withheld from the viewer shows no note rather than a guessed one. A visually
hidden live region announces a changed interval, or changed wording such as a new time
format, once.

The Open all day checkbox selects full-day availability for the applicable open days.
Shared hours and weekday overrides use the same control. Full-day hours retain the
API representation `00:00`–`24:00`; users do not need to enter those values. A partial
day ending at midnight remains distinct from a full day. The Show all day checkbox
in display preferences changes the visible calendar window, not the item's availability.

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
module-level mutable state, and rendering one page cannot change another page's formatting. `bookingDateTimeLocaleFor(format)`
gives the locale for a particular choice, as the preference examples use. Browsers do not let a page change a native
`type="time"` input's clock, so those inputs keep the browser region's format; with an explicit
choice that differs from the region, text beside an input, such as the snapped-time note, can show
14:00 while the input shows 2:00 PM. The availability bar keeps a 24-hour clock for its compact axis. Time inputs
snap on blur as described above, and the form announces the adjusted value instead of changing
it silently.

The Time grid week view lays seven day columns on one wall-clock hour axis, using the viewer's selected
clock format. An overlap group shows
at most two lanes (`WEEK_GRID_MAX_LANES`); the rest collapse into a “+N more” slot that opens that
day in the Time grid day view. Duration-sized cards in the week grid and the booking form's day
schedule drop the booker badge, then the second title line, so the item name and time stay visible.

Global display defaults are stored on the audited `BookingConfigurationDefaults` singleton. The
initial values are `08:00`–`18:00`, Browser mode, no custom timezone, and the Automatic time
format (`timeFormat` column, added by `changeLog-booking-time-format.xml`). A user override is one
versioned JSON document stored under `BOOKING_DISPLAY_PREFERENCES` in the existing
`UserPreference` system. `timeFormat` was added to version 1 of that document; a stored document
without it reads as Automatic, the behaviour it was saved under. `BookingDisplayPreferencesManager` owns serialization, versioning,
validation, fallback, and preference access. Blank, corrupt, or unsupported stored documents fall
back to the current global values.

The current-subject REST API is:

```text
GET    /api/v2/users/me/booking-preferences
PUT    /api/v2/users/me/booking-preferences
DELETE /api/v2/users/me/booking-preferences
```

PUT is a complete replacement, not a patch; an omitted `timeFormat` means `AUTOMATIC`. The
`timeFormat` field on this API and on `/api/v2/booking-settings` accepts exactly `AUTOMATIC`,
`H12` or `H24`: other strings, differently cased names and JSON numbers (which Jackson would
otherwise read as enum ordinals) receive `400 Bad Request` with `errors.api.v2.invalidRequest`.
DELETE removes the logical override. During run-as,
the subject owns the preference and the actor remains available for audit context.

The preference editor keeps unsaved input against the server values loaded when editing began.
Background refreshes update a clean form, but preserve a dirty draft and its original baseline.
Successful Save or Reset adopts the returned server values and clears the draft, including Reset
when the server document is unchanged. Failed requests retain the draft. Reverting every field to
the editing baseline makes the form clean and adopts the latest cached server values.

Booking preferences and the sysadmin Settings page edit these values, including the Time format
radios (each labelled with an example time in that format), with the same
`BookingDisplaySettingsFields`. The custom timezone field is rendered only while Custom is
selected. Every other mode submits `customTimezone: null`, as the input schema requires, so the
component keeps the last custom zone in memory and restores it when the user switches back to Custom
before saving; Custom without a remembered zone starts at `UTC`. A hidden field has no validation
error, so an invalid custom zone blocks Save only while Custom is selected.

## Weekly scheduling defaults

The `openingHours` schema and weekday rules are defined in [BookingScheduling.md](BookingScheduling.md).

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

Calendar availability resolves each weekday's hours in the item's scheduling timezone. Missing
weekdays are unavailable for the whole local day. The interval helper merges touching openings
and follows Java's DST boundary resolution, including `24:00` at the next midnight.

Booking creation and edits validate the whole absolute window against the independent weekday
entries. Touching openings permit a booking across midnight; any closed gap rejects it.
Maintenance keeps its explicit opening-hours bypass. Non-uniform schedules appear as a weekly
readout alongside the item's scheduling timezone.

Catalogue availability projections and cache signatures include the full `openingHours` schedule.
Changing any weekday recalculates the quick-filter category, including within the current minute.

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
View, layout, and the Mine/My items quick filters use nuqs-backed URL parameters.
Item filters and Search use
`calendar-resources.where` and `calendar-resources.q`;
event filters use `calendar-events.where`.
The page coordinates date navigation and display-state reset, pausing event
fetching until they settle to avoid requests for an intermediate date/period.

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
the content and facts columns. Keep these placeholders aligned when changing a
page layout. Availability-filter counts reserve space before their values arrive
so they do not wrap the narrow-screen toolbar on completion.

Before mounting the Booking shell or sidebar, AppShell waits for feature flags. Disabled or
unavailable Booking flags redirect to `/workspace` without issuing Booking queries.
When Booking is enabled, the Inventory sidebar links to `/booking`; the legacy global AppBar
does not include a separate Booking link.
The Booking sidebar's Administration group lists Settings for sysadmins and Bookable Items for
users who can read at least one booking configuration or set up an eligible instrument
(`useCanOpenBookableItemsAdministration`: a one-row configurations count and the Add form's blank
target browse, both cached for 10 minutes); with neither, the group is omitted. The Bookable Items
page hides Add until an eligible target exists. Settings renders a not-permitted state for
non-sysadmins without calling its sysadmin-only API. The Settings and bookable item queries do not
retry permanent 4xx answers (`pages/queryRetry.ts`), so a missing or unreadable item shows its
not-found state at once; network failures and 5xx responses keep the default three retries.
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
sheet when pressed. The wide layout gives the form 65% and the sidebar 35%
of the space remaining after the column gap. The form fills its column without
an additional width cap, with the timeline below the item information card. Opening the
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

## REST API compatibility

The `timezone` field on `/api/v2/booking-configurations` is the item's scheduling timezone. Single
and bulk creates may set it to an IANA zone ID; blank or unknown zones receive `400 Bad Request`
from the entity validation. Creates that omit it are assigned the JVM-backed institution timezone.
It is immutable afterwards: patch and bulk-update requests that include it receive the collection
framework's normal `400 Bad Request` response. The Add bookable item form offers it as a searchable
select that defaults to the institution timezone; the edit form omits it.

Integration forms and inline calendar edits use the complete `openingHours` schedule. Maintenance availability remains all day, and a failed configuration save retains each weekday draft. Generated OpenAPI fixtures retain their named read components when combining booking endpoints.

## Item filters on the dashboard

The dashboard's Bookable items controls scope both Upcoming bookings and the monthly
summary. `dashboard.where` stores the expression in the URL. Predicates reach the server
before the five-row upcoming limit and the 1,000-event monthly limit. Both widgets wait
while saved custom-field definitions load. Missing or invalid definitions retain the saved
expression and require an explicit reset; a failed definition request offers retry.
