package com.researchspace.maintenance.service;

import static com.researchspace.maintenance.service.MaintenanceTestFixtures.maintenance;
import static com.researchspace.maintenance.service.MaintenanceTestFixtures.pagedRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.testutils.SpringTransactionalTest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Unit tests covering scheduled maintenance. */
public class MaintenanceManagerTest extends SpringTransactionalTest {

  @Autowired private MaintenanceManager maintenanceManager;

  private User sysUser;
  private User regularUser;

  private static final String TEST_MSG = "test maintenance message";

  private static Date dateNow;
  private static Date dateLastHour;
  private static Date dateNextHour;
  private static Date dateNextDay;

  @BeforeAll
  public static void initDates() {
    Instant now = Instant.now();
    dateNow = Date.from(now);
    dateLastHour = Date.from(now.minus(Duration.ofHours(1)));
    dateNextHour = Date.from(now.plus(Duration.ofHours(1)));
    dateNextDay = Date.from(now.atZone(ZoneId.systemDefault()).plusDays(1).toInstant());
  }

  @BeforeEach
  public void setUp() {
    initSysadminUser();
  }

  private void initSysadminUser() {
    sysUser = createAndSaveUserIfNotExists("testMaintenanceSysadmin", Constants.SYSADMIN_ROLE);
    regularUser = createAndSaveUserIfNotExists("testMaintenanceUser");
  }

  @Test
  public void testSaveRetrieveScheduledMaintenance() {

    ScheduledMaintenance newMaintenance = new ScheduledMaintenance(dateNow, dateNextHour);
    newMaintenance.setMessage(TEST_MSG);

    ScheduledMaintenance saved =
        maintenanceManager.saveScheduledMaintenance(newMaintenance, sysUser);
    assertNotNull(saved);

    ScheduledMaintenance retrieved = maintenanceManager.getScheduledMaintenance(saved.getId());
    assertNotNull(retrieved);
    assertEquals(
        TEST_MSG, retrieved.getMessage(), "saved and retrieved message should be the same");

    // cleanup
    maintenanceManager.removeScheduledMaintenance(saved.getId(), sysUser);
  }

  @Test
  public void onlySysadminCanSaveScheduledMaintenance() {
    ScheduledMaintenance maintenance = new ScheduledMaintenance(null, null);
    assertThrows(
        AuthorizationException.class,
        () -> maintenanceManager.saveScheduledMaintenance(maintenance, regularUser));
  }

  @Test
  public void testRetrievingNextScheduledMaintenance() {

    ScheduledMaintenance nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertEquals(ScheduledMaintenance.NULL, nextMaintenance);

    ScheduledMaintenance firstMaintenance = new ScheduledMaintenance(dateNextHour, dateNextDay);
    ScheduledMaintenance firstSavedMaintenance =
        maintenanceManager.saveScheduledMaintenance(firstMaintenance, sysUser);

    nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertNotNull(nextMaintenance);
    assertEquals(
        firstSavedMaintenance, nextMaintenance, "next maintenance should be the saved one");

    ScheduledMaintenance secondMaintenance = new ScheduledMaintenance(dateLastHour, dateNextHour);
    ScheduledMaintenance secondSavedMaintenance =
        maintenanceManager.saveScheduledMaintenance(secondMaintenance, sysUser);

    nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertNotNull(nextMaintenance);
    assertEquals(
        secondSavedMaintenance,
        nextMaintenance,
        "next maintenance should be closest future maintenance");

    // cleanup
    maintenanceManager.removeScheduledMaintenance(firstMaintenance.getId(), sysUser);
    maintenanceManager.removeScheduledMaintenance(secondMaintenance.getId(), sysUser);
  }

  @Test
  public void executesEveryRsqlOperatorAgainstPersistedMaintenances() {
    ScheduledMaintenance first = saveMaintenance(2, 8, "apiV2 Alpha 50%_done!");
    ScheduledMaintenance second = saveMaintenance(3, 8, "apiV2 database upgrade");
    ScheduledMaintenance third = saveMaintenance(4, 8, null);
    ScheduledMaintenance fourth = saveMaintenance(5, 8, "apiV2 database upgrade");
    String ids =
        String.join(
            ",",
            List.of(first, second, third, fourth).stream()
                .map(maintenance -> maintenance.getId().toString())
                .toList());

    assertIds(List.of(first), find("id==%s".formatted(first.getId())));
    assertIds(
        List.of(second, third, fourth), find("id=in=(%s);id!=%s".formatted(ids, first.getId())));
    assertIds(List.of(second, third), find("id>%s;id<%s".formatted(first.getId(), fourth.getId())));
    assertIds(
        List.of(first, second, third),
        find("id>=%s;id<=%s".formatted(first.getId(), third.getId())));
    assertIds(List.of(first, third), find("id=in=(%s,%s)".formatted(first.getId(), third.getId())));
    assertIds(
        List.of(second, fourth),
        find("id=in=(%s);id=out=(%s,%s)".formatted(ids, first.getId(), third.getId())));
    assertIds(List.of(first), find("message=contains='50%_done!'"));
    assertIds(
        List.of(second, fourth), find("id=in=(%s);message=like='database upgrade'".formatted(ids)));
    assertIds(List.of(third), find("id=in=(%s);message=exists=false".formatted(ids)));
    assertIds(
        List.of(first, second, fourth),
        find(
            "id==%s,(id=in=(%s,%s);message=like='database upgrade')"
                .formatted(first.getId(), second.getId(), fourth.getId())));

    ResourceRequest sorted =
        pagedRequest(
            "id=in=(%s,%s)".formatted(second.getId(), fourth.getId()),
            List.of(new Sort("message", true), new Sort("id", false)),
            1,
            1);
    ResourcePage<ScheduledMaintenance> firstPage = maintenanceManager.getResources(sorted, sysUser);
    assertEquals(2L, firstPage.total());
    assertEquals(fourth.getId(), firstPage.resources().get(0).getId());
    assertEquals(2L, maintenanceManager.countResources(sorted, sysUser));
  }

  @Test
  public void readsAndBulkWritesBothSeeExpiredRows() {
    ScheduledMaintenance expired = saveMaintenance(-4, -3, "apiV2 expired");
    ScheduledMaintenance future = saveMaintenance(2, 3, "apiV2 future");
    ResourceRequest matchingBoth =
        pagedRequest(
            "id=in=(%s,%s)".formatted(expired.getId(), future.getId()),
            ApiV2MaintenanceResource.DESCRIPTION.defaultSort(),
            1,
            20);

    ResourcePage<ScheduledMaintenance> results =
        maintenanceManager.getResources(matchingBoth, sysUser);
    assertEquals(2L, results.total());
    assertEquals(expired.getId(), results.resources().get(0).getId());
    assertEquals(2L, maintenanceManager.countResources(matchingBoth, sysUser));
    assertTrue(maintenanceManager.getResource(expired.getId(), sysUser).isPresent());

    List<ScheduledMaintenance> updated =
        maintenanceManager.updateResources(
            matchingBoth, ParsedDocument.update(Map.of("message", "apiV2 bulk updated")), sysUser);
    assertEquals(2, updated.size());
    assertEquals(
        "apiV2 bulk updated",
        maintenanceManager.getScheduledMaintenance(expired.getId()).getMessage());
  }

  @Test
  public void nextScheduledMaintenanceStillIgnoresExpiredRows() {
    saveMaintenance(-4, -3, "apiV2 expired");

    assertEquals(ScheduledMaintenance.NULL, maintenanceManager.getNextScheduledMaintenance());
  }

  @Test
  public void testRemovingScheduledMaintenance() {

    ScheduledMaintenance newMaintenance = new ScheduledMaintenance(dateNow, dateNextDay);
    maintenanceManager.saveScheduledMaintenance(newMaintenance, sysUser);

    ScheduledMaintenance nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertNotNull(nextMaintenance);

    maintenanceManager.removeScheduledMaintenance(newMaintenance.getId(), sysUser);

    nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertEquals(ScheduledMaintenance.NULL, nextMaintenance);
  }

  @Test
  public void onlySysadminCanRemoveScheduledMaintenance() {
    assertThrows(
        AuthorizationException.class,
        () -> maintenanceManager.removeScheduledMaintenance(1L, regularUser));
  }

  private ScheduledMaintenance saveMaintenance(int startHours, int endHours, String message) {
    return maintenanceManager.saveScheduledMaintenance(
        maintenance(startHours, endHours, message), sysUser);
  }

  private ResourcePage<ScheduledMaintenance> find(String where) {
    return maintenanceManager.getResources(
        pagedRequest(where, List.of(new Sort("id", true)), 1, 20), sysUser);
  }

  private static void assertIds(
      List<ScheduledMaintenance> expected, ResourcePage<ScheduledMaintenance> actual) {
    assertEquals(
        expected.stream().map(ScheduledMaintenance::getId).toList(),
        actual.resources().stream().map(ScheduledMaintenance::getId).toList());
  }
}
