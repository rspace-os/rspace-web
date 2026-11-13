package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.booking.dao.BookingCalendarCurrentAccessDao;
import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.RoleManager;
import com.researchspace.service.UserDeletionManager;
import com.researchspace.service.UserDeletionPolicy;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import jakarta.ws.rs.NotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Holds source transactions after their service call, before revocation finalization and commit.
 */
class PermissionSourceGateIT extends RealTransactionSpringTestBase {
  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();
    initUser(piUser);
  }

  @AfterEach
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private final BookingCalendarManager calendars;
  private final BookingCalendarPermissionGateDao gate;
  private final PlatformTransactionManager transactions;
  private final JdbcTemplate jdbc;

  @Autowired
  PermissionSourceGateIT(
      BookingCalendarManager calendars,
      BookingCalendarPermissionGateDao gate,
      PlatformTransactionManager transactions,
      JdbcTemplate jdbc) {
    this.calendars = calendars;
    this.gate = gate;
    this.transactions = transactions;
    this.jdbc = jdbc;
  }

  enum Path {
    DISABLE,
    LOCK,
    MEMBERSHIP,
    SELF_LEAVE,
    OWNER_MEMBERSHIP,
    GROUP_DELETE,
    SHARING,
    EMPTY_ACL,
    DELETE,
    GLOBAL_ROLE,
    LAB_ADMIN_VIEW,
    LAB_ADMIN_DEMOTION,
    COMMUNITY_MOVE,
    COMMUNITY_DELETE,
    COMMUNITY_ADMIN,
    OWNER_TRANSFER,
    ACCOUNT_DELETE,
    PI_REPLACEMENT,
    PI_DEMOTION,
    PROMOTION,
    INACTIVITY,
    COLLABORATION
  }

  private record Fixture(
      User reader,
      User owner,
      Long configurationId,
      Long instrumentId,
      Long survivorId,
      Runnable source) {}

  private Fixture fixture(Path path) throws Exception {
    User owner = createInitAndLoginAnyUser();
    User reader = createInitAndLoginAnyUser();
    boolean piReader = path == Path.PI_REPLACEMENT || path == Path.PI_DEMOTION;
    if (piReader) {
      changeAccount(reader, fresh -> fresh.addRole(roleMgr.getRole(Constants.PI_ROLE)));
    }
    Group group;
    if (path == Path.COLLABORATION) {
      User otherPi = createInitAndLoginAnyUser();
      changeAccount(otherPi, fresh -> fresh.addRole(roleMgr.getRole(Constants.PI_ROLE)));
      Group owners = createGroupForUsersWithDefaultPi(owner, piUser);
      Group readers = createGroupForPiAndUsers(otherPi, new User[] {reader, otherPi});
      group = createCollabGroupBetweenGroups(owners, readers);
      grpMgr.addUserToGroup(owner.getUsername(), group.getId(), RoleInGroup.DEFAULT);
      grpMgr.addUserToGroup(reader.getUsername(), group.getId(), RoleInGroup.DEFAULT);
    } else {
      group =
          piReader
              ? createGroupForPiAndUsers(reader, new User[] {owner, reader})
              : createGroupForUsersWithDefaultPi(owner, reader, piUser);
    }
    ApiInstrument instrument = createBasicInstrumentForUser(owner, "Gate revocation " + path);
    BookingConfiguration configuration = bookingConfigurationFor(instrument, owner);
    User admin = getSysAdminUser();
    RoleManager roles = applicationContext.getBean(RoleManager.class);
    CommunityServiceManager communities = applicationContext.getBean(CommunityServiceManager.class);
    Community community = new Community();
    community.setUniqueName("w5-" + UUID.randomUUID());
    community.setDisplayName(community.getUniqueName());
    if (path == Path.GLOBAL_ROLE) {
      changeAccount(reader, fresh -> fresh.addRole(roles.getRole(Constants.SYSADMIN_ROLE)));
    }
    logoutAndLoginAs(piReader ? reader : piUser);
    if (path == Path.SELF_LEAVE) {
      group.setGroupType(GroupType.PROJECT_GROUP);
      grpMgr.saveGroup(group, false, piUser);
    }
    if (path == Path.LAB_ADMIN_VIEW || path == Path.LAB_ADMIN_DEMOTION) {
      grpMgr.setRoleForUser(group.getId(), reader.getId(), "RS_LAB_ADMIN", piUser);
      grpMgr.authorizeLabAdminToViewAll(reader.getId(), piUser, group.getId(), true);
    }
    if (path == Path.COMMUNITY_MOVE
        || path == Path.COMMUNITY_DELETE
        || path == Path.COMMUNITY_ADMIN) {
      changeAccount(reader, fresh -> fresh.addRole(roles.getRole(Constants.ADMIN_ROLE)));
      community.addAdmin(userMgr.get(reader.getId()));
      community.addAdmin(admin);
      community.setGroupIds(List.of(group.getId()));
      logoutAndLoginAsSysAdmin();
      communities.saveNewCommunity(community, admin);
    }
    if (path == Path.GLOBAL_ROLE
        || path == Path.LAB_ADMIN_VIEW
        || path == Path.LAB_ADMIN_DEMOTION
        || path == Path.COMMUNITY_MOVE
        || path == Path.COMMUNITY_DELETE
        || path == Path.COMMUNITY_ADMIN
        || piReader) {
      ApiInstrument patch = new ApiInstrument();
      patch.setId(instrument.getId());
      patch.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
      instrumentApiMgr.updateApiInstrument(patch, owner);
    }
    if (path == Path.PI_REPLACEMENT) {
      changeAccount(owner, fresh -> fresh.addRole(roles.getRole(Constants.PI_ROLE)));
    }
    if (path == Path.PI_DEMOTION) {
      logoutAndLoginAs(reader);
      grpMgr.setRoleForUser(group.getId(), owner.getId(), "PI", reader);
    }
    if (path == Path.INACTIVITY) {
      for (User member : group.getMembers()) {
        changeAccount(member, fresh -> fresh.setLastLogin(new Date(0)));
      }
    }
    User recipient = path == Path.OWNER_TRANSFER ? createInitAndLoginAnyUser() : owner;
    User accountToChange = userMgr.get(reader.getId());
    var globalRole = roles.getRole(Constants.SYSADMIN_ROLE);
    User collabPi =
        path == Path.COLLABORATION
            ? doInTransaction(
                () -> {
                  User loaded = userMgr.get(piUser.getId());
                  loaded.getGroups().forEach(g -> Hibernate.initialize(g.getMembers()));
                  return loaded;
                })
            : piUser;
    User promotionTarget = userMgr.get(owner.getId());
    Runnable source =
        switch (path) {
          case COLLABORATION ->
              () -> grpMgr.removeLabGroupMembersFromCollabGroup(group.getId(), collabPi);
          case PI_REPLACEMENT -> () -> grpMgr.setNewPi(group.getId(), owner.getId(), admin);
          case PI_DEMOTION ->
              () -> grpMgr.setRoleForUser(group.getId(), reader.getId(), "DEFAULT", admin);
          case PROMOTION -> () -> grpMgr.promoteUserToPi(promotionTarget, admin);
          case INACTIVITY ->
              () -> grpMgr.removeGroupIfNoMemberLoggedInWithinOneYear(group.getId(), admin);
          case GLOBAL_ROLE ->
              () -> {
                accountToChange.removeRole(globalRole);
                userMgr.save(accountToChange);
              };
          case LAB_ADMIN_VIEW ->
              () -> grpMgr.authorizeLabAdminToViewAll(reader.getId(), piUser, group.getId(), false);
          case LAB_ADMIN_DEMOTION ->
              () -> grpMgr.setRoleForUser(group.getId(), reader.getId(), "DEFAULT", piUser);
          case COMMUNITY_MOVE ->
              () ->
                  communities.addGroupToCommunity(
                      group.getId(), Community.DEFAULT_COMMUNITY_ID, admin);
          case COMMUNITY_DELETE -> () -> communities.removeCommunity(community.getId());
          case COMMUNITY_ADMIN ->
              () -> communities.removeAdminFromCommunity(reader.getId(), community.getId());
          case OWNER_TRANSFER ->
              () -> {
                ApiInstrument patch = new ApiInstrument();
                patch.setId(instrument.getId());
                patch.setOwner(new ApiUser(recipient));
                instrumentApiMgr.changeApiInstrumentOwner(patch, owner);
              };
          case ACCOUNT_DELETE ->
              () ->
                  applicationContext
                      .getBean(UserDeletionManager.class)
                      .removeUser(
                          reader.getId(),
                          new UserDeletionPolicy(
                              UserDeletionPolicy.UserTypeRestriction.NO_RESTRICTION),
                          admin);
          case DISABLE ->
              () -> {
                accountToChange.setEnabled(false);
                userMgr.save(accountToChange);
              };
          case LOCK ->
              () -> {
                accountToChange.setAccountLocked(true);
                userMgr.save(accountToChange);
              };
          case MEMBERSHIP ->
              () -> grpMgr.removeUserFromGroup(reader.getUsername(), group.getId(), piUser);
          case SELF_LEAVE ->
              () -> grpMgr.removeUserFromGroup(reader.getUsername(), group.getId(), reader);
          case OWNER_MEMBERSHIP ->
              () -> grpMgr.removeUserFromGroup(owner.getUsername(), group.getId(), piUser);
          case GROUP_DELETE -> () -> grpMgr.removeGroup(group.getId(), piUser);
          case SHARING ->
              () -> {
                ApiInstrument patch = new ApiInstrument();
                patch.setId(instrument.getId());
                patch.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
                instrumentApiMgr.updateApiInstrument(patch, owner);
              };
          case EMPTY_ACL ->
              () -> {
                ApiInstrument patch = new ApiInstrument();
                patch.setId(instrument.getId());
                patch.setSharingMode(ApiInventorySharingMode.WHITELIST);
                patch.setSharedWith(List.of());
                instrumentApiMgr.updateApiInstrument(patch, owner);
              };
          case DELETE -> () -> instrumentApiMgr.markInstrumentAsDeleted(instrument.getId(), owner);
        };
    ApiInstrument survivor = createBasicInstrumentForUser(owner, "Unaffected calendar");
    Long survivorId = bookingConfigurationFor(survivor, owner).getId();
    calendars.create(survivorId, owner, owner);
    User operator =
        switch (path) {
          case MEMBERSHIP, OWNER_MEMBERSHIP, GROUP_DELETE -> piUser;
          case SELF_LEAVE -> reader;
          case COLLABORATION -> collabPi;
          case SHARING, EMPTY_ACL, DELETE, OWNER_TRANSFER -> owner;
          default -> admin;
        };
    if (operator.hasSysadminRole()) logoutAndLoginAsSysAdmin();
    else logoutAndLoginAs(operator);
    Subject authorized =
        new Subject.Builder()
            .principals(new SimplePrincipalCollection(SecurityUtils.getSubject().getPrincipals()))
            .authenticated(true)
            .buildSubject();
    Runnable authorizedSource = authorized.associateWith(source);
    logoutAndLoginAs(reader);
    return new Fixture(
        reader, owner, configuration.getId(), instrument.getId(), survivorId, authorizedSource);
  }

  private void changeAccount(User user, Consumer<User> change) {
    User fresh = userMgr.get(user.getId());
    change.accept(fresh);
    User saved = userMgr.save(fresh);
    user.setRoles(saved.getRoles());
    user.setVersion(saved.getVersion());
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void sourceBeforeSubscriptionDeniesAndRevokesAfterCommit(Path path) throws Exception {
    withBookingEnabled(
        () -> {
          Fixture f = fixture(path);
          calendars.create(f.configurationId(), f.reader(), f.reader());
          CountDownLatch changed = new CountDownLatch(1);
          CountDownLatch commit = new CountDownLatch(1);
          CountDownLatch subscribeStarted = new CountDownLatch(1);
          ExecutorService pool = Executors.newFixedThreadPool(2);
          try {
            Future<?> source =
                pool.submit(
                    () ->
                        new TransactionTemplate(transactions)
                            .executeWithoutResult(
                                status -> {
                                  f.source().run();
                                  changed.countDown();
                                  await(commit);
                                }));
            await(changed);
            Future<?> subscription =
                pool.submit(
                    () -> {
                      subscribeStarted.countDown();
                      return calendars.create(f.configurationId(), f.reader(), f.reader());
                    });
            await(subscribeStarted);
            assertThrows(
                TimeoutException.class, () -> subscription.get(200, TimeUnit.MILLISECONDS));
            commit.countDown();
            source.get(15, TimeUnit.SECONDS);
            ExecutionException denied =
                assertThrows(
                    ExecutionException.class, () -> subscription.get(15, TimeUnit.SECONDS));
            assertTrue(
                denied.getCause()
                        instanceof BookingCalendarManagerImpl.BookingCalendarNotFoundException
                    || denied.getCause() instanceof AuthorizationException
                    || denied.getCause() instanceof ObjectRetrievalFailureException,
                () -> denied.getCause().toString());
            assertEquals(0, linkCount(f));
            if (path == Path.DELETE) {
              instrumentApiMgr.restoreDeletedInstrument(f.instrumentId(), f.owner());
              assertEquals(0, linkCount(f));
            }
          } finally {
            commit.countDown();
            pool.shutdownNow();
          }
        });
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void subscriptionBeforeSourceLeavesNoRevokedToken(Path path) throws Exception {
    withBookingEnabled(
        () -> {
          Fixture f = fixture(path);
          CountDownLatch subscribed = new CountDownLatch(1);
          CountDownLatch commit = new CountDownLatch(1);
          ExecutorService pool = Executors.newFixedThreadPool(2);
          try {
            Future<?> subscription =
                pool.submit(
                    () ->
                        new TransactionTemplate(transactions)
                            .executeWithoutResult(
                                status -> {
                                  gate.lockShared();
                                  calendars.create(f.configurationId(), f.reader(), f.reader());
                                  subscribed.countDown();
                                  await(commit);
                                }));
            await(subscribed);
            Future<?> source = pool.submit(f.source());
            assertThrows(TimeoutException.class, () -> source.get(200, TimeUnit.MILLISECONDS));
            commit.countDown();
            subscription.get(15, TimeUnit.SECONDS);
            source.get(15, TimeUnit.SECONDS);
            assertEquals(0, linkCount(f));
          } finally {
            commit.countDown();
            pool.shutdownNow();
          }
        });
  }

  @ParameterizedTest
  @EnumSource(Path.class)
  void rollbackRetainsSourceAccessAndToken(Path path) throws Exception {
    withBookingEnabled(
        () -> {
          Fixture f = fixture(path);
          BookingCalendarManager.Created before =
              calendars.create(f.configurationId(), f.reader(), f.reader());
          BookingCalendarCurrentAccessDao actual =
              applicationContext.getBean(BookingCalendarCurrentAccessDao.class);
          BookingCalendarCurrentAccessDao failing =
              Mockito.mock(BookingCalendarCurrentAccessDao.class);
          Mockito.when(
                  failing.revokeDenied(
                      ArgumentMatchers.anySet(),
                      ArgumentMatchers.anySet(),
                      ArgumentMatchers.anyBoolean()))
              .thenAnswer(
                  invocation -> {
                    actual.revokeDenied(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2));
                    throw new RevocationFailure();
                  });
          BookingCalendarRevocationCoordinator coordinator =
              new BookingCalendarRevocationCoordinator(
                  gate, failing, applicationContext.getBean(SessionFactory.class));
          assertThrows(
              RevocationFailure.class,
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(
                          status -> {
                            coordinator.affectAll();
                            f.source().run();
                          }));
          assertEquals(1, linkCount(f));
          assertEquals(
              before.status().etag(),
              calendars.create(f.configurationId(), f.reader(), f.reader()).status().etag());
        });
  }

  @ParameterizedTest
  @EnumSource(
      value = Path.class,
      names = {"OWNER_TRANSFER", "DELETE"})
  void lifecycleSourceBeforeBookingDeniesCreation(Path path) throws Exception {
    withBookingEnabled(
        () -> {
          Fixture f = fixture(path);
          var instrument = doInTransaction(() -> instrumentDao.get(f.instrumentId()));
          var target =
              new ResolvedBookableTarget(
                  new BookableTargetReference(BookableTargetType.INSTRUMENT, f.instrumentId()),
                  instrument);
          Instant start = Instant.now().plus(7, ChronoUnit.DAYS);
          var command =
              new TimeSlotBookingManager.Create(
                  target,
                  Date.from(start),
                  Date.from(start.plus(1, ChronoUnit.HOURS)),
                  "Gate race");
          CountDownLatch changed = new CountDownLatch(1);
          CountDownLatch commit = new CountDownLatch(1);
          ExecutorService pool = Executors.newFixedThreadPool(2);
          try {
            Future<?> source =
                pool.submit(
                    () ->
                        new TransactionTemplate(transactions)
                            .executeWithoutResult(
                                status -> {
                                  f.source().run();
                                  changed.countDown();
                                  await(commit);
                                }));
            await(changed);
            Future<?> booking =
                pool.submit(
                    () ->
                        applicationContext
                            .getBean(TimeSlotBookingManager.class)
                            .createBooking(command, f.reader(), f.reader()));
            assertThrows(TimeoutException.class, () -> booking.get(200, TimeUnit.MILLISECONDS));
            commit.countDown();
            source.get(15, TimeUnit.SECONDS);
            ExecutionException denied =
                assertThrows(ExecutionException.class, () -> booking.get(15, TimeUnit.SECONDS));
            assertTrue(
                denied.getCause() instanceof AuthorizationException
                    || denied.getCause() instanceof BookingTargetUnavailableException,
                () -> denied.getCause().toString());
            assertEquals(
                0,
                jdbc.queryForObject(
                    "select count(*) from TimeSlotBooking where bookingConfiguration_id = ?",
                    Integer.class,
                    f.configurationId()));
          } finally {
            commit.countDown();
            pool.shutdownNow();
          }
        });
  }

  @ParameterizedTest
  @EnumSource(
      value = Path.class,
      names = {"SHARING", "OWNER_TRANSFER"})
  void contentPatchCannotReplayOldPermissionFields(Path path) throws Exception {
    withBookingEnabled(
        () -> {
          Fixture f = fixture(path);
          calendars.create(f.configurationId(), f.reader(), f.reader());
          CountDownLatch changed = new CountDownLatch(1);
          CountDownLatch commit = new CountDownLatch(1);
          CountDownLatch contentStarted = new CountDownLatch(1);
          ExecutorService pool = Executors.newFixedThreadPool(2);
          try {
            Future<?> source =
                pool.submit(
                    () ->
                        new TransactionTemplate(transactions)
                            .executeWithoutResult(
                                status -> {
                                  f.source().run();
                                  changed.countDown();
                                  await(commit);
                                }));
            await(changed);
            Future<ApiInstrument> content =
                pool.submit(
                    () -> {
                      ApiInstrument patch = new ApiInstrument();
                      patch.setId(f.instrumentId());
                      patch.setName("Concurrent content edit");
                      contentStarted.countDown();
                      return instrumentApiMgr.updateApiInstrument(patch, f.owner());
                    });
            await(contentStarted);
            assertThrows(TimeoutException.class, () -> content.get(200, TimeUnit.MILLISECONDS));
            commit.countDown();
            source.get(15, TimeUnit.SECONDS);
            if (path == Path.SHARING) {
              ApiInstrument updated = content.get(15, TimeUnit.SECONDS);
              assertEquals("Concurrent content edit", updated.getName());
              assertEquals(ApiInventorySharingMode.OWNER_ONLY, updated.getSharingMode());
            } else {
              ExecutionException denied =
                  assertThrows(ExecutionException.class, () -> content.get(15, TimeUnit.SECONDS));
              assertInstanceOf(NotFoundException.class, denied.getCause());
              assertNotEquals(
                  f.owner().getId(),
                  jdbc.queryForObject(
                      "select owner_id from InstrumentEntity where id = ?",
                      Long.class,
                      f.instrumentId()));
            }
            assertEquals(0, linkCount(f));
          } finally {
            commit.countDown();
            pool.shutdownNow();
          }
        });
  }

  @Test
  void globalRoleLossRetainsTheSameSubscribersSharedGroupToken() throws Exception {
    withBookingEnabled(
        () -> {
          Fixture f = fixture(Path.GLOBAL_ROLE);
          ApiInstrument shared = createBasicInstrumentForUser(f.owner(), "Alternate group access");
          Long sharedConfiguration = bookingConfigurationFor(shared, f.owner()).getId();
          BookingCalendarManager.Created retained =
              calendars.create(sharedConfiguration, f.reader(), f.reader());
          calendars.create(f.configurationId(), f.reader(), f.reader());
          User current = userMgr.get(f.reader().getId());
          current.removeRole(roleMgr.getRole(Constants.SYSADMIN_ROLE));
          new TransactionTemplate(transactions)
              .executeWithoutResult(status -> userMgr.save(current));
          assertEquals(0, linkCount(f));
          assertEquals(
              retained.status().etag().replace("\"", ""),
              jdbc.queryForObject(
                  "select tokenHash from BookableItemCalendarSubscription where"
                      + " bookingConfiguration_id = ? and user_id = ?",
                  String.class,
                  sharedConfiguration,
                  f.reader().getId()));
        });
  }

  private static class RevocationFailure extends RuntimeException {}

  private int linkCount(Fixture f) {
    assertEquals(
        1,
        jdbc.queryForObject(
            "select count(*) from BookableItemCalendarSubscription where bookingConfiguration_id ="
                + " ? and user_id = ?",
            Integer.class,
            f.survivorId(),
            f.owner().getId()));
    return jdbc.queryForObject(
        "select count(*) from BookableItemCalendarSubscription where bookingConfiguration_id = ?"
            + " and user_id = ?",
        Integer.class,
        f.configurationId(),
        f.reader().getId());
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(15, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
