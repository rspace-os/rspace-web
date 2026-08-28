package com.researchspace.service.inventory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

  /** All configured operation keys. */
  public Set<String> keys() {
    return operationsByKey.keySet();
  }
}
