package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * The backend's registry of Inventory operation definitions, parsed once at startup from the
 * verbatim classpath copy of the frontend's {@code operations_config.json} (DevDocs/adr/0015; a
 * drift test keeps the two copies byte-identical). Construction fails fast on a missing or
 * unparseable file so a bad build cannot boot with an unvalidated public endpoint. Stage 2
 * (DevDocs/adr/0016) replaces this source with user-editable definitions without changing
 * consumers.
 */
@Component
public class InventoryOperationConfigRegistry {

  private final Map<String, InventoryOperationConfig> operationsByKey;

  public InventoryOperationConfigRegistry() {
    this(new ClassPathResource("inventory/operations_config.json"));
  }

  InventoryOperationConfigRegistry(Resource source) {
    try (InputStream configStream = source.getInputStream()) {
      List<InventoryOperationConfig> operations =
          new ObjectMapper().readValue(configStream, new TypeReference<>() {});
      operationsByKey =
          operations.stream()
              .collect(
                  Collectors.toUnmodifiableMap(InventoryOperationConfig::key, Function.identity()));
    } catch (IOException | IllegalArgumentException | NullPointerException e) {
      throw new IllegalStateException(
          "Could not load the Inventory operation definitions from " + source, e);
    }
  }

  /** The definition for the given operation key (exact match), if one is configured. */
  public Optional<InventoryOperationConfig> get(String operationKey) {
    return Optional.ofNullable(operationKey).map(operationsByKey::get);
  }

  /** All configured operation keys. */
  public Set<String> keys() {
    return operationsByKey.keySet();
  }
}
