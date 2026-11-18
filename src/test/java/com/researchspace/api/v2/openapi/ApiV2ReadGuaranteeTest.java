package com.researchspace.api.v2.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.inventory.api.v2.InstrumentResourceOperations;
import com.researchspace.inventory.model.ApiV2ContainerResource;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.FieldSchema;
import com.researchspace.model.collection.ResourceFieldSchema;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import com.researchspace.testutils.TestFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The backend never omits a property the OpenAPI document marks {@code x-rspace-read-guaranteed}
 * from a readable document that requested it.
 */
class ApiV2ReadGuaranteeTest {

  private static final User OWNER = TestFactory.createAnyUser("guaranteeOwner");

  @Test
  void guaranteedPropertiesAreNeverUnreadable() {
    for (CollectionDescription<?> description : descriptions()) {
      Set<String> guaranteed = guaranteed(description, member -> true);
      assertTrue(guaranteed.contains(description.idField()), description.resourceName());
      for (AccessContext context : contexts(description.resourceName())) {
        Set<String> unreadable = description.unreadableFields(context);
        for (String name : guaranteed) {
          assertFalse(unreadable.contains(name), description.resourceName() + "." + name);
        }
      }
    }
  }

  @Test
  void entityReadsContainEveryGuaranteedField() throws Exception {
    Instrument instrument = new Instrument();
    instrument.setId(4L);
    assertRendersGuaranteedFields(ApiV2UserResource.DESCRIPTION, OWNER);
    assertRendersGuaranteedFields(
        ApiV2ContainerResource.DESCRIPTION, TestFactory.createListContainer(OWNER));
    assertRendersGuaranteedFields(ApiV2InstrumentResource.DESCRIPTION, instrument);
  }

  @Test
  void scalarInstrumentReadsContainEveryGuaranteedProperty() {
    InstrumentEntityApiManager manager = mock(InstrumentEntityApiManager.class);
    when(manager.getReadableInstrumentSummaries(any(ResourceRequest.class), any()))
        .thenReturn(
            new ResourcePage<>(List.of(new InstrumentReadSummary(4L, null, false, null)), 1));
    var operations =
        new InstrumentResourceOperations(
            manager,
            mock(InstrumentReadAccess.class),
            mock(InstrumentCustomFieldManager.class),
            mock(ExtraFieldDao.class));

    Map<String, Object> document =
        operations.read(mock(ResourceRequest.class), OWNER).resources().get(0);
    Set<String> guaranteed = guaranteed(ApiV2InstrumentResource.DESCRIPTION, member -> true);
    assertTrue(document.keySet().containsAll(guaranteed), document.keySet().toString());
  }

  private static <T> void assertRendersGuaranteedFields(
      CollectionDescription<T> description, T entity) {
    Set<String> guaranteed = guaranteed(description, member -> member instanceof FieldSchema);
    for (AccessContext context : contexts(description.resourceName())) {
      Map<String, Object> document =
          description.toDocument(entity, field -> true, new LinkedHashMap<>(), context);
      assertTrue(
          document.keySet().containsAll(guaranteed),
          description.resourceName() + " " + document.keySet());
    }
  }

  private static List<CollectionDescription<?>> descriptions() {
    return List.of(
        ApiV2UserResource.DESCRIPTION,
        ApiV2ContainerResource.DESCRIPTION,
        ApiV2InstrumentResource.DESCRIPTION);
  }

  private static Set<String> guaranteed(
      CollectionDescription<?> description, Predicate<ResourceFieldSchema> kind) {
    return description.schema().documentFields().stream()
        .filter(kind)
        .filter(member -> member.name().equals(description.idField()) || member.readInherited())
        .map(ResourceFieldSchema::name)
        .collect(Collectors.toSet());
  }

  /** A sysadmin, a PI, a user, the owner and an anonymous caller. */
  private static List<AccessContext> contexts(String resource) {
    return List.of(
        read(
            TestFactory.createAnyUserWithRole("guaranteeSysadmin", Constants.SYSADMIN_ROLE),
            resource),
        read(TestFactory.createAnyUserWithRole("guaranteePi", Constants.PI_ROLE), resource),
        read(TestFactory.createAnyUser("guaranteeUser"), resource),
        new AccessContext(OWNER, AccessContext.Operation.READ, resource, OWNER.getId()),
        read(null, resource));
  }

  private static AccessContext read(User user, String resource) {
    return new AccessContext(user, AccessContext.Operation.READ, resource);
  }
}
