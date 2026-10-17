package com.researchspace.booking.service;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.apache.shiro.authz.AuthorizationException;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class BookingCurrentAccessIT extends RealTransactionSpringTestBase {

  private final BookingConfigurationManager configurationManager;
  private final InstrumentDao instrumentDao;

  @Autowired
  BookingCurrentAccessIT(
      BookingConfigurationManager configurationManager, InstrumentDao instrumentDao) {
    this.configurationManager = configurationManager;
    this.instrumentDao = instrumentDao;
  }

  @BeforeEach
  void initializeBaseFixture() throws Exception {
    super.setUp();
  }

  @Test
  void configurationMutationRejectsGroupMembershipRevokedBeforeConfigurationLock() {
    User owner = createInitAndLoginAnyUser();
    User subject = createAndSaveUser(getRandomName(10));
    initUser(piUser);
    initUser(subject);
    Group group = createGroupForPiAndUsers(piUser, new User[] {subject});
    ApiInstrument created = createBasicInstrumentForUser(owner, "Current authorization scope");
    Instrument instrument = initializedInstrument(created.getId());
    ResolvedBookableTarget target =
        new ResolvedBookableTarget(
            new BookableTargetReference(BookableTargetType.INSTRUMENT, instrument.getId()),
            instrument);
    BookingConfiguration configuration =
        configurationManager.createConfiguration(
            new BookingConfigurationManager.Create(true, "UTC", target), owner, owner);
    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction -> {
              BookingConfiguration persisted =
                  sessionFactory
                      .getCurrentSession()
                      .find(BookingConfiguration.class, configuration.getId());
              Group persistedGroup =
                  sessionFactory.getCurrentSession().find(Group.class, group.getId());
              persisted
                  .getResourceAccess()
                  .addAssignment(
                      ResourceRoleAssignment.forGroup(
                          BookingResourceRoleScheme.MANAGER, persistedGroup));
              sessionFactory.getCurrentSession().flush();
            });

    TransactionStatus staleTransaction = openTransaction();
    try {
      User staleSubject = userMgr.getUserByUsername(subject.getUsername());
      assertTrue(
          staleSubject.getGroups().stream().anyMatch(value -> group.getId().equals(value.getId())));
      assertTrue(
          configurationManager.getConfiguration(configuration.getId(), staleSubject).isPresent());

      TransactionTemplate revoke = new TransactionTemplate(getTxMger());
      revoke.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      revoke.executeWithoutResult(
          transaction -> {
            int removed =
                sessionFactory
                    .getCurrentSession()
                    .createNativeMutationQuery(
                        "DELETE FROM UserGroup WHERE user_id = :userId AND group_id = :groupId")
                    .setParameter("userId", staleSubject.getId())
                    .setParameter("groupId", group.getId())
                    .executeUpdate();
            assertEquals(1, removed);
          });

      assertTrue(
          staleSubject.getGroups().stream().anyMatch(value -> group.getId().equals(value.getId())));
      assertThrows(
          AuthorizationException.class,
          () ->
              configurationManager.updateConfiguration(
                  configuration.getId(),
                  new BookingConfigurationManager.Patch(false, null),
                  staleSubject,
                  staleSubject));
    } finally {
      getTxMger().rollback(staleTransaction);
    }

    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction -> {
              BookingConfiguration unchanged =
                  sessionFactory
                      .getCurrentSession()
                      .find(BookingConfiguration.class, configuration.getId());
              assertTrue(unchanged.isEnabled());
            });
  }

  @Test
  void instrumentLockRefreshesOwnerAndDeletedStateLoadedBeforeConcurrentCommit() {
    User originalOwner = createInitAndLoginAnyUser();
    User newOwner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(originalOwner, "Current instrument scope");

    TransactionStatus staleTransaction = openTransaction();
    try {
      Instrument stale = instrumentDao.get(created.getId());
      Hibernate.initialize(stale.getOwner());
      assertEquals(originalOwner.getId(), stale.getOwner().getId());
      assertFalse(stale.isDeleted());

      TransactionTemplate change = new TransactionTemplate(getTxMger());
      change.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      change.executeWithoutResult(
          transaction -> {
            int updated =
                sessionFactory
                    .getCurrentSession()
                    .createNativeMutationQuery(
                        "UPDATE InstrumentEntity SET owner_id = :ownerId, deleted = true, "
                            + "version = version + 1 WHERE id = :instrumentId")
                    .setParameter("ownerId", newOwner.getId())
                    .setParameter("instrumentId", created.getId())
                    .executeUpdate();
            assertEquals(1, updated);
          });

      assertThrows(DataAccessException.class, () -> instrumentDao.lockById(created.getId()));
    } finally {
      getTxMger().rollback(staleTransaction);
    }

    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction -> {
              Instrument locked = instrumentDao.lockById(created.getId()).orElseThrow();
              assertEquals(newOwner.getId(), locked.getOwner().getId());
              assertTrue(locked.isDeleted());
            });
  }

  @Test
  void instrumentLockPreservesPendingDirtyState() {
    User owner = createInitAndLoginAnyUser();
    ApiInstrumentTemplate template = createBasicInstrumentTemplateForUser(owner);
    ApiInstrument request = new ApiInstrument();
    request.setName("Instrument with a field");
    request.setTemplateId(template.getId());
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, owner);
    String pendingData = "Pending field data";

    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction -> {
              Instrument instrument = instrumentDao.get(created.getId());
              instrument.getFields().get(0).setData(pendingData);

              Instrument locked = instrumentDao.lockById(created.getId()).orElseThrow();

              assertEquals(pendingData, locked.getFields().get(0).getData());
            });

    new TransactionTemplate(getTxMger())
        .executeWithoutResult(
            transaction ->
                assertEquals(
                    pendingData, instrumentDao.get(created.getId()).getFields().get(0).getData()));
  }

  private Instrument initializedInstrument(Long instrumentId) {
    openTransaction();
    Instrument instrument = instrumentDao.get(instrumentId);
    Hibernate.initialize(instrument.getOwner());
    commitTransaction();
    return instrument;
  }
}
