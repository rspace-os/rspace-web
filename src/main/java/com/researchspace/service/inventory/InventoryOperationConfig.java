package com.researchspace.service.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * One operation definition from {@code operations_config.json}, reduced to the fields the backend
 * validation interprets (DevDocs/adr/0007). The wizard fetches the same file verbatim from GET
 * /operations/config and reads the full shape (labels, icons, wizard steps); everything the backend
 * does not enforce is ignored on binding, so purely presentational config changes cannot break the
 * API. Unknown properties are ignored on the CONFIG only: strictness applies to the request, which
 * is whitelisted against this definition. Stage 2 (DevDocs/adr/0007) swaps the source of these
 * definitions to user-editable data without changing this shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryOperationConfig(
    String key, boolean requiresMultiple, boolean noOutput, List<Input> inputs, Effect effect) {

  public InventoryOperationConfig {
    inputs = inputs == null ? List.of() : List.copyOf(inputs);
    effect = effect == null ? Effect.EMPTY : effect;
  }

  /**
   * The {@code inputs[].type} values the wizard renders and the config may declare.
   *
   * <p>Deliberately NOT described as "types the backend interprets": the request validator branches
   * on the type in one place only ({@code "temperature"}, for the configured Celsius bounds), and
   * the others are purely the wizard's business. The set exists so a typo ({@code "quantitiy"})
   * fails the build rather than silently rendering nothing, not because the backend needs a branch
   * per type.
   *
   * <p>That distinction matters because the registry rejects anything outside this set at
   * construction: describing it as a backend concern invited the conclusion that a new wizard-only
   * type needed no entry here, and adding one without it would boot-fail the whole application
   * (parallel review, I16). Add the type here when the wizard learns to render it.
   */
  public static final Set<String> INTERPRETED_INPUT_TYPES =
      Set.of("text", "integer", "quantity", "temperature");

  /**
   * The {@code computed[].fn} names the request validator knows the output shape of. An unknown
   * function silently skips the content check, so a field the wizard computes would be accepted
   * with any content at all.
   */
  public static final Set<String> INTERPRETED_COMPUTED_FUNCTIONS = Set.of("increment", "today");

  /** A wizard input; only the constraints the backend can check server-side are bound. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Input(
      String key,
      String type,
      boolean required,
      BigDecimal min,
      BigDecimal minCelsius,
      BigDecimal maxCelsius) {}

  /** The parts of the operation's effect the backend enforces on the wire format. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Effect(
      String nameFrom,
      String countFrom,
      String amountTakenFrom,
      String eachAmountFrom,
      String storageTempFrom,
      String processNameFrom,
      boolean emptiesOrigin,
      List<Computed> computed,
      List<Link> links,
      List<TextField> textFields,
      List<OriginField> originFields) {

    static final Effect EMPTY =
        new Effect(null, null, null, null, null, null, false, null, null, null, null);

    public Effect {
      computed = computed == null ? List.of() : List.copyOf(computed);
      links = links == null ? List.of() : List.copyOf(links);
      textFields = textFields == null ? List.of() : List.copyOf(textFields);
      originFields = originFields == null ? List.of() : List.copyOf(originFields);
    }
  }

  /**
   * A value the wizard derives client-side and writes into the named input ({@code into}), which
   * the declared text/origin fields then consume. The backend does not recompute it (the inputs are
   * not on the wire and {@code today} would fight client/server timezones, DevDocs/adr/0007); it
   * checks the shape the function promises.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Computed(String fn, String into) {}

  /** A provenance link the new sample must carry back to each origin. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Link(String relationType, String fieldNameKey) {}

  /** A text field the operation adds to the sample it creates (e.g. Cryopreserve's cryomedium). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TextField(String nameKey, String contentFrom) {}

  /** A field the operation adds to each origin subsample (Destroy's disposed date). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record OriginField(String nameKey, String contentFrom, String type) {}
}
