-- Dev-only edge cases layered on booking-dev-seed.sql. Items keep their seed ids. User ids:
-- sysadmin1=-12, user1a=-1, user2b=-3, user3c=-7. JSON values must stay in the canonical form
-- written by BookingOpeningHoursCodec (no spaces, weekdays ascending, ISO 1=Monday..7=Sunday).
-- Event times are UTC, computed with DATE_ADD/WEEKDAY (WEEKDAY: 0=Monday..6=Sunday) because the
-- dev database may have no time-zone tables for CONVERT_TZ.
--
-- The seed's own events sit at 08:00 and 11:00 on CURRENT_DATE-3, CURRENT_DATE and
-- CURRENT_DATE+7, and some now fall outside the new hours, on purpose: existing bookings stay
-- visible after hours change. Weekday-anchored events below start from CURRENT_DATE+8 so they
-- never overlap those seed events on items that do not allow double booking.

-- Europe/Berlin: Mon-Fri 08:00-18:00, Friday 08:00-12:00, weekend closed.
UPDATE BookingConfiguration
SET openingStart = '08:00', openingEnd = '18:00', openDays = '[1,2,3,4,5]',
    openingExceptions = '[{"dayOfWeek":5,"start":"08:00","end":"12:00"}]',
    configurationVersion = configurationVersion + 1, updatedAt = NOW(6), updatedBy_id = -12
WHERE id = 910100002;

-- UTC: open all day, Mon-Fri only.
UPDATE BookingConfiguration
SET openDays = '[1,2,3,4,5]',
    configurationVersion = configurationVersion + 1, updatedAt = NOW(6), updatedBy_id = -12
WHERE id = 910100003;

-- UTC: evening hours every day. A partial day cannot close at 24:00 (only 00:00-24:00 may end
-- at midnight), so 23:00 is the closing time.
UPDATE BookingConfiguration
SET openingStart = '18:00', openingEnd = '23:00',
    configurationVersion = configurationVersion + 1, updatedAt = NOW(6), updatedBy_id = -12
WHERE id = 910100004;

-- America/New_York: 08:00-20:00, Sunday closed, Wednesday open all day by exception.
UPDATE BookingConfiguration
SET openingStart = '08:00', openingEnd = '20:00', openDays = '[1,2,3,4,5,6]',
    openingExceptions = '[{"dayOfWeek":3,"start":"00:00","end":"24:00"}]',
    configurationVersion = configurationVersion + 1, updatedAt = NOW(6), updatedBy_id = -12
WHERE id = 910100005;

-- Pacific/Honolulu (UTC-10): 17:00-23:00, Sunday closed. A viewer's weekday differs from the
-- item's for most of the opening window.
UPDATE BookingConfiguration
SET timeZone = 'Pacific/Honolulu', openingStart = '17:00', openingEnd = '23:00',
    openDays = '[1,2,3,4,5,6]',
    configurationVersion = configurationVersion + 1, updatedAt = NOW(6), updatedBy_id = -12
WHERE id = 910100006;

-- Asia/Singapore (UTC+8): 05:00-13:00 every day, which straddles a European viewer's midnight.
UPDATE BookingConfiguration
SET openingStart = '05:00', openingEnd = '13:00',
    configurationVersion = configurationVersion + 1, updatedAt = NOW(6), updatedBy_id = -12
WHERE id = 910100007;

-- Asia/Kolkata (UTC+05:30, fractional offset): 15-minute slots, 09:00-17:30 every day.
UPDATE BookingConfiguration
SET timeZone = 'Asia/Kolkata', slotGranularityMinutes = 15, openingStart = '09:00',
    openingEnd = '17:30',
    configurationVersion = configurationVersion + 1, updatedAt = NOW(6), updatedBy_id = -12
WHERE id = 910100008;

-- 910100002, first weekend from CURRENT_DATE+8: maintenance on a closed Saturday
-- (10:00-12:00 UTC) and an existing booking left on a closed Sunday (10:00-11:00 UTC).
INSERT INTO TimeSlotBooking
  (bookingConfiguration_id, requester_id, startTime, endTime, state, purpose, deleted,
   createdAt, updatedAt, createdBy_id, updatedBy_id, kind, version)
SELECT 910100002, ev.requester,
       DATE_ADD(DATE_ADD(w.saturday, INTERVAL ev.dayOffset DAY), INTERVAL ev.startHour HOUR),
       DATE_ADD(DATE_ADD(w.saturday, INTERVAL ev.dayOffset DAY), INTERVAL ev.endHour HOUR),
       'CONFIRMED', ev.purpose, b'0', NOW(6), NOW(6), ev.requester, ev.requester, ev.kind, 0
FROM
  (SELECT DATE_ADD(b.base, INTERVAL MOD(5 - WEEKDAY(b.base) + 7, 7) DAY) AS saturday
   FROM (SELECT DATE_ADD(CURRENT_DATE, INTERVAL 8 DAY) AS base) b) w
  CROSS JOIN
  (SELECT 'MAINTENANCE' AS kind, -12 AS requester, 0 AS dayOffset, 10 AS startHour,
          12 AS endHour, 'Dev booking edge case: maintenance on a closed Saturday' AS purpose
   UNION ALL
   SELECT 'BOOKING', -1, 1, 10, 11, 'Dev booking edge case: booking on a closed Sunday') ev;

-- 910100003, first Monday from CURRENT_DATE+8: Monday 00:00 to Wednesday 00:00 UTC, spanning
-- two consecutive all-day open days.
INSERT INTO TimeSlotBooking
  (bookingConfiguration_id, requester_id, startTime, endTime, state, purpose, deleted,
   createdAt, updatedAt, createdBy_id, updatedBy_id, kind, version)
SELECT 910100003, -3, w.monday, DATE_ADD(w.monday, INTERVAL 2 DAY), 'CONFIRMED',
       'Dev booking edge case: spans two open days, midnight to midnight', b'0', NOW(6),
       NOW(6), -3, -3, 'BOOKING', 0
FROM
  (SELECT DATE_ADD(b.base, INTERVAL MOD(7 - WEEKDAY(b.base), 7) DAY) AS monday
   FROM (SELECT DATE_ADD(CURRENT_DATE, INTERVAL 8 DAY) AS base) b) w;

-- 910100004, CURRENT_DATE+1: 22:00-23:00 UTC, ending exactly at closing time.
INSERT INTO TimeSlotBooking
  (bookingConfiguration_id, requester_id, startTime, endTime, state, purpose, deleted,
   createdAt, updatedAt, createdBy_id, updatedBy_id, kind, version)
VALUES
  (910100004, -7,
   DATE_ADD(DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY), INTERVAL 22 HOUR),
   DATE_ADD(DATE_ADD(CURRENT_DATE, INTERVAL 1 DAY), INTERVAL 23 HOUR),
   'CONFIRMED', 'Dev booking edge case: ends exactly at closing time', b'0', NOW(6), NOW(6),
   -7, -7, 'BOOKING', 0);
