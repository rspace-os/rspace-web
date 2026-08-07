package com.researchspace.maintenance.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.ResourceRequest.Page;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
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

  @BeforeClass
  public static void initDates() {
    Calendar cal = Calendar.getInstance();
    dateNow = cal.getTime();

    cal.add(Calendar.HOUR_OF_DAY, -1);
    dateLastHour = cal.getTime();

    cal.add(Calendar.HOUR_OF_DAY, 2);
    dateNextHour = cal.getTime();

    cal.add(Calendar.HOUR_OF_DAY, -1);
    cal.add(Calendar.DAY_OF_MONTH, 1);
    dateNextDay = cal.getTime();
  }

  @Before
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
        "saved and retrieved message should be the same", TEST_MSG, retrieved.getMessage());

    // cleanup
    maintenanceManager.removeScheduledMaintenance(saved.getId(), sysUser);
  }

  @Test(expected = AuthorizationException.class)
  public void onlySysadminCanSaveScheduledMaintenance() {
    maintenanceManager.saveScheduledMaintenance(new ScheduledMaintenance(null, null), regularUser);
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
        "next maintenance should be the saved one", firstSavedMaintenance, nextMaintenance);

    ScheduledMaintenance secondMaintenance = new ScheduledMaintenance(dateLastHour, dateNextHour);
    ScheduledMaintenance secondSavedMaintenance =
        maintenanceManager.saveScheduledMaintenance(secondMaintenance, sysUser);

    nextMaintenance = maintenanceManager.getNextScheduledMaintenance();
    assertNotNull(nextMaintenance);
    assertEquals(
        "next maintenance should be closest future maintenance",
        secondSavedMaintenance,
        nextMaintenance);

    // cleanup
    maintenanceManager.removeScheduledMaintenance(firstMaintenance.getId(), sysUser);
    maintenanceManager.removeScheduledMaintenance(secondMaintenance.getId(), sysUser);
  }

  @Test
  public void filtersSortsCountsAndFindsFutureMaintenances() {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.DAY_OF_MONTH, 2);
    Date start = calendar.getTime();
    calendar.add(Calendar.HOUR_OF_DAY, 1);
    Date end = calendar.getTime();

    ScheduledMaintenance matching = new ScheduledMaintenance(start, end);
    matching.setMessage("apiV2Match");
    maintenanceManager.saveScheduledMaintenance(matching, sysUser);
    ScheduledMaintenance other = new ScheduledMaintenance(start, end);
    other.setMessage("apiV2Other");
    maintenanceManager.saveScheduledMaintenance(other, sysUser);

    ResourcePage<ScheduledMaintenance> results =
        maintenanceManager.getResources(
            new ResourceRequest(
                new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION)
                    .parse("message==apiV2Match"),
                List.of(new Sort("startDate", false), new Sort("id", true)),
                new Page(1, 20),
                FieldSelection.all(),
                IncludeTree.empty()));

    assertEquals(1L, results.total());
    assertEquals(matching.getId(), results.resources().get(0).getId());
    assertEquals(
        1L,
        maintenanceManager.countResources(
            ResourceRequest.unpaged(
                new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION)
                    .parse("message==apiV2Match"))));
    assertTrue(maintenanceManager.getResource(matching.getId()).isPresent());
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

    assertIds(List.of(first), find("id==" + first.getId()));
    assertIds(List.of(second, third, fourth), find("id=in=(" + ids + ");id!=" + first.getId()));
    assertIds(List.of(second, third), find("id>" + first.getId() + ";id<" + fourth.getId()));
    assertIds(
        List.of(first, second, third), find("id>=" + first.getId() + ";id<=" + third.getId()));
    assertIds(List.of(first, third), find("id=in=(" + first.getId() + "," + third.getId() + ")"));
    assertIds(
        List.of(second, fourth),
        find("id=in=(" + ids + ");id=out=(" + first.getId() + "," + third.getId() + ")"));
    assertIds(List.of(first), find("message=contains='50%_done!'"));
    assertIds(List.of(second, fourth), find("id=in=(" + ids + ");message=like='database upgrade'"));
    assertIds(List.of(third), find("id=in=(" + ids + ");message=exists=false"));
    assertIds(
        List.of(first, second, fourth),
        find(
            "id=="
                + first.getId()
                + ",(id=in=("
                + second.getId()
                + ","
                + fourth.getId()
                + ");message=like='database upgrade')"));

    ResourceRequest sorted =
        request(
            "id=in=(" + second.getId() + "," + fourth.getId() + ")",
            List.of(new Sort("message", true), new Sort("id", false)),
            1,
            1);
    ResourcePage<ScheduledMaintenance> firstPage = maintenanceManager.getResources(sorted);
    assertEquals(2L, firstPage.total());
    assertEquals(fourth.getId(), firstPage.resources().get(0).getId());
    assertEquals(2L, maintenanceManager.countResources(sorted));
  }

  @Test
  public void readsAndBulkWritesBothSeeExpiredRows() {
    ScheduledMaintenance expired = saveMaintenance(-4, -3, "apiV2 expired");
    ScheduledMaintenance future = saveMaintenance(2, 3, "apiV2 future");
    ResourceRequest matchingBoth =
        request(
            "id=in=(" + expired.getId() + "," + future.getId() + ")",
            ApiV2MaintenanceResource.DESCRIPTION.defaultSort(),
            1,
            20);

    ResourcePage<ScheduledMaintenance> results = maintenanceManager.getResources(matchingBoth);
    assertEquals(2L, results.total());
    assertEquals(expired.getId(), results.resources().get(0).getId());
    assertEquals(2L, maintenanceManager.countResources(matchingBoth));
    assertTrue(maintenanceManager.getResource(expired.getId()).isPresent());

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

  @Test(expected = AuthorizationException.class)
  public void onlySysadminCanRemoveScheduledMaintenance() {
    maintenanceManager.removeScheduledMaintenance(1L, regularUser);
  }

  private ScheduledMaintenance saveMaintenance(int startHours, int endHours, String message) {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.HOUR_OF_DAY, startHours);
    Date start = calendar.getTime();
    calendar.add(Calendar.HOUR_OF_DAY, endHours - startHours);
    ScheduledMaintenance maintenance = new ScheduledMaintenance(start, calendar.getTime());
    maintenance.setMessage(message);
    return maintenanceManager.saveScheduledMaintenance(maintenance, sysUser);
  }

  private ResourcePage<ScheduledMaintenance> find(String where) {
    return maintenanceManager.getResources(request(where, List.of(new Sort("id", true)), 1, 20));
  }

  private static ResourceRequest request(String where, List<Sort> sort, int page, int pageSize) {
    return new ResourceRequest(
        new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION).parse(where),
        sort,
        new Page(page, pageSize),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private static void assertIds(
      List<ScheduledMaintenance> expected, ResourcePage<ScheduledMaintenance> actual) {
    assertEquals(
        expected.stream().map(ScheduledMaintenance::getId).toList(),
        actual.resources().stream().map(ScheduledMaintenance::getId).toList());
  }
}
