package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.inventory.model.ApiV2ContainerResource;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessContext.Operation;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.service.inventory.InstrumentReadAccess;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Covers the REST API v2 collection query for instruments, which only a real database can check:
 * the nested {@code editInfo.name} filter property, the single-table discriminator that keeps
 * instrument templates out of the collection, and the inventory permission restriction.
 */
public class InstrumentDaoCollectionTest extends SpringTransactionalTest {

  private final InstrumentDao instrumentDao;
  private final InstrumentReadAccess instrumentReadAccess;

  @Autowired
  InstrumentDaoCollectionTest(
      InstrumentDao instrumentDao, InstrumentReadAccess instrumentReadAccess) {
    this.instrumentDao = instrumentDao;
    this.instrumentReadAccess = instrumentReadAccess;
  }

  private AccessResult readAccess(User user) {
    return instrumentReadAccess.check(new AccessContext(user, Operation.READ, "instruments"));
  }

  private static ResourceRequest page(FilterExpression filter, int number, int size) {
    return new ResourceRequest(
        filter,
        List.of(new Sort("name", true), new Sort("id", true)),
        new ResourceRequest.Page(number, size),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private static FilterExpression nameContains(String term) {
    return new FilterExpression.Comparison("name", Operator.CONTAINS, List.of(term), false);
  }

  private static FilterExpression notDeleted() {
    return new FilterExpression.Comparison("deleted", Operator.EQUAL, List.of(false), false);
  }

  private static List<String> names(List<Instrument> instruments) {
    return instruments.stream().map(Instrument::getName).toList();
  }

  @Autowired private CriteriaBuilderFactory queryFactory;

  @Test
  public void boundedCountReadsOnlyTheRequestedPrefixWithoutLoadingEntities() {
    User owner = createInitAndLoginAnyUser();
    for (int i = 0; i < 5; i++) createBasicInstrumentForUser(owner, "Bounded SQL scope " + i);
    var session = sessionFactory.getCurrentSession();
    session.flush();
    var executor =
        new CollectionQueryExecutor<>(Instrument.class, ApiV2InstrumentResource.DESCRIPTION, "i");
    var statistics = sessionFactory.getStatistics();
    boolean enabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    try {
      statistics.clear();
      assertEquals(
          3,
          executor.count(
              queryFactory,
              session,
              page(nameContains("Bounded SQL scope"), 1, 20).withCountMax(2)));
      assertEquals(1, statistics.getPrepareStatementCount());
      assertEquals(0, statistics.getEntityLoadCount());
    } finally {
      statistics.setStatisticsEnabled(enabled);
    }
  }

  @Test
  public void countsWithoutSelectingRowsAndDropsFetchPathsOnOverflow() {
    User owner = createInitAndLoginAnyUser();
    createBasicInstrumentForUser(owner, "Count only scope");
    var session = sessionFactory.getCurrentSession();
    session.flush();
    var executor =
        new CollectionQueryExecutor<>(Instrument.class, ApiV2InstrumentResource.DESCRIPTION, "i");
    var request = page(nameContains("Count only scope"), 1, 20);
    var statistics = sessionFactory.getStatistics();
    boolean enabled = statistics.isStatisticsEnabled();
    statistics.setStatisticsEnabled(true);
    try {
      statistics.clear();
      assertEquals(1, executor.count(queryFactory, session, request));
      assertEquals(1, statistics.getPrepareStatementCount());
      statistics.clear();
      var overflow =
          executor.page(
              queryFactory,
              session,
              page(nameContains("Count only scope"), Integer.MAX_VALUE, 2),
              null,
              RelationshipReadAccess.none(),
              List.of("owner"));
      assertEquals(1, overflow.total());
      assertTrue(overflow.resources().isEmpty());
      assertEquals(1, statistics.getPrepareStatementCount());
    } finally {
      statistics.setStatisticsEnabled(enabled);
    }
  }

  @Test
  public void unfilteredQueryCounts() {
    createInitAndLoginAnyUser();
    var executor = new CollectionQueryExecutor<>(User.class, ApiV2UserResource.DESCRIPTION, "u");
    var session = sessionFactory.getCurrentSession();
    session.flush();
    long expected =
        session.createQuery("select count(u) from User u", Long.class).getSingleResult();
    assertEquals(expected, executor.count(queryFactory, session, page(null, 1, 20)));
  }

  @Test
  public void countsCorrelatedParentNameFilters() {
    User owner = createInitAndLoginAnyUser();
    var instrument =
        instrumentDao.get(createBasicInstrumentForUser(owner, "Related scope").getId());
    var registry =
        new ResourceRegistry(
            List.of(ApiV2InstrumentResource.DESCRIPTION, ApiV2ContainerResource.DESCRIPTION));
    var targets = RelationshipReadAccess.unrestricted(registry);
    var executor =
        new CollectionQueryExecutor<>(Instrument.class, ApiV2InstrumentResource.DESCRIPTION, "i");
    var request =
        page(
            new FilterExpression.Comparison(
                "parentContainer.name",
                Operator.EQUAL,
                List.of(instrument.getParentContainer().getName()),
                false),
            1,
            20);
    var session = sessionFactory.getCurrentSession();
    assertEquals(1, executor.page(queryFactory, session, request, null, targets).total());
    assertEquals(1, executor.count(queryFactory, session, request, null, targets));
  }

  @Test
  public void filtersByNameThroughTheNestedEditInfoProperty() {
    User owner = createInitAndLoginAnyUser();
    createBasicInstrumentForUser(owner, "Confocal microscope");
    createBasicInstrumentForUser(owner, "Centrifuge");

    ResourceRequest request = page(nameContains("confocal"), 1, 20);

    assertEquals(
        List.of("Confocal microscope"),
        names(instrumentDao.getReadableResources(request, readAccess(owner)).resources()));
    assertEquals(1, instrumentDao.countReadableResources(request, readAccess(owner)));
  }

  @Test
  public void excludesInstrumentTemplates() {
    User owner = createInitAndLoginAnyUser();
    createBasicInstrumentForUser(owner, "Shared name");
    createBasicInstrumentTemplateForUser(owner, "Shared name");

    List<Instrument> matches =
        instrumentDao
            .getReadableResources(page(nameContains("Shared name"), 1, 20), readAccess(owner))
            .resources();

    assertEquals(1, matches.size());
    assertTrue(matches.get(0).getGlobalIdentifier().startsWith("IN"));
  }

  @Test
  public void sortsAndPagesInTheDatabase() {
    User owner = createInitAndLoginAnyUser();
    createBasicInstrumentForUser(owner, "Beta scope");
    createBasicInstrumentForUser(owner, "Alpha scope");

    ResourceRequest first = page(nameContains("scope"), 1, 1);
    ResourceRequest second = page(nameContains("scope"), 2, 1);

    assertEquals(
        List.of("Alpha scope"),
        names(instrumentDao.getReadableResources(first, readAccess(owner)).resources()));
    assertEquals(2, instrumentDao.getReadableResources(first, readAccess(owner)).total());
    assertEquals(
        List.of("Beta scope"),
        names(instrumentDao.getReadableResources(second, readAccess(owner)).resources()));
  }

  @Test
  public void hidesAnInstrumentTheCallerMayNotRead() {
    User owner = createInitAndLoginAnyUser();
    createBasicInstrumentForUser(owner, "Private scope");
    User other = createInitAndLoginAnyUser();

    ResourceRequest request = page(nameContains("Private scope"), 1, 20);

    assertTrue(
        instrumentDao.getReadableResources(request, readAccess(other)).resources().isEmpty());
    assertEquals(0, instrumentDao.countReadableResources(request, readAccess(other)));
    assertEquals(1, instrumentDao.countReadableResources(request, readAccess(owner)));
  }

  @Test
  public void appliesTheSoftDeletionConstraintFromTheReadPolicy() {
    User owner = createInitAndLoginAnyUser();
    Instrument instrument =
        instrumentDao.get(createBasicInstrumentForUser(owner, "Trashed scope").getId());
    instrument.setRecordDeleted(true);
    instrumentDao.save(instrument);

    ResourceRequest request =
        page(new FilterExpression.And(List.of(nameContains("Trashed scope"), notDeleted())), 1, 20);

    assertTrue(
        instrumentDao.getReadableResources(request, readAccess(owner)).resources().isEmpty());
    assertEquals(0, instrumentDao.countReadableResources(request, readAccess(owner)));
  }

  @Test
  public void readsScalarSummariesThroughTheTypedProjection() {
    User owner = createInitAndLoginAnyUser();
    Instrument instrument =
        instrumentDao.get(createBasicInstrumentForUser(owner, "Summary scope").getId());

    InstrumentReadSummary summary =
        instrumentDao
            .getReadableSummaries(Set.of(instrument.getId()), owner)
            .get(instrument.getId());

    assertEquals("Summary scope", summary.name());
    assertFalse(summary.deleted());
  }

  @Test
  public void locksAndReadsCurrentGroupTransferAuthority() {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"));
    User ordinaryMember = createAndSaveUserIfNotExists(getRandomAlphabeticString("member"));
    initialiseContentWithEmptyContent(pi, owner, ordinaryMember);
    Group group = createGroup(getRandomAlphabeticString("transferGroup"), pi);
    addUsersToGroup(pi, group, owner, ordinaryMember);

    assertTrue(instrumentDao.hasLockedTransferAuthority(pi, owner));
    assertFalse(instrumentDao.hasLockedTransferAuthority(ordinaryMember, owner));
  }
}
