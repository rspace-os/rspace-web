-- Fresh dev-only booking catalogue. User ids come from initial-seed-run.sql and
-- initial-seed-devtest.sql: sysadmin1=-12, user1a=-1, user2b=-3, user3c=-7.
-- The high, reserved ids keep this fixture separate from generated inventory rows.

INSERT INTO InstrumentEntity
  (id, createdBy, creationDate, modificationDate, modifiedBy, deleted, name, description,
   version, sharingMode, owner_id, DTYPE, isEditable, currMaxColIndex,
   creationDateMillis, modificationDateMillis)
VALUES
  (910100001, 'user1a', NOW(), NOW(), 'user1a', b'0', 'Dev booking instrument 01',
   'Fresh dev booking seed', 1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000,
   UNIX_TIMESTAMP() * 1000),
  (910100002, 'user1a', NOW(), NOW(), 'user1a', b'0', 'Dev booking instrument 02',
   'Fresh dev booking seed', 1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000,
   UNIX_TIMESTAMP() * 1000),
  (910100003, 'user1a', NOW(), NOW(), 'user1a', b'0', 'Dev booking instrument 03',
   'Fresh dev booking seed', 1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000,
   UNIX_TIMESTAMP() * 1000),
  (910100004, 'user1a', NOW(), NOW(), 'user1a', b'0', 'Dev booking instrument 04',
   'Fresh dev booking seed', 1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000,
   UNIX_TIMESTAMP() * 1000),
  (910100005, 'user1a', NOW(), NOW(), 'user1a', b'0', 'Dev booking instrument 05',
   'Fresh dev booking seed', 1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000,
   UNIX_TIMESTAMP() * 1000),
  (910100006, 'user1a', NOW(), NOW(), 'user1a', b'0', 'Dev booking instrument 06',
   'Fresh dev booking seed', 1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000,
   UNIX_TIMESTAMP() * 1000),
  (910100007, 'user1a', NOW(), NOW(), 'user1a', b'0', 'Dev booking instrument 07',
   'Fresh dev booking seed', 1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000,
   UNIX_TIMESTAMP() * 1000),
  (910100008, 'user1a', NOW(), NOW(), 'user1a', b'0', 'Dev booking instrument 08',
   'Fresh dev booking seed', 1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000,
   UNIX_TIMESTAMP() * 1000),
  (910100009, 'user1a', NOW(), NOW(), 'user1a', b'0', 'Dev booking instrument 09 (no configuration)',
   'Fresh dev booking seed', 1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000,
   UNIX_TIMESTAMP() * 1000);

INSERT INTO ResourceAccess
  (id, schemeKey, version, createdAt, updatedAt, createdBy_id, updatedBy_id)
VALUES
  (910100001, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12),
  (910100002, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12),
  (910100003, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12),
  (910100004, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12),
  (910100005, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12),
  (910100006, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12),
  (910100007, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12),
  (910100008, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12);

INSERT INTO BookingConfiguration
  (id, enabled, timeZone, targetType, targetId, configurationVersion, createdAt, updatedAt,
   createdBy_id, updatedBy_id, resourceAccess_id, slotGranularityMinutes, openingStart,
   openingEnd, bufferBeforeMinutes, bufferAfterMinutes, allowDoubleBooking,
   maxBookingDurationMinutes, state)
VALUES
  (910100001, b'1', 'Europe/Berlin', 'INSTRUMENT', 910100001, 0, NOW(6), NOW(6), -12, -12, 910100001, 5, '00:00', '24:00', 0, 0, b'1', 0, 'ACTIVE'),
  (910100002, b'1', 'Europe/Berlin', 'INSTRUMENT', 910100002, 0, NOW(6), NOW(6), -12, -12, 910100002, 5, '00:00', '24:00', 0, 0, b'0', 0, 'ACTIVE'),
  (910100003, b'1', 'UTC',            'INSTRUMENT', 910100003, 0, NOW(6), NOW(6), -12, -12, 910100003, 5, '00:00', '24:00', 0, 0, b'0', 0, 'ACTIVE'),
  (910100004, b'1', 'UTC',            'INSTRUMENT', 910100004, 0, NOW(6), NOW(6), -12, -12, 910100004, 5, '00:00', '24:00', 0, 0, b'0', 0, 'ACTIVE'),
  (910100005, b'1', 'America/New_York','INSTRUMENT', 910100005, 0, NOW(6), NOW(6), -12, -12, 910100005, 5, '00:00', '24:00', 0, 0, b'0', 0, 'ACTIVE'),
  (910100006, b'1', 'America/New_York','INSTRUMENT', 910100006, 0, NOW(6), NOW(6), -12, -12, 910100006, 5, '00:00', '24:00', 0, 0, b'0', 0, 'ACTIVE'),
  (910100007, b'1', 'Asia/Singapore', 'INSTRUMENT', 910100007, 0, NOW(6), NOW(6), -12, -12, 910100007, 5, '00:00', '24:00', 0, 0, b'0', 0, 'ACTIVE'),
  (910100008, b'1', 'Asia/Singapore', 'INSTRUMENT', 910100008, 0, NOW(6), NOW(6), -12, -12, 910100008, 5, '00:00', '24:00', 0, 0, b'0', 0, 'ACTIVE');

INSERT INTO ResourceRoleAssignment
  (resourceAccess_id, roleKey, granteeKey, granteeKind, user_id, group_id, audienceKey,
   nameSnapshot, detailSnapshot)
VALUES
  (910100001, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100002, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100003, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100004, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100005, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100006, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100007, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100008, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100001, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL),
  (910100002, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL),
  (910100003, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL),
  (910100004, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL),
  (910100005, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL),
  (910100006, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL),
  (910100007, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL),
  (910100008, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL);

-- 8 configured instruments x 3 dates x 2 events = 48 events. The requesters cycle through
-- sysadmin and users 1-3. The final two events overlap on the double-booking instrument.
INSERT INTO TimeSlotBooking
  (bookingConfiguration_id, requester_id, startTime, endTime, state, purpose, deleted,
   createdAt, updatedAt, createdBy_id, updatedBy_id, kind, version)
SELECT cfg.id,
       CASE MOD(cfg.id + days.dayOffset + slots.slot, 4)
         WHEN 0 THEN -12 WHEN 1 THEN -1 WHEN 2 THEN -3 ELSE -7 END,
       DATE_ADD(DATE_ADD(CURRENT_DATE, INTERVAL days.dayOffset DAY), INTERVAL slots.startHour HOUR),
       DATE_ADD(DATE_ADD(CURRENT_DATE, INTERVAL days.dayOffset DAY), INTERVAL (slots.startHour + 1) HOUR),
       'CONFIRMED', CONCAT('Fresh dev booking seed event ', cfg.id, ' ', days.dayOffset, ' ', slots.slot),
       b'0', NOW(6), NOW(6), -12, -12, 'BOOKING', 0
FROM
  (SELECT 910100001 AS id UNION ALL SELECT 910100002 UNION ALL SELECT 910100003 UNION ALL
   SELECT 910100004 UNION ALL SELECT 910100005 UNION ALL SELECT 910100006 UNION ALL
   SELECT 910100007 UNION ALL SELECT 910100008) cfg
  CROSS JOIN (SELECT -3 AS dayOffset UNION ALL SELECT 0 UNION ALL SELECT 7) days
  CROSS JOIN (SELECT 8 AS startHour, 1 AS slot UNION ALL SELECT 11, 2) slots;

INSERT INTO TimeSlotBooking
  (bookingConfiguration_id, requester_id, startTime, endTime, state, purpose, deleted,
   createdAt, updatedAt, createdBy_id, updatedBy_id, kind, version)
VALUES
  (910100001, -1, DATE_ADD(CURRENT_DATE, INTERVAL 8 HOUR), DATE_ADD(CURRENT_DATE, INTERVAL 9 HOUR),
   'CONFIRMED', 'Fresh dev seed overlapping event A', b'0', NOW(6), NOW(6), -1, -1, 'BOOKING', 0),
  (910100001, -3, DATE_ADD(CURRENT_DATE, INTERVAL 8 HOUR), DATE_ADD(CURRENT_DATE, INTERVAL 9 HOUR),
   'CONFIRMED', 'Fresh dev seed overlapping event B', b'0', NOW(6), NOW(6), -3, -3, 'BOOKING', 0);
