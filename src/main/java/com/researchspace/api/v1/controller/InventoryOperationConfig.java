package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * One operation definition from {@code operations_config.json}, reduced to the fields the backend
 * validation interprets (DevDocs/adr/0015). The file is shared verbatim with the frontend (which
 * reads the full shape: labels, icons, wizard steps); everything the backend does not enforce is
 * ignored on binding, so purely presentational config changes cannot break the API. Stage 2
 * (DevDocs/adr/0016) swaps the source of these definitions to user-editable data without changing
 * this shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryOperationConfig(
    String key, boolean requiresMultiple, boolean noOutput, List<Input> inputs, Effect effect) {

  public InventoryOperationConfig {
    inputs = inputs == null ? List.of() : List.copyOf(inputs);
    effect = effect == null ? new Effect(null, null, false, null) : effect;
  }

  /** A wizard input; only the constraints the backend can check server-side are bound. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Input(
      String key, String type, boolean required, BigDecimal minCelsius, BigDecimal maxCelsius) {}

  /** The parts of the operation's effect the backend enforces on the wire format. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Effect(
      String amountTakenFrom, String storageTempFrom, boolean emptiesOrigin, List<Link> links) {
    public Effect {
      links = links == null ? List.of() : List.copyOf(links);
    }
  }

  /** A provenance link the new sample must carry back to each origin. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Link(String relationType) {}
}
