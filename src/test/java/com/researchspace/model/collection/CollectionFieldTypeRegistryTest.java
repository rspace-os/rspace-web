package com.researchspace.model.collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.TextNode;
import com.researchspace.model.collection.CollectionFieldTypeRegistry.DeclarationOptions;
import com.researchspace.model.collection.CollectionFieldTypeRegistry.Registration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionFieldTypeRegistryTest {
  private final CollectionFieldTypeRegistry registry = CollectionFieldTypeRegistry.builtIns();

  @Test
  void resolvesPrimitiveAliasesAndEnumFamilies() {
    assertEquals(Long.class, registry.resolve(long.class, DeclarationOptions.DEFAULT).javaType());
    assertEquals(Integer.class, registry.resolve(int.class, DeclarationOptions.DEFAULT).javaType());
    assertEquals(
        Boolean.class, registry.resolve(boolean.class, DeclarationOptions.DEFAULT).javaType());
    CollectionFieldType<?> enumeration = registry.resolve(State.class, DeclarationOptions.DEFAULT);
    assertEquals(State.OPEN, enumeration.parse("OPEN"));
    assertEquals(Map.of("enum", List.of("OPEN", "CLOSED")), enumeration.schema().structure());
    assertTrue(enumeration.sortable());
  }

  @Test
  void createsTextPerDeclarationAndKeepsItsParseTimeLimit() {
    CollectionFieldType<?> shortText = registry.resolve(String.class, new DeclarationOptions(2));
    CollectionFieldType<?> longText = registry.resolve(String.class, new DeclarationOptions(5));
    assertNotSame(shortText, longText);
    assertEquals(2, shortText.schema().maxLength());
    assertThrows(IllegalArgumentException.class, () -> shortText.parse("abc"));
    assertEquals("abc", longText.parse("abc"));
  }

  @Test
  void rejectsDuplicateClassesAndPrimitiveAliasConflicts() {
    Registration<Long> boxed =
        new Registration<>(Long.class, ignored -> CollectionFieldTypes.longNumber());
    Registration<Long> primitive =
        new Registration<>(long.class, ignored -> CollectionFieldTypes.longNumber());
    assertThrows(
        IllegalArgumentException.class,
        () -> new CollectionFieldTypeRegistry(List.of(boxed, boxed)));
    assertThrows(
        IllegalArgumentException.class,
        () -> new CollectionFieldTypeRegistry(List.of(boxed, primitive)));
  }

  @Test
  void rejectsUnknownClassesAndIncompatibleOptions() {
    assertEquals(
        "Text maximum length must not be less than -1",
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.resolve(String.class, new DeclarationOptions(-2)))
            .getMessage());
    assertEquals(
        "Maximum length is only supported for text fields",
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.resolve(Long.class, new DeclarationOptions(2)))
            .getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () -> registry.resolve(State.class, new DeclarationOptions(2)));
    assertEquals(
        "Unsupported resource field type java.lang.Object",
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.resolve(Object.class, DeclarationOptions.DEFAULT))
            .getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () -> registry.resolve(List.class, DeclarationOptions.DEFAULT));
  }

  @Test
  void registrySnapshotsDoNotFollowCallerMutations() {
    List<Registration<?>> registrations = new ArrayList<>(registry.registrations());
    CollectionFieldTypeRegistry snapshot = new CollectionFieldTypeRegistry(registrations);
    registrations.clear();
    assertEquals(Long.class, snapshot.resolve(Long.class, DeclarationOptions.DEFAULT).javaType());
    assertThrows(UnsupportedOperationException.class, () -> snapshot.registrations().clear());
  }

  @Test
  void customRegistrationReachesAnnotatedDescriptionsAndFilterParsing() {
    List<Registration<?>> registrations = new ArrayList<>(registry.registrations());
    registrations.add(
        new Registration<>(
            Token.class,
            options -> {
              options.requireNoOptions();
              return new TokenType();
            }));
    CollectionDescription<Entity> description =
        CollectionDescription.fromApiV2Resource(
            Resource.class,
            Entity.class,
            List.of(),
            List.of(new Sort("id", true)),
            new CollectionFieldTypeRegistry(registrations));

    Field<Entity, ?> token = description.requireField("token");
    assertEquals(
        new Token("abc"), token.type().parseDocument(new TextNode("abc"), WriteOperation.CREATE));
    FilterExpression.Comparison comparison =
        assertInstanceOf(
            FilterExpression.Comparison.class,
            new RsqlFilterParser(description).parse("token==abc"));
    assertEquals(List.of(new Token("abc")), comparison.values());
    assertEquals(FieldComparisonStrategy.SCALAR, token.type().comparisonStrategy());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CollectionDescription.fromApiV2Resource(
                Resource.class, Entity.class, List.of(), List.of(new Sort("id", true))));
  }

  private enum State {
    OPEN,
    CLOSED
  }

  private record Token(String value) {}

  private static final class TokenType implements CollectionFieldType<Token> {
    @Override
    public Class<Token> javaType() {
      return Token.class;
    }

    @Override
    public InputKind inputKind() {
      return InputKind.STRING;
    }

    @Override
    public Token parse(String value) {
      return new Token(value);
    }

    @Override
    public Object serialize(Token value) {
      return value.value();
    }

    @Override
    public Set<Operator> operators() {
      return Set.of(Operator.EQUAL);
    }
  }

  @ApiV2ResourceDefinition(name = "tokens", entity = Entity.class, id = "id", auditFields = false)
  private record Resource(@ApiV2ResourceField Long id, @ApiV2ResourceField Token token) {}

  public static final class Entity {
    public Long getId() {
      return 1L;
    }

    public Token getToken() {
      return new Token("abc");
    }
  }
}
