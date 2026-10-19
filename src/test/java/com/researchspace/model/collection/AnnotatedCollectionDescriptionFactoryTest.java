package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.researchspace.model.User;
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
            AuditResource.class,
            AuditEntity.class,
            List.of(targetRelationship()),
            List.of(new Sort("id", true)));

    assertEquals(
        List.of("id", "quantity", "createdAt", "updatedAt"),
        description.fields().stream().map(Field::name).toList());
    assertEquals(
        List.of("target", "createdBy", "updatedBy"),
        description.relationships().stream().map(Relationship::name).toList());
    assertEquals("createdAt", description.requireField("createdAt").property());
    assertEquals("updatedAt", description.requireField("updatedAt").property());
    assertEquals(
        "updatedBy.id", description.requireRelationship("updatedBy").binding().idProperty());
    assertEquals(
        new ResourceReference<>("users", 42L),
        description.requireRelationship("updatedBy").binding().read(new AuditEntity()));

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
            ProxyAuditResource.class,
            ProxyAuditEntity.class,
            List.of(),
            List.of(new Sort("id", true)));

    assertEquals(
        new ResourceReference<>("users", 73L),
        description.requireRelationship("createdBy").binding().read(entity));
    verify(proxy, never()).getId();
  }

  @Test
  void preservesPrimitiveBoxingAndRejectsIncompatibleAccessorTypes() {
    CollectionDescription<BoxedQuantityEntity> description =
        CollectionDescription.fromApiV2Resource(
            BoxedQuantityResource.class,
            BoxedQuantityEntity.class,
            List.of(),
            List.of(new Sort("id", true)));
    BoxedQuantityEntity entity = new BoxedQuantityEntity();

    assertEquals(3L, description.toDocument(entity).get("quantity"));
    description.apply(entity, Map.of("quantity", 8L), WriteOperation.UPDATE);
    assertEquals(8L, entity.getQuantity());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionDescription.fromApiV2Resource(
                MismatchedResource.class,
                MismatchedEntity.class,
                List.of(),
                List.of(new Sort("id", true))));
  }

  @Test
  void rethrowsRuntimeExceptionsFromReadersAndWriters() {
    CollectionDescription<ThrowingEntity> description =
        CollectionDescription.fromApiV2Resource(
            ThrowingResource.class, ThrowingEntity.class, List.of(), List.of(new Sort("id", true)));
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

  private static Relationship<AuditEntity> targetRelationship() {
    return Relationship.referenceToOne(
        "target",
        "targets",
        CollectionFieldTypes.longNumber(),
        RelatedTarget.class,
        AuditEntity::getTarget,
        RelatedTarget::id,
        "targetId");
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
  private record AuditResource(@ApiV2ResourceField Long id, @ApiV2ResourceField long quantity) {}

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
  private record ProxyAuditResource(@ApiV2ResourceField Long id) {}

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
  }

  private record RelatedTarget(Long id) {}

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
}
