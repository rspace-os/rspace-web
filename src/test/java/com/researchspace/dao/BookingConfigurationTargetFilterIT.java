package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Covers filtering booking configurations by a field of their target instrument, which only a real
 * database can check: Blaze must accept the correlated subquery, and the target's own read rule
 * must narrow it.
 */
@ExtendWith(SpringExtension.class)
class BookingConfigurationTargetFilterIT extends SpringTransactionalTest {

  @Autowired private InstrumentDao instrumentDao;
  @Autowired private BookingConfigurationDao bookingConfigurationDao;
  @Autowired private CriteriaBuilderFactory criteriaBuilderFactory;

  private static final CollectionQueryExecutor<BookingConfiguration> CONFIGURATIONS =
      new CollectionQueryExecutor<>(
          BookingConfiguration.class,
          ApiV2BookingConfigurationResource.DESCRIPTION,
          "bookingConfiguration");

  private static RelationshipReadAccess targets(FilterExpression access) {
    return RelationshipReadAccess.forActor(registry(access), null);
  }

  private static ResourceRegistry registry(FilterExpression access) {
    AccessFunction readAccess =
        AccessFunction.documented(
            "Test relationship target access.",
            Set.of(),
            ignored -> access == null ? AccessResult.allowed() : AccessResult.allowedWhere(access));
    return new ResourceRegistry(
        List.of(
            ApiV2BookingConfigurationResource.DESCRIPTION,
            ApiV2UserResource.DESCRIPTION,
            ApiV2InstrumentResource.description(readAccess)));
  }

  private static ResourceRequest byTargetName(String term) {
    return new ResourceRequest(
        new FilterExpression.Comparison("target.name", Operator.CONTAINS, List.of(term), false),
        List.of(new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  private BookingConfiguration configurationFor(Long instrumentId) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId));
    sessionFactory.getCurrentSession().persist(configuration);
    sessionFactory.getCurrentSession().flush();
    return configuration;
  }

  private long count(ResourceRequest request, RelationshipReadAccess targets) {
    return CONFIGURATIONS.count(
        criteriaBuilderFactory, sessionFactory.getCurrentSession(), request, null, targets);
  }

  @Test
  void filtersByTheNameOfTheTargetInstrument() {
    User owner = createInitAndLoginAnyUser();
    configurationFor(createBasicInstrumentForUser(owner, "Confocal microscope").getId());
    configurationFor(createBasicInstrumentForUser(owner, "Centrifuge").getId());

    assertEquals(
        1,
        CONFIGURATIONS
            .page(
                criteriaBuilderFactory,
                sessionFactory.getCurrentSession(),
                byTargetName("confocal"),
                null,
                targets(null))
            .resources()
            .size());
    assertEquals(1, count(byTargetName("confocal"), targets(null)));
  }

  @Test
  void appliesTheReadRuleOfTheTargetInsideTheSubquery() {
    User owner = createInitAndLoginAnyUser();
    configurationFor(createBasicInstrumentForUser(owner, "Confocal microscope").getId());
    FilterExpression noInstrumentIsReadable =
        new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false);

    assertEquals(1, count(byTargetName("confocal"), targets(null)));
    assertEquals(0, count(byTargetName("confocal"), targets(noInstrumentIsReadable)));
  }

  @Test
  void targetAccessOnlyRestrictsQueriesThatObserveTheRelationship() {
    User owner = createInitAndLoginAnyUser();
    configurationFor(createBasicInstrumentForUser(owner, "Private confocal").getId());
    ResourceRequest targetFilter = byTargetName("Private confocal");
    ResourceRequest all = ResourceRequest.unpaged(null);
    RelationshipReadAccess readable = targets(null);
    RelationshipReadAccess unreadable =
        targets(new FilterExpression.Comparison("name", Operator.EQUAL, List.of("nothing"), false));

    assertEquals(1, bookingConfigurationDao.countResources(targetFilter, readable));
    assertEquals(0, bookingConfigurationDao.countResources(targetFilter, unreadable));
    assertEquals(0, bookingConfigurationDao.getResources(targetFilter, unreadable).total());
    assertEquals(1, bookingConfigurationDao.countResources(all, unreadable));
    assertEquals(1, bookingConfigurationDao.getResources(all, unreadable).total());
  }
}
