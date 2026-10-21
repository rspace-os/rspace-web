# Booking opening hours

Booking configurations and institution defaults expose one `openingHours` array.
Each row has an integral ISO `dayOfWeek` from 1 for Monday to 7 for Sunday, a
`start` in `HH:mm`, and an exclusive `end` in `HH:mm` or `24:00`. The start must
precede the end. Schedules contain one to seven unique weekdays and return in
weekday order. Omitted weekdays are closed.
Initial defaults contain seven independent `00:00` to `24:00` entries. To close
all days, disable bookings; an empty week is invalid.

The collection declares the three row fields through `BookingOpeningHoursCodec`.
The singleton settings PATCH and persistence converter use that same contract.
The read-only `roleSources` array also declares its children and nested grantee,
preserving numeric group IDs and the `ALL_USERS` audience ID.

A supplied schedule replaces the whole week. Omission on create copies the
complete institution schedule; later defaults changes leave existing items
unchanged. Omission on update preserves the current schedule. Collection writes
reject explicit null, while the singleton PATCH treats null as unchanged.

Booking policy resolves each weekday in the scheduling timezone. Coverage is
end-exclusive and can join consecutive intervals at midnight. Maintenance keeps
its existing exemptions from opening hours and maximum duration. Granularity,
buffers, permissions, and configuration versions keep their existing behavior.

Apply the additive independent-hours migration before running this application.
Legacy columns remain for migration and historical data; application code no
longer reads or writes them. See [booking database migrations](BookingDatabaseMigrations.md).

Audit snapshots store `openingHours`. My RSpace's audit response and CSV export use the same
formatter for weekday/time rows. Historical `openingStart`, `openingEnd`, `openDays` and
`openingExceptions` remain readable; unknown or deleted schedules keep their historical values.
The formatter does not rewrite recorded snapshot JSON.
