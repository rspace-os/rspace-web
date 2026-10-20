package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.researchspace.model.User;
import jakarta.persistence.Embeddable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.jupiter.api.Test;

class AnnotatedCollectionDescriptionFactoryTest {

  @Test
  void preservesAuditAliasPrecedenceAndMemberOrder() {
    CollectionDescription<AuditEntity> description =
        CollectionDescription.fromApiV2Resource(
            AuditResource.class, AuditEntity.class, List.of(new Sort("id", true)));

    assertEquals(
        List.of("id", "quantity", "createdAt", "updatedAt"),
        description.fields().stream().map(Field::name).toList());
    assertEquals(
        List.of("target", "createdBy", "updatedBy"),
        description.relationships().stream().map(Relationship::name).toList());
    assertEquals("targetId", description.requireRelationship("target").binding().idProperty());
    assertEquals(
        new ResourceReference<>("targets", 45L),
        description.requireRelationship("target").binding().read(new AuditEntity()));
    assertEquals("createdAt", description.requireField("createdAt").property());
    assertEquals("updatedAt", description.requireField("updatedAt").property());
    assertEquals(
        "updatedBy.id", description.requireRelationship("updatedBy").binding().idProperty());
    assertEquals(
        new ResourceReference<>("users", 42L),
        description.requireRelationship("updatedBy").binding().read(new AuditEntity()));
    assertTrue(description.requireRelationship("target").nullable());
    assertFalse(description.requireRelationship("target").writableOn(WriteOperation.CREATE));

    ResourceRegistry registry =
        new ResourceRegistry(List.of(description, usersDescription(), targetsDescription()));
    Map<String, Object> document =
        new ResourceRenderer(registry)
            .render(new AuditEntity(), description, FieldSelection.all(), IncludeTree.empty());
    assertEquals(
        List.of("id", "quantity", "createdAt", "updatedAt", "target", "createdBy", "updatedBy"),
        new ArrayList<>(document.keySet()));
  }

  @Test
  void readsAnUninitializedAuditUserProxyIdWithoutCallingGetId() {
    User proxy = mock(User.class, withSettings().extraInterfaces(HibernateProxy.class));
    LazyInitializer initializer = mock(LazyInitializer.class);
    when(((HibernateProxy) proxy).getHibernateLazyInitializer()).thenReturn(initializer);
    when(initializer.getIdentifier()).thenReturn(73L);
    ProxyAuditEntity entity = new ProxyAuditEntity(proxy);
    CollectionDescription<ProxyAuditEntity> description =
        CollectionDescription.fromApiV2Resource(
            ProxyAuditResource.class, ProxyAuditEntity.class, List.of(new Sort("id", true)));

    for (String name : List.of("createdBy", "target")) {
      assertEquals(
          new ResourceReference<>("users", 73L),
          description.requireRelationship(name).binding().read(entity));
    }
    verify(proxy, never()).getId();
  }

  @Test
  void preservesPrimitiveBoxingAndRejectsIncompatibleAccessorTypes() {
    CollectionDescription<BoxedQuantityEntity> description =
        CollectionDescription.fromApiV2Resource(
            BoxedQuantityResource.class, BoxedQuantityEntity.class, List.of(new Sort("id", true)));
    BoxedQuantityEntity entity = new BoxedQuantityEntity();

    assertEquals(3L, description.toDocument(entity).get("quantity"));
    description.apply(entity, Map.of("quantity", 8L), WriteOperation.UPDATE);
    assertEquals(8L, entity.getQuantity());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionDescription.fromApiV2Resource(
                MismatchedResource.class, MismatchedEntity.class, List.of(new Sort("id", true))));
  }

  @Test
  void rethrowsRuntimeExceptionsFromReadersAndWriters() {
    CollectionDescription<ThrowingEntity> description =
        CollectionDescription.fromApiV2Resource(
            ThrowingResource.class, ThrowingEntity.class, List.of(new Sort("id", true)));
    IllegalStateException readerFailure = new IllegalStateException("reader failed");
    IllegalStateException writerFailure = new IllegalStateException("writer failed");
    ThrowingEntity entity = new ThrowingEntity(readerFailure, writerFailure);

    assertSame(
        readerFailure,
        assertThrows(IllegalStateException.class, () -> description.toDocument(entity)));
    assertSame(
        writerFailure,
        assertThrows(
            IllegalStateException.class,
            () -> description.apply(entity, Map.of("value", "new"), WriteOperation.UPDATE)));
  }

  private static CollectionDescription<User> usersDescription() {
    return new CollectionDescription<>(
        "users",
        User.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), User::getId)),
        "id",
        List.of(new Sort("id", true)));
  }

  private static CollectionDescription<RelatedTarget> targetsDescription() {
    return new CollectionDescription<>(
        "targets",
        RelatedTarget.class,
        List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), RelatedTarget::id)),
        "id",
        List.of(new Sort("id", true)));
  }

  @ApiV2ResourceDefinition(name = "auditResources", entity = AuditEntity.class, id = "id")
  private record AuditResource(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceField long quantity,
      @ApiV2ResourceRelationship(
              relationTo = {
                @ApiV2RelationshipTarget(resource = "targets", entity = RelatedTarget.class)
              },
              idProperty = "targetId")
          ResourceReference<?, ?> target) {}

  public static final class AuditEntity {

    public Long getId() {
      return 1L;
    }

    public Long getQuantity() {
      return 3L;
    }

    public void setQuantity(Long quantity) {}

    public Date getCreatedAt() {
      return new Date(1000L);
    }

    public Date getCreationDate() {
      return new Date(2000L);
    }

    public Date getUpdatedAt() {
      return new Date(3000L);
    }

    public Date getModificationDate() {
      return new Date(4000L);
    }

    public User getCreatedBy() {
      return user(41L);
    }

    public User getUpdatedBy() {
      return user(42L);
    }

    public User getLastUpdatedBy() {
      return user(43L);
    }

    public User getModifiedBy() {
      return user(44L);
    }

    public RelatedTarget getTarget() {
      return new RelatedTarget(45L);
    }
  }

  @ApiV2ResourceDefinition(name = "proxyAuditResources", entity = ProxyAuditEntity.class, id = "id")
  private record ProxyAuditResource(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceRelationship(
              relationTo = @ApiV2RelationshipTarget(resource = "users", entity = User.class))
          ResourceReference<?, ?> target) {}

  public static final class ProxyAuditEntity {

    private final User createdBy;

    ProxyAuditEntity(User createdBy) {
      this.createdBy = createdBy;
    }

    public Long getId() {
      return 1L;
    }

    public User getCreatedBy() {
      return createdBy;
    }

    public User getTarget() {
      return createdBy;
    }
  }

  public record RelatedTarget(Long id) {}

  @ApiV2ResourceDefinition(
      name = "boxedQuantities",
      entity = BoxedQuantityEntity.class,
      id = "id",
      auditFields = false)
  private record BoxedQuantityResource(
      @ApiV2ResourceField Long id, @ApiV2ResourceField long quantity) {}

  public static final class BoxedQuantityEntity {

    private Long quantity = 3L;

    public Long getId() {
      return 1L;
    }

    public Long getQuantity() {
      return quantity;
    }

    public void setQuantity(Long quantity) {
      this.quantity = quantity;
    }
  }

  @ApiV2ResourceDefinition(
      name = "mismatched",
      entity = MismatchedEntity.class,
      id = "id",
      auditFields = false)
  private record MismatchedResource(
      @ApiV2ResourceField Long id, @ApiV2ResourceField String value) {}

  public static final class MismatchedEntity {

    public Long getId() {
      return 1L;
    }

    public Integer getValue() {
      return 2;
    }
  }

  @ApiV2ResourceDefinition(
      name = "throwing",
      entity = ThrowingEntity.class,
      id = "id",
      auditFields = false)
  private record ThrowingResource(@ApiV2ResourceField Long id, @ApiV2ResourceField String value) {}

  public static final class ThrowingEntity {

    private final RuntimeException readerFailure;
    private final RuntimeException writerFailure;

    ThrowingEntity(RuntimeException readerFailure, RuntimeException writerFailure) {
      this.readerFailure = readerFailure;
      this.writerFailure = writerFailure;
    }

    public Long getId() {
      return 1L;
    }

    public String getValue() {
      throw readerFailure;
    }

    public void setValue(String value) {
      throw writerFailure;
    }
  }

  private static User user(long id) {
    User user = new User();
    user.setId(id);
    return user;
  }

  private enum TargetKind {
    INSTRUMENT
  }

  public record RecordValue(TargetKind kind, Long id) {}

  @Embeddable
  public static final class InstrumentTargetReference {
    public TargetKind type() {
      return TargetKind.INSTRUMENT;
    }

    public Long id() {
      return 51L;
    }
  }

  public static final class BeanValue {
    public String getKind() {
      return "INSTRUMENT";
    }

    public Long getId() {
      return 62L;
    }

    public void setMissing(Long value) {}
  }

  public static final class LinkEntity {
    public Long getId() {
      return 1L;
    }

    public InstrumentTargetReference getTarget() {
      return new InstrumentTargetReference();
    }

    public RecordValue getRecordTarget() {
      return new RecordValue(TargetKind.INSTRUMENT, 51L);
    }

    public BeanValue getBeanTarget() {
      return new BeanValue();
    }

    public void setWriteOnly(BeanValue value) {}
  }

  @ApiV2ResourceDefinition(name = "relationships", entity = LinkEntity.class, id = "id")
  private record RelationshipResource(
      @ApiV2ResourceField Long id,
      @ApiV2ResourceRelationship(
              relationTo =
                  @ApiV2RelationshipTarget(
                      resource = "instruments",
                      entity = RelatedTarget.class,
                      kind = "INSTRUMENT",
                      globalIdPrefix = "IN"),
              kindProperty = "target.type",
              idProperty = "target.id",
              requiredOnCreate = true,
              nullable = false,
              writeOn = WriteOperation.CREATE)
          ResourceReference<?, ?> target,
      @ApiV2ResourceRelationship(
              relationTo =
                  @ApiV2RelationshipTarget(
                      resource = "instruments",
                      entity = RelatedTarget.class,
                      kind = "INSTRUMENT"))
          ResourceReference<?, ?> recordTarget,
      @ApiV2ResourceRelationship(
              property = "beanTarget",
              relationTo =
                  @ApiV2RelationshipTarget(
                      resource = "instruments",
                      entity = RelatedTarget.class,
                      kind = "INSTRUMENT",
                      globalIdPrefix = "IN"),
              requiredOnCreate = true,
              writeOn = {WriteOperation.CREATE, WriteOperation.UPDATE},
              acceptGlobalIdOn = {WriteOperation.CREATE, WriteOperation.UPDATE},
              allowSelfReference = true,
              readAccess = ApiV2ResourceField.AccessPreset.AUTHENTICATED,
              writeAccess = ApiV2ResourceField.AccessPreset.SYSADMIN,
              title = "Target",
              description = "Linked target.",
              example = "IN-62",
              deprecated = true)
          ResourceReference<?, ?> linked,
      @ApiV2ResourceMember(factory = "valueField") Object value,
      @ApiV2ResourceMember(factory = "relatedRelationship") Object related) {
    public static ResourceMember<LinkEntity> valueField() {
      return Field.readOnly("value", "id", CollectionFieldTypes.longNumber(), LinkEntity::getId);
    }

    public static ResourceMember<LinkEntity> relatedRelationship() {
      return Relationship.referenceToOne(
          "related",
          "targets",
          CollectionFieldTypes.longNumber(),
          RecordValue.class,
          LinkEntity::getRecordTarget,
          RecordValue::id,
          "recordTarget.id");
    }
  }

  @Test
  void matchesCodeBuiltRelationship() {
    Relationship<LinkEntity> actual =
        describe(RelationshipResource.class).requireRelationship("target");
    Relationship<LinkEntity> expected =
        Relationship.polymorphicToOne(
                "target",
                CollectionFieldTypes.longNumber(),
                List.of(
                    new RelationshipTarget<>(
                        "instruments", TargetKind.INSTRUMENT, "IN", RelatedTarget.class)),
                new SplitReferenceBinding<LinkEntity, TargetKind, Long>(
                    entity ->
                        new ResourceReference<>(entity.getTarget().type(), entity.getTarget().id()),
                    "target.type",
                    "target.id"))
            .required()
            .writeOnlyOn(WriteOperation.CREATE);
    assertEquals(
        expected.binding().read(new LinkEntity()), actual.binding().read(new LinkEntity()));
    assertEquals(expected.binding().kindProperty(), actual.binding().kindProperty());
    assertEquals(expected.binding().idProperty(), actual.binding().idProperty());
    for (WriteOperation operation : WriteOperation.values()) {
      assertEquals(expected.writableOn(operation), actual.writableOn(operation));
      for (RelationshipInputForm form : RelationshipInputForm.values()) {
        assertEquals(expected.acceptsInput(operation, form), actual.acceptsInput(operation, form));
      }
    }
    assertEquals(expected.nullable(), actual.nullable());
    assertEquals(expected.isRequiredOnCreate(), actual.isRequiredOnCreate());
    assertEquals(expected.targets(), actual.targets());
    assertEquals(expected.openApi, actual.openApi);
  }

  @Test
  void readsDefaultRecordPathsAndAppliesBeanFlagsAndMemberFactories() {
    CollectionDescription<LinkEntity> description = describe(RelationshipResource.class);
    LinkEntity entity = new LinkEntity();
    Relationship<LinkEntity> defaults = description.requireRelationship("recordTarget");
    assertEquals(
        new ResourceReference<>(TargetKind.INSTRUMENT, 51L), defaults.binding().read(entity));
    assertEquals("recordTarget.id", defaults.binding().idProperty());
    assertEquals("recordTarget.kind", defaults.binding().kindProperty());
    assertTrue(defaults.nullable());
    for (WriteOperation operation : WriteOperation.values()) {
      assertFalse(defaults.writableOn(operation));
    }
    Relationship<LinkEntity> linked = description.requireRelationship("linked");
    assertEquals(new ResourceReference<>("INSTRUMENT", 62L), linked.binding().read(entity));
    assertEquals("INSTRUMENT", linked.targets().get(0).storedKind());
    assertTrue(linked.isRequiredOnCreate());
    assertFalse(linked.nullable());
    assertTrue(linked.selfReferenceAllowed());
    for (WriteOperation operation : WriteOperation.values()) {
      assertTrue(linked.writableOn(operation));
      assertTrue(linked.acceptsInput(operation, RelationshipInputForm.GLOBAL_ID));
    }
    assertEquals(
        ApiV2ResourceField.AccessPreset.AUTHENTICATED.resolve().documentation(),
        linked.readAccess().documentation());
    assertEquals(
        ApiV2ResourceField.AccessPreset.SYSADMIN.resolve().documentation(),
        linked.writeAccess.documentation());
    assertEquals(
        new OpenApiSchemaDocumentation(
            "Target", "Linked target.", "IN-62", true, null, List.of(), Map.of()),
        linked.openApi);
    assertEquals(1L, description.toDocument(entity).get("value"));
    assertEquals(
        new ResourceReference<>("targets", 51L),
        description.requireRelationship("related").binding().read(entity));
  }

  private static CollectionDescription<LinkEntity> describe(Class<?> resourceType) {
    return CollectionDescription.fromApiV2Resource(
        resourceType, LinkEntity.class, List.of(new Sort("id", true)));
  }

  private static void assertInvalidResource(Class<?> resourceType, String message) {
    IllegalArgumentException failure =
        assertThrows(IllegalArgumentException.class, () -> describe(resourceType));
    assertTrue(failure.getMessage().contains(message), failure.getMessage());
  }

  @Test
  void rejectsInvalidDeclarationsWithUsefulMessages() {
    assertInvalidResource(
        MultipleAnnotationsResource.class, "exactly one resource member annotation");
    assertInvalidResource(NonReferenceResource.class, "must be a ResourceReference");
    assertInvalidResource(UnreadablePropertyResource.class, ".writeOnly is not readable");
    assertInvalidResource(PartialKindsResource.class, "kinds must be set on every target or none");
    assertInvalidResource(UnknownKindResource.class, "unknown kind UNKNOWN");
    assertInvalidResource(MissingIdResource.class, ".missing is not readable");
    assertInvalidResource(UnreadableKindResource.class, ".missing is not readable");
    assertInvalidResource(
        MonomorphicKindResource.class, "monomorphic relationship cannot declare a kind property");
    assertInvalidResource(MissingFactoryResource.class, "must be a no-argument method");
    assertInvalidResource(NonStaticResource.class, "accessible static no-argument method");
    assertInvalidResource(WrongNameResource.class, "must return a member named value");
    assertInvalidResource(WildcardEntityResource.class, "entity type must accept");
  }

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record MultipleAnnotationsResource(
      @ApiV2ResourceField
          @ApiV2ResourceRelationship(
              relationTo =
                  @ApiV2RelationshipTarget(resource = "targets", entity = RelatedTarget.class))
          ResourceReference<?, ?> target) {}

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record NonReferenceResource(
      @ApiV2ResourceRelationship(
              relationTo =
                  @ApiV2RelationshipTarget(resource = "targets", entity = RelatedTarget.class))
          String target) {}

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record UnreadablePropertyResource(
      @ApiV2ResourceRelationship(
              property = "writeOnly",
              relationTo =
                  @ApiV2RelationshipTarget(resource = "targets", entity = RelatedTarget.class))
          ResourceReference<?, ?> target) {}

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record PartialKindsResource(
      @ApiV2ResourceRelationship(
              relationTo = {
                @ApiV2RelationshipTarget(
                    resource = "instruments",
                    entity = RelatedTarget.class,
                    kind = "INSTRUMENT"),
                @ApiV2RelationshipTarget(resource = "targets", entity = RelatedTarget.class)
              })
          ResourceReference<?, ?> target) {}

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record UnknownKindResource(
      @ApiV2ResourceRelationship(
              property = "recordTarget",
              relationTo =
                  @ApiV2RelationshipTarget(
                      resource = "instruments",
                      entity = RelatedTarget.class,
                      kind = "UNKNOWN"))
          ResourceReference<?, ?> target) {}

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record MissingIdResource(
      @ApiV2ResourceRelationship(
              idProperty = "target.missing",
              relationTo =
                  @ApiV2RelationshipTarget(resource = "targets", entity = RelatedTarget.class))
          ResourceReference<?, ?> target) {}

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record UnreadableKindResource(
      @ApiV2ResourceRelationship(
              property = "beanTarget",
              kindProperty = "beanTarget.missing",
              relationTo =
                  @ApiV2RelationshipTarget(
                      resource = "instruments",
                      entity = RelatedTarget.class,
                      kind = "INSTRUMENT"))
          ResourceReference<?, ?> target) {}

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record MonomorphicKindResource(
      @ApiV2ResourceRelationship(
              kindProperty = "target.type",
              relationTo =
                  @ApiV2RelationshipTarget(resource = "targets", entity = RelatedTarget.class))
          ResourceReference<?, ?> target) {}

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record MissingFactoryResource(@ApiV2ResourceMember(factory = "missing") Object value) {}

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record NonStaticResource(@ApiV2ResourceMember(factory = "make") Object value) {
    public ResourceMember<LinkEntity> make() {
      return RelationshipResource.valueField();
    }
  }

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record WrongNameResource(@ApiV2ResourceMember(factory = "make") Object value) {
    public static ResourceMember<LinkEntity> make() {
      return Field.readOnly("other", "id", CollectionFieldTypes.longNumber(), LinkEntity::getId);
    }
  }

  @ApiV2ResourceDefinition(name = "invalid", entity = LinkEntity.class, id = "id")
  private record WildcardEntityResource(@ApiV2ResourceMember(factory = "make") Object value) {
    public static ResourceMember<? extends User> make() {
      return Field.readOnly("value", "id", CollectionFieldTypes.longNumber(), User::getId);
    }
  }
}
