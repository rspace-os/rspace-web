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

The preference editor keeps unsaved input against the server values loaded when editing began.
Background refreshes update a clean form, but preserve a dirty draft and its original baseline.
Successful Save or Reset adopts the returned server values and clears the draft, including Reset
when the server document is unchanged. Failed requests retain the draft. Reverting every field to
the editing baseline makes the form clean and adopts the latest cached server values.

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

## REST API compatibility

The `timezone` field on `/api/v2/booking-configurations` is now read-only. It remains available in
GET responses as the item's scheduling timezone, but create, patch, and bulk requests that include
it receive the collection framework's normal `400 Bad Request` response. Public creates assign the
JVM-backed institution timezone. This is an immediate breaking change; internal Java manager
commands retain their timezone fields for fixtures and other trusted scheduling workflows.
