package com.researchspace.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentSearchResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.inventory.impl.InstrumentEntityApiManagerImpl;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

public class InstrumentEntityApiManagerIT extends RealTransactionSpringTestBase {

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void checkBasicInstrumentCreateRetrieveAndExists() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument created = createBasicInstrumentForUser(testUser, "it-instrument");
    assertNotNull(created.getId());
    assertEquals("it-instrument", created.getName());
    assertFalse(created.isTemplate());

    assertTrue(instrumentApiMgr.instrumentExists(created.getId()));

    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    assertEquals(created.getId(), retrieved.getId());
    assertEquals("it-instrument", retrieved.getName());
  }

  @Test
  public void checkDefaultInstrumentNameApplied() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument request = new ApiInstrument();
    request.setName(" ");

    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(request, testUser);
    assertEquals(InstrumentEntityApiManagerImpl.INSTRUMENT_DEFAULT_NAME, created.getName());
  }

  @Test
  public void checkInstrumentPermissionAssertions() throws Exception {
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    ApiInstrument created = createBasicInstrumentForUser(owner, "permissions-it");

    assertNotNull(instrumentApiMgr.assertUserCanReadInstrument(created.getId(), owner));
    assertNotNull(instrumentApiMgr.assertUserCanEditInstrument(created.getId(), owner));

    User otherUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(otherUser);
    Long instrumentId = created.getId();
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanEditInstrument(instrumentId, otherUser));
  }

  @Test
  public void checkInventoryEntityFieldPermissionRoutingForSample() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithInitialisedContent(testUser);

    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(testUser);
    Long sampleFieldId = complexSample.getFields().get(0).getId();

    InventoryRecord readResult =
        instrumentApiMgr.assertUserCanReadInventoryEntityField(sampleFieldId, testUser);
    assertEquals(complexSample.getId(), readResult.getId());

    InventoryRecord editResult =
        instrumentApiMgr.assertUserCanEditInventoryEntityField(sampleFieldId, testUser);
    assertEquals(complexSample.getId(), editResult.getId());
  }

  @Test
  public void checkTemplateMethodsForMissingTemplate() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    assertFalse(instrumentApiMgr.instrumentTemplateExists(Long.MAX_VALUE));
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.getApiInstrumentTemplateById(Long.MAX_VALUE, testUser));
  }

  @Test
  public void updateApiInstrument_persistsChanges() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument created = createBasicInstrumentForUser(testUser, "before-update");

    created.setName("after-update");
    created.setDescription("a new description");
    instrumentApiMgr.updateApiInstrument(created, testUser);

    ApiInstrument retrieved = instrumentApiMgr.getApiInstrumentById(created.getId(), testUser);
    assertEquals("after-update", retrieved.getName());
    assertEquals("a new description", retrieved.getDescription());
  }

  @Test
  public void markInstrumentAsDeleted_andRestore() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument created = createBasicInstrumentForUser(testUser, "soft-delete-it");
    assertFalse(created.isDeleted());

    ApiInstrument deleted = instrumentApiMgr.markInstrumentAsDeleted(created.getId(), testUser);
    assertTrue(deleted.isDeleted());

    // deleted instrument still appears when including deleted records
    ApiInstrumentSearchResult withDeleted =
        instrumentApiMgr.getInstrumentsForUser(
            PaginationCriteria.createDefaultForClass(Instrument.class),
            null,
            InventorySearchDeletedOption.DELETED_ONLY,
            testUser);
    assertThat(withDeleted.getInstruments()).anyMatch(i -> i.getId().equals(created.getId()));

    ApiInstrument restored = instrumentApiMgr.restoreDeletedInstrument(created.getId(), testUser);
    assertFalse(restored.isDeleted());
  }

  @Test
  public void duplicateInstrument_createsPersistentCopy() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);

    ApiInstrument original = createBasicInstrumentForUser(testUser, "to-be-duplicated");

    ApiInstrument copy = instrumentApiMgr.duplicateInstrument(original.getId(), testUser);

    assertNotNull(copy.getId());
    assertFalse(copy.getId().equals(original.getId()));
    assertEquals(original.getName() + "_COPY", copy.getName());
    assertTrue(instrumentApiMgr.instrumentExists(copy.getId()));
  }

  @Test
  public void getInstrumentsForUser_returnsOwnedInstruments() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);
    createBasicInstrumentForUser(testUser, "listed-it-instrument");

    ApiInstrumentSearchResult result =
        instrumentApiMgr.getInstrumentsForUser(
            PaginationCriteria.createDefaultForClass(Instrument.class),
            null,
            InventorySearchDeletedOption.EXCLUDE,
            testUser);

    assertTrue(result.getTotalHits() >= 1);
    assertThat(result.getInstruments()).anyMatch(i -> i.getName().equals("listed-it-instrument"));
  }

  @Test
  public void nameExistsForUser_detectsExistingName() throws Exception {
    User testUser = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(testUser);
    String name = "unique-it-" + CoreTestUtils.getRandomName(6);

    assertFalse(instrumentApiMgr.nameExistsForUser(name, testUser));

    createBasicInstrumentForUser(testUser, name);

    assertTrue(instrumentApiMgr.nameExistsForUser(name, testUser));
  }

  @Test
  public void changeApiInstrumentOwner_transfersOwnership() throws Exception {
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    User newOwner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(newOwner);

    ApiInstrument created = createBasicInstrumentForUser(owner, "to-be-transferred");
    assertEquals(owner.getUsername(), created.getOwner().getUsername());

    created.setOwner(new ApiUser(newOwner));
    ApiInstrument transferred = instrumentApiMgr.changeApiInstrumentOwner(created, owner);

    assertEquals(newOwner.getUsername(), transferred.getOwner().getUsername());

    // new owner can read it; original owner no longer has access
    assertNotNull(instrumentApiMgr.assertUserCanReadInstrument(transferred.getId(), newOwner));
    Long transferredId = transferred.getId();
    assertThrows(
        Exception.class, () -> instrumentApiMgr.assertUserCanEditInstrument(transferredId, owner));
  }

  @Test
  public void changeApiInstrumentOwner_piOfOwnerGroupTransfersUnderLockedAuthority()
      throws Exception {
    setUpUserWithoutCustomContent(piUser);
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    User newOwner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(newOwner);
    createGroupForPiAndUsers(piUser, new User[] {piUser, owner});
    ApiInstrument created = createBasicInstrumentForUser(owner, "pi-transfer");
    created.setOwner(new ApiUser(newOwner));

    ApiInstrument transferred =
        doInTransaction(
            () ->
                instrumentApiMgr.changeApiInstrumentOwner(
                    created, userMgr.getUserByUsername(piUser.getUsername())));

    assertEquals(newOwner.getUsername(), transferred.getOwner().getUsername());
  }

  @Test
  public void changeApiInstrumentOwner_keepsAnEditCommittedAfterTheTransferReadTheInstrument()
      throws Exception {
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    User newOwner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(newOwner);
    ApiInstrument created = createBasicInstrumentForUser(owner, "before-rename");
    CountDownLatch read = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(1);
    try {
      Future<?> transfer =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          tx -> {
                            instrumentApiMgr.getApiInstrumentById(created.getId(), owner);
                            read.countDown();
                            awaitOrFail(release);
                            ApiInstrument toNewOwner = new ApiInstrument();
                            toNewOwner.setId(created.getId());
                            toNewOwner.setOwner(new ApiUser(newOwner));
                            instrumentApiMgr.changeApiInstrumentOwner(toNewOwner, owner);
                          }));
      assertTrue(read.await(30, TimeUnit.SECONDS));
      ApiInstrument rename = new ApiInstrument();
      rename.setId(created.getId());
      rename.setName("after-rename");
      doInTransaction(() -> instrumentApiMgr.updateApiInstrument(rename, owner));
      release.countDown();
      transfer.get(60, TimeUnit.SECONDS);

      ApiInstrument after =
          doInTransaction(() -> instrumentApiMgr.getApiInstrumentById(created.getId(), newOwner));
      assertEquals(newOwner.getUsername(), after.getOwner().getUsername());
      assertEquals("after-rename", after.getName());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  public void changeApiInstrumentOwner_refusesAPiWhoseAuthorityEndedAfterTheTransferBegan()
      throws Exception {
    setUpUserWithoutCustomContent(piUser);
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    User newOwner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(newOwner);
    Group group = createGroupForPiAndUsers(piUser, new User[] {piUser, owner});
    ApiInstrument created = createBasicInstrumentForUser(owner, "pi-loses-authority");
    CountDownLatch snapshotTaken = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(1);
    try {
      Future<?> transfer =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          tx -> {
                            User pi = userMgr.getUserByUsername(piUser.getUsername());
                            instrumentApiMgr.assertUserCanTransferInstrument(created.getId(), pi);
                            snapshotTaken.countDown();
                            awaitOrFail(release);
                            ApiInstrument toNewOwner = new ApiInstrument();
                            toNewOwner.setId(created.getId());
                            toNewOwner.setOwner(new ApiUser(newOwner));
                            instrumentApiMgr.changeApiInstrumentOwner(toNewOwner, pi);
                          }));
      assertTrue(snapshotTaken.await(30, TimeUnit.SECONDS));
      // committed after the transfer's snapshot: only a locking read of the membership sees it
      logoutAndLoginAs(piUser);
      doInTransaction(() -> grpMgr.removeUserFromGroup(owner.getUsername(), group.getId(), piUser));
      release.countDown();

      ExecutionException refused =
          assertThrows(ExecutionException.class, () -> transfer.get(60, TimeUnit.SECONDS));
      assertTrue(
          refused.getCause() instanceof IllegalArgumentException,
          () -> "unexpected refusal: " + refused.getCause());
      assertEquals(
          owner.getUsername(),
          doInTransaction(() -> instrumentApiMgr.getApiInstrumentById(created.getId(), owner))
              .getOwner()
              .getUsername());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  /** Fails the race rather than letting it run in the wrong order when the other side is late. */
  private static void awaitOrFail(CountDownLatch latch) {
    try {
      if (!latch.await(30, TimeUnit.SECONDS)) {
        throw new AssertionError("timed out waiting for the other transaction");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted waiting for the other transaction", e);
    }
  }

  @Test
  public void assertUserCanDeleteInstrument_ownerCanDeleteOtherUserCannot() throws Exception {
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    User other = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(other);

    ApiInstrument created = createBasicInstrumentForUser(owner, "perm-delete-it");

    assertNotNull(instrumentApiMgr.assertUserCanDeleteInstrument(created.getId(), owner));
    Long instrumentId = created.getId();
    assertThrows(
        Exception.class, () -> instrumentApiMgr.assertUserCanDeleteInstrument(instrumentId, other));
  }

  @Test
  public void assertUserCanTransferInstrument_ownerCanTransferOtherUserCannot() throws Exception {
    User owner = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(owner);
    User other = createAndSaveUser(CoreTestUtils.getRandomName(10));
    setUpUserWithoutCustomContent(other);

    ApiInstrument created = createBasicInstrumentForUser(owner, "perm-transfer-it");

    assertNotNull(instrumentApiMgr.assertUserCanTransferInstrument(created.getId(), owner));
    Long instrumentId = created.getId();
    assertThrows(
        Exception.class,
        () -> instrumentApiMgr.assertUserCanTransferInstrument(instrumentId, other));
  }
}
