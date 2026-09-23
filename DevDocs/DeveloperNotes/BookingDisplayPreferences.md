# Booking display preferences

Booking keeps presentation choices separate from scheduling rules:

- The **display timezone** and **availability window** come from the current user's Booking
  preference, falling back to the global Booking defaults. They control rendered dates and times,
  route-level “today”, booking-form wall clocks, availability domains, and the Now marker.
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
display interval: before it starts, an item with a free segment is “Free later today”; at or after
its end, it matches neither filter. Loading these counts does not block the unfiltered catalogue.
Candidate discovery uses the catalogue's Search, type and item-property predicates before applying
the 1,000-item availability limit. Local availability comparisons become logical true when deriving
that candidate predicate, preserving AND/OR grouping. Counts therefore describe the current item
scope. Today's availability bars reuse the same booking intervals; other dates load their own
intervals. Both event and availability caches are scoped to the caller.

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
Forms explicitly label the display timezone and render conflict times in that same timezone,
including when the item's scheduling timezone differs.

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
