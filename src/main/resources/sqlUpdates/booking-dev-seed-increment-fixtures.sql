-- Extend only the fresh dev seed. Existing or edited configurations are left untouched.
UPDATE BookingConfiguration
SET slotGranularityMinutes = 15
WHERE id IN (910100001, 910100002, 910100003, 910100004,
             910100005, 910100006, 910100007, 910100008);

INSERT INTO InstrumentEntity
  (id, createdBy, creationDate, modificationDate, modifiedBy, deleted, name, description,
   version, sharingMode, owner_id, DTYPE, isEditable, currMaxColIndex,
   creationDateMillis, modificationDateMillis)
VALUES
  (910100010, 'user1a', NOW(), NOW(), 'user1a', b'0',
   'Dev booking instrument 10 (1-day increments)', 'Fresh dev booking seed',
   1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000),
  (910100011, 'user1a', NOW(), NOW(), 'user1a', b'0',
   'Dev booking instrument 11 (15 minutes, any start)', 'Fresh dev booking seed',
   1, 0, -1, 'Instrument', b'1', 0, UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000);

INSERT INTO ResourceAccess
  (id, schemeKey, version, createdAt, updatedAt, createdBy_id, updatedBy_id)
VALUES
  (910100010, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12),
  (910100011, 'booking-configurations', 0, NOW(6), NOW(6), -12, -12);

INSERT INTO BookingConfiguration
  (id, enabled, timeZone, targetType, targetId, configurationVersion, createdAt, updatedAt,
   createdBy_id, updatedBy_id, resourceAccess_id, slotGranularityMinutes, incrementUnit,
   alignToIncrement, openingStart, openingEnd, bufferBeforeMinutes, bufferAfterMinutes,
   allowDoubleBooking, maxBookingDurationMinutes, state)
VALUES
  (910100010, b'1', 'Europe/Berlin', 'INSTRUMENT', 910100010, 0, NOW(6), NOW(6), -12, -12,
   910100010, 1440, 'DAYS', b'1', '00:00', '24:00', 0, 0, b'0', 0, 'ACTIVE'),
  (910100011, b'1', 'UTC', 'INSTRUMENT', 910100011, 0, NOW(6), NOW(6), -12, -12,
   910100011, 15, 'MINUTES', b'0', '00:00', '24:00', 0, 0, b'0', 0, 'ACTIVE');

INSERT INTO ResourceRoleAssignment
  (resourceAccess_id, roleKey, granteeKey, granteeKind, user_id, group_id, audienceKey,
   nameSnapshot, detailSnapshot)
VALUES
  (910100010, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100011, 'OWNER', 'user:-12', 'USER', -12, NULL, NULL, 'System Admin', 'sysadmin1'),
  (910100010, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL),
  (910100011, 'BOOKER', 'audience:all-users', 'AUDIENCE', NULL, NULL, 'ALL_USERS', 'All users', NULL);
