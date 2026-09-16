package com.researchspace.service.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One operation definition from {@code operations_config.json}, reduced to the fields the backend
 * validates. Unknown properties are ignored deliberately: strictness applies to the request, which
 * is whitelisted against this definition, not to this config shape.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InventoryOperationConfig(
    String key, boolean requiresMultiple, boolean noOutput, List<Input> inputs, Effect effect) {

  public InventoryOperationConfig {
    inputs = inputs == null ? List.of() : List.copyOf(inputs);
    effect = effect == null ? Effect.EMPTY : effect;
  }

  /**
   * The {@code inputs[].type} values the config may declare; only {@code "temperature"} is actually
   * interpreted server-side (for its Celsius bounds), the rest are the wizard's business. The
   * registry rejects any type outside this set at construction, so add a new wizard-only type here
   * too or the application fails to boot.
   */
  public static final Set<String> INTERPRETED_INPUT_TYPES =
      Set.of("text", "integer", "quantity", "temperature");

  /**
   * The {@code computed[].fn} names the request validator knows the output shape of. An unknown
   * function silently skips the content check, so a field the wizard computes would be accepted
   * with any content at all.
   */
  public static final Set<String> INTERPRETED_COMPUTED_FUNCTIONS = Set.of("increment", "today");

  /**
   * A wizard input's server-side constraints, plus {@code default}: the value substituted for an
   * absent optional input. Bound as the raw JSON value and typed by {@link
   * InventoryOperationInputValidator#withDefaults} against the input's type.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Input(
      String key,
      String type,
      boolean required,
      BigDecimal min,
      BigDecimal max,
      BigDecimal minCelsius,
      BigDecimal maxCelsius,
      @JsonProperty("default") Object defaultValue) {}

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
   * A value the wizard derives client-side and writes into the named input ({@code into}). The
   * backend does not recompute it (the inputs are not on the wire, and {@code today} would fight
   * client/server timezones); it only checks the shape the function promises.
   *
   * <p>The request validator ignores {@code args}; {@link InventoryOperationRequestBuilder}
   * interprets it when building the request server-side.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Computed(String fn, String into, Map<String, ArgSource> args) {
    public Computed {
      args = args == null ? Map.of() : Map.copyOf(args);
    }

    public Computed(String fn, String into) {
      this(fn, into, null);
    }
  }

  /**
   * One computed-value argument source; exactly one of the three is set in config: a field on the
   * origin's parent sample (matched by this key, then by its localized name), a wizard input's
   * current value, or a literal.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ArgSource(String parentSampleField, String input, BigDecimal constant) {}

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
