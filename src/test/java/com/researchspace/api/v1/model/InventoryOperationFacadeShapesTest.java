package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Each typed facade agrees with the definition it fronts (plan-operations-server-builds.md, M6
 * gate): its origin cardinality follows {@code requiresMultiple}, so {@code @Size(min = 2)} cannot
 * drift from it; its input fields are exactly the definition's input keys, so {@code
 * toOperationInputs()} stays a mechanical copy and a core error names a field the client sent; and
 * it carries no value rule, so nothing can go stale against the config. Plus the seven request
 * examples frozen in operations-facade-design-m0.md bind as written.
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
            Size size = origins.getAnnotation(Size.class);
            assertNotNull(size, key + ": minItems belongs in the published schema");
            assertEquals(2, size.min(), key);
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
            assertTrue(
                annotation instanceof NotNull || annotation instanceof Size,
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
}
