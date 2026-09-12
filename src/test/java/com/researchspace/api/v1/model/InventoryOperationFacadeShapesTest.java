package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests.Aliquot;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests.Cryopreserve;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests.Derive;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests.Destroy;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests.Passage;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests.Pool;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests.Request;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests.Revive;
import com.researchspace.service.inventory.InventoryOperationConfig;
import com.researchspace.service.inventory.InventoryOperationConfig.Input;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Each typed facade agrees with the definition it fronts (DevDocs/adr/0007, M6 gate): its origin
 * cardinality follows {@code requiresMultiple}, so {@code @Size(min = 2)} cannot drift from it; its
 * input fields are exactly the definition's input keys, so {@code toOperationInputs()} stays a
 * mechanical copy and a core error names a field the client sent; and it carries no value rule, so
 * nothing can go stale against the config. Plus the seven request examples frozen in
 * DevDocs/adr/0007 bind as written.
 */
class InventoryOperationFacadeShapesTest {

  private static final Map<String, Class<? extends Request>> FACADES =
      Map.of(
          "aliquot", Aliquot.class,
          "passage", Passage.class,
          "pool", Pool.class,
          "derive", Derive.class,
          "cryopreserve", Cryopreserve.class,
          "revive", Revive.class,
          "destroy", Destroy.class);

  /** The fields that are not inputs: what is consumed, and the two wizard-level choices. */
  private static final Set<String> STRUCTURAL =
      Set.of("origin", "origins", "templateId", "documentedByGlobalId");

  private final InventoryOperationConfigRegistry registry = new InventoryOperationConfigRegistry();

  /** The mapper the API's converter is built from (see ApiInventoryOperationPostBindingTest). */
  private final ObjectMapper apiMapper = Jackson2ObjectMapperBuilder.json().build();

  private static final Validator validator =
      Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void thereIsExactlyOneFacadePerConfiguredOperation() {
    assertEquals(registry.keys(), FACADES.keySet());
  }

  @Test
  void originCardinalityFollowsRequiresMultiple() {
    FACADES.forEach(
        (key, facade) -> {
          InventoryOperationConfig definition = registry.get(key).orElseThrow();
          Field origin = field(facade, "origin");
          Field origins = field(facade, "origins");
          if (definition.requiresMultiple()) {
            assertNull(origin, key + " is multi-origin, so no singular origin");
            assertNotNull(origins, key);
            List<Size> sizes = sizeConstraints(origins);
            assertFalse(sizes.isEmpty(), key + ": minItems belongs in the published schema");
            assertEquals(2, sizes.stream().mapToInt(Size::min).max().orElse(0), key + ": minItems");
            // The ceiling belongs at binding for the same reason the generic DTO carries one: the
            // validator's MAX_ORIGINS check runs only after Jackson has materialised every element
            // and performTyped has walked all of them parsing global ids, so without this an
            // authenticated caller could post an unbounded origins array (parallel review). Read
            // off the generic DTO rather than restated, so the two endpoints cannot disagree.
            assertEquals(
                genericOriginsCeiling(),
                sizes.stream().mapToInt(Size::max).min().orElse(Integer.MAX_VALUE),
                key + ": maxItems belongs at binding, not only in the core");
          } else {
            assertNotNull(origin, key + " takes exactly one origin");
            assertNull(origins, key + " is single-origin, so no origins list");
            assertNotNull(origin.getAnnotation(NotNull.class), key + ": the origin is required");
          }
        });
  }

  @Test
  void inputFieldsAreExactlyTheDefinitionsInputKeys() {
    FACADES.forEach(
        (key, facade) -> {
          InventoryOperationConfig definition = registry.get(key).orElseThrow();
          // amountTaken is declared as an input so the wizard renders a field, but it travels on
          // the origin element (M3 decision, M0 shapes), never in the inputs.
          Set<String> declared =
              definition.inputs().stream()
                  .map(InventoryOperationConfig.Input::key)
                  .filter(input -> !"amountTaken".equals(input))
                  .collect(Collectors.toCollection(TreeSet::new));
          Set<String> fields =
              fields(facade).stream()
                  .map(Field::getName)
                  .filter(name -> !STRUCTURAL.contains(name))
                  .collect(Collectors.toCollection(TreeSet::new));
          assertEquals(declared, fields, key);
          // The template and documentation target exist only where a sample is created (D8: no
          // other sample metadata).
          assertEquals(!definition.noOutput(), field(facade, "templateId") != null, key);
          assertEquals(!definition.noOutput(), field(facade, "documentedByGlobalId") != null, key);
        });
  }

  @Test
  void noFacadeCarriesAValueRule() {
    // Facades validate shape only; every value rule (required inputs, bounds, amount semantics) is
    // the config-driven core's, so a stale published bound cannot exist (M6).
    for (Class<?> facade : FACADES.values()) {
      for (Field field : fields(facade)) {
        for (Annotation annotation : field.getAnnotations()) {
          if (annotation
              .annotationType()
              .getPackageName()
              .equals("jakarta.validation.constraints")) {
            // Size.List is the repeated form of Size, used where each bound needs its own message;
            // still a shape rule, not a value rule.
            assertTrue(
                annotation instanceof NotNull
                    || annotation instanceof Size
                    || annotation instanceof Size.List,
                facade.getSimpleName() + "." + field.getName() + " carries " + annotation);
          }
        }
      }
    }
  }

  @Test
  void theSevenM0ExamplesBindAsWritten() throws Exception {
    Aliquot aliquot =
        apiMapper.readValue(
            "{ \"origin\": { \"globalId\": \"SS1234\", \"amountTaken\": {\"numericValue\": 6,"
                + " \"unitId\": 3} }, \"sampleName\": \"Aliquots\", \"count\": 2, \"eachAmount\":"
                + " {\"numericValue\": 3, \"unitId\": 3}, \"templateId\": 42,"
                + " \"documentedByGlobalId\": \"SD99\" }",
            Aliquot.class);
    assertEquals("SS1234", aliquot.getOrigin().getGlobalId());
    assertEquals(Set.of("sampleName", "count", "eachAmount"), aliquot.toOperationInputs().keySet());
    assertEquals(2, aliquot.toOperationInputs().get("count"));
    assertEquals(Long.valueOf(42), aliquot.getTemplateId());
    assertEquals("SD99", aliquot.getDocumentedByGlobalId());

    Passage passage =
        apiMapper.readValue(
            "{ \"origin\": { \"globalId\": \"SS1234\" }, \"sampleName\": \"HeLa p3\", \"count\":"
                + " 1, \"eachAmount\": {\"numericValue\": 5, \"unitId\": 3} }",
            Passage.class);
    assertNull(passage.getOrigin().getAmountTaken(), "passage takes nothing");
    assertEquals(Set.of("sampleName", "count", "eachAmount"), passage.toOperationInputs().keySet());

    Pool pool =
        apiMapper.readValue(
            "{ \"origins\": [ { \"globalId\": \"SS1234\", \"amountTaken\": {\"numericValue\": 5,"
                + " \"unitId\": 3} }, { \"globalId\": \"SS5678\", \"amountTaken\":"
                + " {\"numericValue\": 8, \"unitId\": 3} } ], \"sampleName\": \"Pooled lysate\","
                + " \"count\": 1, \"eachAmount\": {\"numericValue\": 13, \"unitId\": 3} }",
            Pool.class);
    assertEquals(2, pool.originList().size());
    assertEquals("SS5678", pool.originList().get(1).getGlobalId());

    Derive derive =
        apiMapper.readValue(
            "{ \"origin\": { \"globalId\": \"SS1234\", \"amountTaken\": {\"numericValue\": 1,"
                + " \"unitId\": 3} }, \"processName\": \"PCR\", \"sampleName\": \"Derived DNA\","
                + " \"count\": 1, \"eachAmount\": {\"numericValue\": 1, \"unitId\": 3} }",
            Derive.class);
    assertEquals("PCR", derive.toOperationInputs().get("processName"));

    Cryopreserve cryopreserve =
        apiMapper.readValue(
            "{ \"origin\": { \"globalId\": \"SS1234\", \"amountTaken\": {\"numericValue\": 1,"
                + " \"unitId\": 3} }, \"sampleName\": \"Frozen\", \"count\": 1, \"eachAmount\":"
                + " {\"numericValue\": 1, \"unitId\": 3}, \"cryomedium\": \"DMSO 10%\","
                + " \"storageTemp\": {\"numericValue\": -80, \"unitId\": 8} }",
            Cryopreserve.class);
    assertEquals(
        Set.of("sampleName", "count", "eachAmount", "cryomedium", "storageTemp"),
        cryopreserve.toOperationInputs().keySet());
    assertTrue(cryopreserve.toOperationInputs().get("storageTemp") instanceof ApiQuantityInfo);

    Revive revive =
        apiMapper.readValue(
            "{ \"origin\": { \"globalId\": \"SS1234\", \"amountTaken\": {\"numericValue\": 1,"
                + " \"unitId\": 3} }, \"sampleName\": \"Revived\", \"count\": 1, \"eachAmount\":"
                + " {\"numericValue\": 1, \"unitId\": 3}, \"storageTemp\": {\"numericValue\": 4,"
                + " \"unitId\": 8} }",
            Revive.class);
    assertEquals(
        Set.of("sampleName", "count", "eachAmount", "storageTemp"),
        revive.toOperationInputs().keySet());

    Destroy destroy =
        apiMapper.readValue("{ \"origin\": { \"globalId\": \"SS1234\" } }", Destroy.class);
    assertEquals("SS1234", destroy.originList().get(0).getGlobalId());
    assertTrue(destroy.toOperationInputs().isEmpty());
    assertNull(destroy.getTemplateId());
  }

  @Test
  void anAbsentOptionalInputIsLeftOutSoTheServerDefaultApplies() throws Exception {
    // D7: count is optional with a server default of 1. It must not arrive as a null value, which
    // the input validator would have to treat specially; it simply is not sent.
    Aliquot aliquot =
        apiMapper.readValue(
            "{ \"origin\": { \"globalId\": \"SS1\" }, \"sampleName\": \"A\", \"eachAmount\":"
                + " {\"numericValue\": 1, \"unitId\": 3} }",
            Aliquot.class);
    assertFalse(aliquot.toOperationInputs().containsKey("count"));
  }

  @Test
  void theOriginElementAcceptsAnExpectedQuantity() throws Exception {
    // D5: optional on every origin, so a script that means "destroy SS1234" can omit it.
    Destroy destroy =
        apiMapper.readValue(
            "{ \"origin\": { \"globalId\": \"SS1\", \"expectedQuantity\": {\"numericValue\": 10,"
                + " \"unitId\": 3} } }",
            Destroy.class);
    assertEquals(
        0,
        new java.math.BigDecimal("10")
            .compareTo(destroy.getOrigin().getExpectedQuantity().getNumericValue()));
  }

  @Test
  void beanValidationEnforcesTheShapeRulesOnly() {
    Pool pool = new Pool();
    pool.setOrigins(List.of(new ApiInventoryOperationRequests.Origin()));
    assertEquals(Set.of("origins"), violatedPaths(pool), "one origin is not a pool");
    pool.setOrigins(null);
    assertEquals(Set.of(), violatedPaths(pool), "the core reports an absent list as origins");

    assertEquals(Set.of("origin"), violatedPaths(new Aliquot()));
    assertEquals(Set.of("origin"), violatedPaths(new Destroy()));

    // No required input is a bean-validation rule: the core reports it by input key.
    Aliquot bare = new Aliquot();
    bare.setOrigin(new ApiInventoryOperationRequests.Origin());
    assertEquals(Set.of(), violatedPaths(bare));
  }

  private static Set<String> violatedPaths(Object request) {
    return validator.validate(request).stream()
        .map(ConstraintViolation::getPropertyPath)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  /** The instance fields of the class and its facade superclasses. */
  private static List<Field> fields(Class<?> facade) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> type = facade; type != Object.class; type = type.getSuperclass()) {
      for (Field field : type.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers())) {
          fields.add(field);
        }
      }
    }
    return fields;
  }

  private static Field field(Class<?> facade, String name) {
    return fields(facade).stream().filter(f -> f.getName().equals(name)).findFirst().orElse(null);
  }

  /**
   * Every {@code @Size} on a field, whether declared singly or repeated through {@code @Size.List}.
   * A field needing a distinct message per bound has to use the repeated form, since one annotation
   * carries one message.
   */
  private static List<Size> sizeConstraints(Field field) {
    return List.of(field.getAnnotationsByType(Size.class));
  }

  /**
   * The origins ceiling the generic endpoint enforces at binding, so a multi-origin facade can be
   * pinned to it rather than restating the number and drifting from it.
   */
  private static int genericOriginsCeiling() {
    Field origins = field(ApiInventoryOperationPost.class, "origins");
    assertNotNull(origins, "the generic request must still declare origins");
    int ceiling =
        sizeConstraints(origins).stream().mapToInt(Size::max).min().orElse(Integer.MAX_VALUE);
    assertNotEquals(
        Integer.MAX_VALUE, ceiling, "the generic request must still cap origins at binding");
    return ceiling;
  }

  // --- the published OpenAPI spec (parallel review, Q17) ---

  private static final Path PUBLISHED_SPEC =
      Path.of("src/main/webapp/resources/rspace_api_inventory_specs_2_26_0.yaml");

  /**
   * The published spec describes the same seven operations this class pins against the config, in
   * prose, and nothing read it: it was the one copy of the facade shapes with no test at all. Its
   * numbers are the ones a client builds against, so a bound left behind after a config change is a
   * customer-visible lie that survives a deprecation cycle (parallel review, Q17).
   *
   * <p>Prose, not schema constraints, is what is checked, because M7 deliberately kept value bounds
   * out of the published schemas for exactly this staleness reason and stated them in the
   * descriptions instead. That decision is what makes this test necessary rather than optional.
   */
  @Test
  void thePublishedSpecStatesTheBoundsTheConfigActuallyDeclares() throws IOException {
    String spec = Files.readString(PUBLISHED_SPEC);

    for (String key : FACADES.keySet()) {
      assertTrue(
          spec.contains("/operations/" + key + ":"),
          () -> "the published spec must carry POST /operations/" + key);
    }

    // count: every creating operation declares the same min/max, and the spec says so twice per
    // operation - once in the field description, once in the 400 description.
    InventoryOperationConfig aliquot = registry.get("aliquot").orElseThrow();
    Input count = input(aliquot, "count");
    assertSpecStates(spec, "Defaults to 1, at most " + count.max().toBigInteger() + ".");
    assertSpecStates(
        spec,
        "`count` is below "
            + count.min().toBigInteger()
            + " or above "
            + count.max().toBigInteger());

    // cryopreserve's ceiling and revive's range, each stated in the spec as a plain number.
    Input cryoTemp = input(registry.get("cryopreserve").orElseThrow(), "storageTemp");
    assertSpecStates(spec, "is above " + cryoTemp.maxCelsius().toBigInteger() + " degrees Celsius");
    Input reviveTemp = input(registry.get("revive").orElseThrow(), "storageTemp");
    assertSpecStates(
        spec,
        "between "
            + reviveTemp.minCelsius().toBigInteger()
            + " and "
            + reviveTemp.maxCelsius().toBigInteger()
            + " degrees Celsius");
    assertSpecStates(
        spec,
        "stored at "
            + new BigDecimal(reviveTemp.defaultValue().toString()).toBigInteger()
            + " degrees Celsius");
  }

  /** Every input key the seven definitions declare is a property of its published schema. */
  @Test
  void thePublishedSchemasCarryEveryInputTheDefinitionDeclares() throws IOException {
    String spec = Files.readString(PUBLISHED_SPEC);
    FACADES.forEach(
        (key, facade) -> {
          String schemaName =
              Character.toUpperCase(key.charAt(0)) + key.substring(1) + "Operation:";
          int start = spec.indexOf("    " + schemaName);
          assertTrue(start > 0, () -> "no " + schemaName + " schema in the published spec");
          // to the next sibling schema (four-space indent), which bounds this one's body
          int end = spec.indexOf("\n    ", spec.indexOf("\n", start) + 1);
          while (end > 0 && spec.startsWith("\n      ", end)) {
            end = spec.indexOf("\n    ", end + 1);
          }
          String schema = spec.substring(start, end > 0 ? end : spec.length());
          for (Input declared : registry.get(key).orElseThrow().inputs()) {
            // amountTaken travels on the origin element, not on the request body.
            if ("amountTaken".equals(declared.key())) {
              continue;
            }
            assertTrue(
                schema.contains("\n        " + declared.key() + ":"),
                () -> schemaName + " must publish the input " + declared.key());
          }
        });
  }

  private static void assertSpecStates(String spec, String sentence) {
    assertTrue(
        spec.contains(sentence),
        () ->
            "the published spec must state \""
                + sentence
                + "\", which is what operations_config.json declares. Update "
                + PUBLISHED_SPEC
                + " when a bound changes.");
  }

  private static Input input(InventoryOperationConfig definition, String key) {
    return definition.inputs().stream()
        .filter(i -> key.equals(i.key()))
        .findFirst()
        .orElseThrow(() -> new AssertionError(definition.key() + " declares no " + key + " input"));
  }
}
