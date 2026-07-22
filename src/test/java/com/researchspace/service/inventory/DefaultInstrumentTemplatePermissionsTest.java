package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Behaviour of the locked default (system) instrument template: readable and duplicable by every
 * user, but not editable / deletable / transferable by anyone (RSDEV-1219 Parts E &amp; F).
 *
 * <p>The tests obtain the default template via {@link #getOrCreateDefaultLockedTemplate()} so they
 * hold both on a DB where the Liquibase seeder has already created it (owned by the sysadmin) and
 * on one where it has not (in which case the test creates its own locked template).
 */
public class DefaultInstrumentTemplatePermissionsTest extends SpringTransactionalTest {

  @Autowired private InstrumentTemplateDao instrumentTemplateDao;

  @Before
  public void setUp() {
    instrumentTemplateDao.resetDefaultTemplateOwner();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
    instrumentTemplateDao.resetDefaultTemplateOwner();
  }

  /** Creates a template owned by (currently logged-in) owner and locks it (isEditable=false). */
  private InstrumentTemplate persistLockedTemplateFor(User owner, String name) {
    ApiInstrumentTemplate api = createBasicInstrumentTemplateForUser(owner, name);
    InstrumentTemplate entity = instrumentTemplateDao.get(api.getId());
    entity.setEditable(false);
    InstrumentTemplate saved = instrumentTemplateDao.save(entity);
    instrumentTemplateDao.resetDefaultTemplateOwner();
    return saved;
  }

  private InstrumentTemplate persistEditableTemplateFor(User owner, String name) {
    ApiInstrumentTemplate api = createBasicInstrumentTemplateForUser(owner, name);
    return instrumentTemplateDao.get(api.getId());
  }

  /**
   * Returns the locked default template (the seeded one if present, otherwise a freshly created).
   */
  private InstrumentTemplate getOrCreateDefaultLockedTemplate() {
    instrumentTemplateDao.resetDefaultTemplateOwner();
    if (instrumentTemplateDao.getDefaultTemplatesOwner() == null) {
      User owner = createInitAndLoginAnyUser();
      return persistLockedTemplateFor(owner, "locked default template");
    }
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from InstrumentTemplate where editable = false order by id asc",
            InstrumentTemplate.class)
        .setMaxResults(1)
        .getSingleResult();
  }

  @Test
  public void nonOwnerCanReadLockedDefaultInstrumentTemplate() {
    InstrumentTemplate template = getOrCreateDefaultLockedTemplate();

    User other = createInitAndLoginAnyUser();
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(template, other),
        "every user must be able to read the locked default instrument template");
  }

  @Test
  public void lockedDefaultTemplateIsNotEditableByAnyoneIncludingOwner() {
    InstrumentTemplate locked = getOrCreateDefaultLockedTemplate();
    User owner = locked.getOwner();
    User other = createInitAndLoginAnyUser();

    assertFalse(
        invPermissionUtils.canUserEditInventoryRecord(locked, owner),
        "the owning sysadmin must not be able to edit the locked template");
    assertFalse(
        invPermissionUtils.canUserEditInventoryRecord(locked, other),
        "a non-owner must not be able to edit the locked template");
  }

  @Test
  public void ordinaryInstrumentTemplateRemainsEditableByOwner() {
    User owner = createInitAndLoginAnyUser();
    InstrumentTemplate editable = persistEditableTemplateFor(owner, "ordinary template");

    assertTrue(
        invPermissionUtils.canUserEditInventoryRecord(editable, owner),
        "the lock guard must not over-block ordinary (editable) instrument templates");
  }

  @Test
  public void lockedDefaultTemplateCannotBeTransferredEvenByOwner() {
    InstrumentTemplate locked = getOrCreateDefaultLockedTemplate();
    User owner = locked.getOwner();

    assertThrows(
        IllegalArgumentException.class,
        () -> invPermissionUtils.assertUserCanTransferInventoryRecord(locked, owner));
  }

  @Test
  public void lockedTemplatePermittedActionsAreReadOnlyForOwner() {
    InstrumentTemplate locked = getOrCreateDefaultLockedTemplate();
    User owner = locked.getOwner();

    ApiInstrumentTemplate api = new ApiInstrumentTemplate(locked);
    invPermissionUtils.setPermissionsInApiInventoryRecord(api, locked, owner);

    assertTrue(api.getPermittedActions().contains(ApiInventoryRecordPermittedAction.READ));
    assertFalse(
        api.getPermittedActions().contains(ApiInventoryRecordPermittedAction.UPDATE),
        "locked template must not expose UPDATE");
    assertFalse(
        api.getPermittedActions().contains(ApiInventoryRecordPermittedAction.CHANGE_OWNER),
        "locked template must not expose CHANGE_OWNER");
  }

  @Test
  public void duplicateOfLockedTemplateIsEditable() {
    InstrumentTemplate locked = getOrCreateDefaultLockedTemplate();
    User owner = locked.getOwner();

    // duplication asserts READ only, so it stays allowed; the copy must be editable again
    ApiInstrumentTemplate duplicate =
        instrumentApiMgr.duplicateInstrumentTemplate(locked.getId(), owner);
    InstrumentTemplate duplicateEntity = instrumentTemplateDao.get(duplicate.getId());

    assertTrue(duplicateEntity.isEditable(), "a duplicate of a locked template must be editable");
  }
}
