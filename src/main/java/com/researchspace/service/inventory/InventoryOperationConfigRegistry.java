package com.researchspace.service.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.field.FieldType;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * The backend's registry of Inventory operation definitions, parsed once at startup from the single
 * authoritative {@code operations_config.json} on the classpath (DevDocs/adr/0007; the frontend has
 * no copy and fetches GET /operations/config instead). Construction fails fast on a missing or
 * unparseable file so a bad build cannot boot with an unvalidated public endpoint. Stage 2
 * (DevDocs/adr/0007) replaces this source with user-editable definitions without changing
 * consumers.
 */
@Component
public class InventoryOperationConfigRegistry {

  private final Map<String, InventoryOperationConfig> operationsByKey;
  private final String rawConfigJson;

  public InventoryOperationConfigRegistry() {
    this(new ClassPathResource("inventory/operations_config.json"));
  }

  InventoryOperationConfigRegistry(Resource source) {
    try (InputStream configStream = source.getInputStream()) {
      byte[] configBytes = configStream.readAllBytes();
      rawConfigJson = new String(configBytes, StandardCharsets.UTF_8);
      List<InventoryOperationConfig> operations =
          new ObjectMapper().readValue(configBytes, new TypeReference<>() {});
      operationsByKey =
          operations.stream()
              .collect(
                  Collectors.toUnmodifiableMap(InventoryOperationConfig::key, Function.identity()));
    } catch (IOException | IllegalArgumentException | NullPointerException e) {
      throw new IllegalStateException(
          "Could not load the Inventory operation definitions from " + source, e);
    }
    validate(operationsByKey.values(), source);
  }

  /**
   * Rejects a definition the request validator cannot fully interpret, at construction rather than
   * on the first request that happens to exercise it. Binding already covers an unreadable or
   * unparseable file; what it cannot see is a definition that parses yet means something the
   * validator does not implement, and every one of those failures is invisible until a user hits
   * it: an unknown {@code originFields[].type} 500s that operation forever, a dangling content
   * reference silently disables the field's checks, and an uninterpreted {@code inputs[].type}
   * leaves that input's declared constraints unenforced while the definition still advertises them.
   *
   * <p>Every violation is collected and reported together, so a bad config file is one round trip
   * to fix rather than one per boot. Deliberately strict: this is a build-time guard over a file in
   * the repository, and DevDocs/adr/0007 Stage 2 makes these definitions user-editable, at which
   * point the same checks become the save-time validation.
   */
  private static void validate(Collection<InventoryOperationConfig> operations, Resource source) {
    List<String> violations = new ArrayList<>();
    for (InventoryOperationConfig operation : operations) {
      violations.addAll(
          validateOperation(operation).stream()
              .map(problem -> String.format("operation '%s': %s", operation.key(), problem))
              .toList());
    }
    if (!violations.isEmpty()) {
      throw new IllegalStateException(
          "Invalid Inventory operation definitions in "
              + source
              + ": "
              + String.join("; ", violations));
    }
  }

  private static List<String> validateOperation(InventoryOperationConfig operation) {
    List<String> problems = new ArrayList<>();
    if (StringUtils.isBlank(operation.key())) {
      problems.add("key is blank");
    }
    Set<String> inputKeys = new LinkedHashSet<>();
    for (InventoryOperationConfig.Input input : operation.inputs()) {
      if (StringUtils.isBlank(input.key())) {
        problems.add("an input has a blank key");
      } else if (!inputKeys.add(input.key())) {
        problems.add("duplicate input key '" + input.key() + "'");
      }
      if (input.type() != null
          && !InventoryOperationConfig.INTERPRETED_INPUT_TYPES.contains(input.type())) {
        problems.add(
            String.format(
                "input '%s' has type '%s', which no validation branch interprets (known: %s)",
                input.key(), input.type(), InventoryOperationConfig.INTERPRETED_INPUT_TYPES));
      }
      if (input.minCelsius() != null
          && input.maxCelsius() != null
          && input.minCelsius().compareTo(input.maxCelsius()) > 0) {
        problems.add(
            String.format(
                "input '%s' has minCelsius %s above maxCelsius %s",
                input.key(), input.minCelsius(), input.maxCelsius()));
      }
    }

    InventoryOperationConfig.Effect effect = operation.effect();
    // A computed value is written into its own slot, NOT into a declared input: the wizard derives
    // it rather than asking for it (Passage's passageNumber, Destroy's disposedDate). So the names
    // a
    // field's contentFrom may resolve to are the inputs PLUS those slots, while the effect's
    // user-entered sources (nameFrom and friends) must be inputs proper.
    Set<String> computedSlots = new LinkedHashSet<>();
    for (InventoryOperationConfig.Computed computed : effect.computed()) {
      if (StringUtils.isBlank(computed.into())) {
        problems.add("a computed value has a blank 'into'");
      } else if (inputKeys.contains(computed.into())) {
        // It would silently overwrite whatever the user entered under that key.
        problems.add(
            "computed value writes into '" + computed.into() + "', which is also an input");
      } else {
        computedSlots.add(computed.into());
      }
      // Blank-checked before the set lookup: Set.of(...) throws NullPointerException from
      // contains(null), which would abort the whole pass with a bare NPE naming neither the
      // operation nor the problem - the exact failure this validation exists to replace
      // (parallel review, C5).
      if (StringUtils.isBlank(computed.fn())
          || !InventoryOperationConfig.INTERPRETED_COMPUTED_FUNCTIONS.contains(computed.fn())) {
        problems.add(
            String.format(
                "computed function '%s' is unknown (known: %s)",
                computed.fn(), InventoryOperationConfig.INTERPRETED_COMPUTED_FUNCTIONS));
      }
    }
    Set<String> resolvableValues = new LinkedHashSet<>(inputKeys);
    resolvableValues.addAll(computedSlots);

    Map<String, String> inputSources = new LinkedHashMap<>();
    inputSources.put("nameFrom", effect.nameFrom());
    inputSources.put("countFrom", effect.countFrom());
    inputSources.put("amountTakenFrom", effect.amountTakenFrom());
    inputSources.put("eachAmountFrom", effect.eachAmountFrom());
    inputSources.put("storageTempFrom", effect.storageTempFrom());
    inputSources.put("processNameFrom", effect.processNameFrom());
    inputSources.forEach(
        (name, referenced) -> {
          if (referenced != null && !inputKeys.contains(referenced)) {
            problems.add(
                String.format("%s names '%s', which is not a declared input", name, referenced));
          }
        });

    int index = 0;
    for (InventoryOperationConfig.TextField textField : effect.textFields()) {
      problems.addAll(
          validateGeneratedField(
              String.format("textFields[%d]", index++),
              textField.nameKey(),
              textField.contentFrom(),
              resolvableValues));
    }
    index = 0;
    for (InventoryOperationConfig.OriginField originField : effect.originFields()) {
      String path = String.format("originFields[%d]", index++);
      problems.addAll(
          validateGeneratedField(
              path, originField.nameKey(), originField.contentFrom(), resolvableValues));
      if (originField.type() != null) {
        try {
          FieldType.valueOf(originField.type().toUpperCase());
        } catch (IllegalArgumentException unknownType) {
          // The request validator resolves this per request, so an unknown type is a 500 on every
          // request for this operation rather than a boot failure.
          problems.add(String.format("%s has unknown field type '%s'", path, originField.type()));
        }
      }
    }
    index = 0;
    for (InventoryOperationConfig.Link link : effect.links()) {
      if (StringUtils.isBlank(link.fieldNameKey())) {
        problems.add(String.format("links[%d] has a blank fieldNameKey", index));
      }
      if (StringUtils.isBlank(link.relationType())) {
        problems.add(String.format("links[%d] has a blank relationType", index));
      }
      index++;
    }
    // An origin-emptying operation takes the whole origin by definition, so an amount-taken input
    // would be an amount the user chooses that the endpoint then ignores.
    if (effect.emptiesOrigin() && effect.amountTakenFrom() != null) {
      problems.add("emptiesOrigin operations must not also declare amountTakenFrom");
    }
    problems.addAll(validateCountBounds(operation, effect));
    return problems;
  }

  /**
   * The input named by {@code countFrom} must declare bounds inside what the request builder
   * accepts.
   *
   * <p>Without them the builder's own guard is the only thing standing between a client and a
   * subsample count it refuses, and that guard throws {@link IllegalArgumentException}, which the
   * shared advice maps to a 422 carrying the exception's raw English message - untranslated text,
   * echoing the client's input, shown in the wizard's alert. Every configured operation already
   * declares {@code min: 1, max: 100}; this makes the endpoint's 400 depend on the config saying so
   * rather than on it happening to (parallel review).
   */
  private static List<String> validateCountBounds(
      InventoryOperationConfig operation, InventoryOperationConfig.Effect effect) {
    if (effect.countFrom() == null) {
      return List.of();
    }
    InventoryOperationConfig.Input count =
        operation.inputs().stream()
            .filter(input -> effect.countFrom().equals(input.key()))
            .findFirst()
            .orElse(null);
    if (count == null) {
      return List.of(); // already reported as naming an undeclared input
    }
    List<String> problems = new ArrayList<>();
    if (count.min() == null || count.min().compareTo(BigDecimal.ONE) < 0) {
      problems.add(
          String.format(
              "countFrom input '%s' must declare min >= 1, was %s", count.key(), count.min()));
    }
    if (count.max() == null
        || count
                .max()
                .compareTo(BigDecimal.valueOf(InventoryOperationRequestBuilder.MAX_SUBSAMPLES))
            > 0) {
      problems.add(
          String.format(
              "countFrom input '%s' must declare max <= %d, was %s",
              count.key(), InventoryOperationRequestBuilder.MAX_SUBSAMPLES, count.max()));
    }
    return problems;
  }

  private static List<String> validateGeneratedField(
      String path, String nameKey, String contentFrom, Set<String> resolvableValues) {
    List<String> problems = new ArrayList<>();
    if (StringUtils.isBlank(nameKey)) {
      problems.add(path + " has a blank nameKey");
    }
    if (contentFrom == null || !resolvableValues.contains(contentFrom)) {
      problems.add(
          String.format(
              "%s takes content from '%s', which is neither an input nor a computed value",
              path, contentFrom));
    }
    return problems;
  }

  /**
   * The full config file verbatim, for the GET /operations/config endpoint: the frontend renders
   * the wizard from fields (labels, icons, steps) the backend's parsed subset does not bind, so the
   * endpoint must serve the file itself, not a re-serialisation.
   */
  public String rawConfigJson() {
    return rawConfigJson;
  }

  /** The definition for the given operation key (exact match), if one is configured. */
  public Optional<InventoryOperationConfig> get(String operationKey) {
    return Optional.ofNullable(operationKey).map(operationsByKey::get);
  }

  /**
   * All configured operation keys.
   *
   * <p>A TEST SEAM, not live API: nothing in production calls it, and its three callers are the
   * contract tests that assert there is exactly one facade, one golden input set and one registry
   * entry per configured operation. Public rather than package-private only because two of those
   * tests live in other packages. Do not delete it for want of a production caller (parallel
   * review, Q9).
   */
  public Set<String> keys() {
    return operationsByKey.keySet();
  }
}
