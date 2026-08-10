package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Covers the REST API v2 collection query for instruments, which only a real database can check:
 * the nested {@code editInfo.name} filter property, the single-table discriminator that keeps
 * instrument templates out of the collection, and the inventory permission restriction.
 */
public class InstrumentDaoCollectionTest extends SpringTransactionalTest {

  @Autowired private InstrumentDao instrumentDao;

  private static ResourceRequest request(FilterExpression filter) {
    return new ResourceRequest(
        filter,
        List.of(new Sort("name", true), new Sort("id", true)),
        new ResourceRequest.Page(1, 20),
        FieldSelection.all(),
        IncludeTree.empty());
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

  @Test
  public void filtersByNameThroughTheNestedEditInfoProperty() {
    User owner = createInitAndLoginAnyUser();
    createBasicInstrumentForUser(owner, "Confocal microscope");
    createBasicInstrumentForUser(owner, "Centrifuge");

    ResourceRequest request = request(nameContains("confocal"));

    assertEquals(
        List.of("Confocal microscope"),
        names(instrumentDao.getReadableResources(request, owner).resources()));
    assertEquals(1, instrumentDao.countReadableResources(request, owner));
  }

  @Test
  public void excludesInstrumentTemplates() {
    User owner = createInitAndLoginAnyUser();
    createBasicInstrumentForUser(owner, "Shared name");
    createBasicInstrumentTemplateForUser(owner, "Shared name");

    List<Instrument> matches =
        instrumentDao.getReadableResources(request(nameContains("Shared name")), owner).resources();

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
        names(instrumentDao.getReadableResources(first, owner).resources()));
    assertEquals(2, instrumentDao.getReadableResources(first, owner).total());
    assertEquals(
        List.of("Beta scope"),
        names(instrumentDao.getReadableResources(second, owner).resources()));
  }

  @Test
  public void hidesAnInstrumentTheCallerMayNotRead() {
    User owner = createInitAndLoginAnyUser();
    createBasicInstrumentForUser(owner, "Private scope");
    User other = createInitAndLoginAnyUser();

    ResourceRequest request = request(nameContains("Private scope"));

    assertTrue(instrumentDao.getReadableResources(request, other).resources().isEmpty());
    assertEquals(0, instrumentDao.countReadableResources(request, other));
    assertEquals(1, instrumentDao.countReadableResources(request, owner));
  }

  @Test
  public void appliesTheSoftDeletionConstraintFromTheReadPolicy() {
    User owner = createInitAndLoginAnyUser();
    Instrument instrument =
        instrumentDao.get(createBasicInstrumentForUser(owner, "Trashed scope").getId());
    instrument.setRecordDeleted(true);
    instrumentDao.save(instrument);

    ResourceRequest request =
        request(new FilterExpression.And(List.of(nameContains("Trashed scope"), notDeleted())));

    assertTrue(instrumentDao.getReadableResources(request, owner).resources().isEmpty());
    assertEquals(0, instrumentDao.countReadableResources(request, owner));
  }
}
