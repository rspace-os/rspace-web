package com.researchspace.dao;

import static com.researchspace.Constants.ADMIN_ROLE;
import static com.researchspace.Constants.PI_ROLE;
import static com.researchspace.Constants.SYSADMIN_ROLE;
import static com.researchspace.booking.service.BookingResourceRoleScheme.BOOKER;
import static com.researchspace.booking.service.BookingResourceRoleScheme.OWNER;
import static com.researchspace.booking.service.BookingResourceRoleScheme.VIEWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.permissions.RecordSharingACL;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class InstrumentDaoBookingPermissionTest extends SpringTransactionalTest {

  @Autowired private InstrumentDao instrumentDao;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  void bulkBookingAccessMatchesEntityPermissionMatrix() throws Exception {
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"));
    User member = createAndSaveUserIfNotExists(getRandomAlphabeticString("member"));
    User outside = createAndSaveUserIfNotExists(getRandomAlphabeticString("outside"));
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), PI_ROLE);
    User sharingPi = createAndSaveUserIfNotExists(getRandomAlphabeticString("sharePi"), PI_ROLE);
    User communityAdmin =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("admin"), ADMIN_ROLE);
    User sysadmin =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("sysadmin"), SYSADMIN_ROLE);
    initialiseContentWithEmptyContent(
        owner, member, outside, pi, sharingPi, communityAdmin, sysadmin);

    Group ownerGroup = createGroup(getRandomAlphabeticString("ownerGroup"), sharingPi);
    addUsersToGroup(sharingPi, ownerGroup, owner, member);
    Group visibilityGroup = createGroup(getRandomAlphabeticString("visibilityGroup"), pi);
    addUsersToGroup(pi, visibilityGroup, owner);
    Group whitelistGroup = createGroup(getRandomAlphabeticString("whitelistGroup"), sharingPi);
    addUsersToGroup(sharingPi, whitelistGroup, member);

    Community community =
        createAndSaveCommunity(communityAdmin, getRandomAlphabeticString("community"));
    logoutAndLoginAs(communityAdmin);
    communityMgr.addGroupToCommunity(visibilityGroup.getId(), community.getId(), communityAdmin);

    owner = userMgr.get(owner.getId());
    member = userMgr.get(member.getId());
    outside = userMgr.get(outside.getId());
    pi = userMgr.get(pi.getId());
    communityAdmin = userMgr.get(communityAdmin.getId());
    sysadmin = userMgr.get(sysadmin.getId());

    long ownerGroupsId = createBasicInstrumentForUser(owner, "owner groups").getId();
    long whitelistId = createBasicInstrumentForUser(owner, "whitelist").getId();
    Instrument whitelist = instrumentDao.get(whitelistId);
    whitelist.setSharingMode(InventorySharingMode.WHITELIST);
    whitelist.setSharingACL(
        RecordSharingACL.createACLForUserOrGroup(whitelistGroup, PermissionType.WRITE));
    instrumentDao.save(whitelist);

    long privateId = createBasicInstrumentForUser(owner, "private").getId();
    Instrument privateInstrument = instrumentDao.get(privateId);
    privateInstrument.setSharingMode(InventorySharingMode.OWNER_ONLY);
    instrumentDao.save(privateInstrument);

    long deletedId = createBasicInstrumentForUser(owner, "deleted").getId();
    Instrument deleted = instrumentDao.get(deletedId);
    deleted.setRecordDeleted(true);
    instrumentDao.save(deleted);
    flushDatabaseState();

    assertProjectedRole(ownerGroupsId, owner, OWNER);
    assertProjectedRole(ownerGroupsId, member, BOOKER);
    assertProjectedRole(whitelistId, member, BOOKER);
    assertProjectedRole(whitelistId, pi, VIEWER);
    assertProjectedRole(whitelistId, communityAdmin, VIEWER);
    assertProjectedRole(whitelistId, sysadmin, OWNER);
    assertProjectedRole(whitelistId, outside, null);
    assertProjectedRole(privateId, member, null);
    assertProjectedRole(deletedId, owner, null);
    assertProjectedRole(Long.MAX_VALUE, owner, null);
  }

  private void assertProjectedRole(Long instrumentId, User subject, String expectedRole) {
    Map<Long, InstrumentDao.BookingItemAccess> projected =
        instrumentDao.getBookingItemAccess(Set.of(instrumentId), subject);
    Optional<Instrument> instrument = instrumentDao.getSafeNull(instrumentId);
    String entityRole = entityRole(instrument.orElse(null), subject);
    assertEquals(expectedRole, entityRole);
    if (expectedRole == null) {
      assertFalse(projected.containsKey(instrumentId));
      return;
    }

    InstrumentDao.BookingItemAccess access = projected.get(instrumentId);
    assertTrue(access != null);
    assertEquals(instrument.get().getOwner().getUsername(), access.ownerUsername());
    assertEquals(
        subject.hasSysadminRole()
            || invPermissionUtils.canUserEditInventoryRecord(instrument.get(), subject),
        access.directEdit());
  }

  private String entityRole(Instrument instrument, User subject) {
    if (instrument == null
        || instrument.isDeleted()
        || instrument.isTemplate()
        || instrument.getOwner() == null
        || (!subject.hasSysadminRole()
            && !invPermissionUtils.canUserReadInventoryRecord(instrument, subject))) {
      return null;
    }
    if (subject.hasSysadminRole()
        || instrument.getOwner().getUsername().equals(subject.getUsername())) {
      return OWNER;
    }
    return invPermissionUtils.canUserEditInventoryRecord(instrument, subject) ? BOOKER : VIEWER;
  }
}
