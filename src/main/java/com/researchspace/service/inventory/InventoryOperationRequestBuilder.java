package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryOperationAmountMode;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import org.springframework.context.MessageSource;

/**
 * Builds the request the operations endpoint executes, server-side, from an operation definition
 * plus the values the user typed: a Java port of the wizard's {@code buildOperationRequest.ts} and
 * {@code computedValues.ts} (plan-operations-server-builds.md, M1). It interprets every effect
 * primitive the config vocabulary declares - {@code nameFrom}, {@code countFrom}, {@code
 * eachAmountFrom}, {@code amountTakenFrom}, {@code storageTempFrom}, {@code links[]}, {@code
 * textFields[]}, {@code originFields[]}, {@code computed[]} ({@code increment}, {@code today}) -
 * plus the wizard-level documentation link, and sets each generated field's {@code
 * operationFieldKey} itself.
 *
 * <p>Wired to nothing yet: M3 adds the endpoint path that calls it. Deliberate divergences from the
 * wizard's serialized request, each traced to its consumer:
 *
 * <ul>
 *   <li>No aggregate {@code newSample.quantity}. SampleApiManagerImpl reads it only when a
 *       subsample lacks its own quantity, and every subsample built here carries one.
 *   <li>No {@code amountMode: explicit}. InventoryOperationManagerImpl acts only on {@code ALL}
 *       (the compare-and-swap guard), which IS emitted whenever the whole origin is taken.
 * </ul>
 *
 * <p>Locale: the builder itself is locale-free - generated field names come from the supplied
 * {@link LabelResolver}. {@link #messageSourceResolver} is the production resolver: it reads the
 * same i18next JSON catalogs the wizard uses (served to the backend via JsonMessageSource under the
 * {@code inventory:} namespace) and formats them with ICU named arguments, matching the frontend's
 * i18next-icu semantics. The caller decides the locale; per the M0 design (D1) that is the
 * request's Accept-Language, i.e. {@code LocaleContextHolder.getLocale()} at the endpoint. Only
 * en-US catalogs ship, so JsonMessageSource resolves every locale to the en-US text today; the
 * locale still selects ICU's formatting rules.
 */
public final class InventoryOperationRequestBuilder {

  /**
   * The documentation link is a wizard-level feature rather than a per-operation declaration, so it
   * carries this fixed key. Duplicated from the controller layer's
   * OperationNewSampleValidator.DOCUMENTATION_LINK_KEY, which a service class must not import.
   */
  static final String DOCUMENTATION_LINK_KEY = "operations.documentationLink";

  /** The wizard's UNSET_UNIT marker, used when a defaulted zero amount has no unit to inherit. */
  private static final int UNSET_UNIT = 0;

  /** Number.MAX_SAFE_INTEGER: the wizard's ceiling for a counter that can still be incremented. */
  private static final long MAX_SAFE_INTEGER = 9007199254740991L;

  private static final int MAX_SUBSAMPLES = 100;

  private InventoryOperationRequestBuilder() {}

  /** Resolves a generated-field i18n key to a display name; args may interpolate (ICU). */
  @FunctionalInterface
  public interface LabelResolver {
    String resolve(String key, Map<String, Object> args);
  }

  /** How the amount taken is decided across origins; mirrors the wizard's AmountMode. */
  public enum AmountMode {
    SAME,
    PER_SUBSAMPLE,
    ALL
  }

  /** A field on the origin's parent sample that a computed value may read. */
  public record ParentField(String name, String content, String operationFieldKey) {}

  /** One origin subsample, reduced to what the effect primitives consume. */
  public record Origin(
      Long id,
      String globalId,
      String name,
      ApiQuantityInfo quantity,
      List<ParentField> parentSampleFields) {}

  /** The optional SOP link chosen in the wizard's documentation step. */
  public record DocumentationLink(String fieldName, String targetGlobalId) {}

  @Value
  @Builder
  public static class Params {
    InventoryOperationConfig operation;

    /** Input values by input key: String, Number or ApiQuantityInfo, as the input type dictates. */
    Map<String, Object> values;

    List<Origin> origins;
    LabelResolver resolveLabel;

    /** Template for the new sample; null means an ad-hoc sample. */
    Long templateId;

    DocumentationLink documentationLink;

    /** null defaults to SAME, every single-origin operation's mode. */
    AmountMode amountMode;

    /** Per-origin amounts by origin global id, for PER_SUBSAMPLE mode; ignored otherwise. */
    Map<String, ApiQuantityInfo> perSubsampleAmounts;

    /**
     * The client's "today" for the {@code today} computed function. Client-supplied rather than the
     * server clock, so the disposal date is the user's local date even across timezones.
     */
    LocalDate clientToday;
  }

  public static ApiInventoryOperationPost build(Params params) {
    InventoryOperationConfig operation = params.getOperation();
    InventoryOperationConfig.Effect effect = operation.effect();
    AmountMode amountMode =
        params.getAmountMode() == null ? AmountMode.SAME : params.getAmountMode();
    Map<String, ApiQuantityInfo> perSubsampleAmounts =
        params.getPerSubsampleAmounts() == null ? Map.of() : params.getPerSubsampleAmounts();
    LabelResolver resolveLabel = params.getResolveLabel();
    List<Origin> origins = params.getOrigins();

    Map<String, Object> values = applyComputedValues(operation, params);

    Integer eachAmountUnit = null;
    if (effect.eachAmountFrom() != null
        && values.get(effect.eachAmountFrom()) instanceof ApiQuantityInfo eachAmount) {
      eachAmountUnit = eachAmount.getUnitId();
    }

    boolean takesWholeOrigin = effect.emptiesOrigin() || amountMode == AmountMode.ALL;

    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType(operation.key());

    for (Origin origin : origins) {
      ApiInventoryOperationOriginUpdate update = new ApiInventoryOperationOriginUpdate();
      update.setId(origin.id());
      if (takesWholeOrigin) {
        // The snapshot amount below doubles as a compare-and-swap guard only when the request says
        // it meant "all of it" (InventoryOperationManagerImpl.performOperation).
        update.setAmountMode(ApiInventoryOperationAmountMode.ALL);
      }
      update.setAmountTaken(
          amountTakenFor(
              origin,
              effect,
              values,
              amountMode,
              perSubsampleAmounts,
              eachAmountUnit,
              takesWholeOrigin));
      List<ApiExtraField> originFields = originFields(effect, values, resolveLabel);
      if (!originFields.isEmpty()) {
        update.setExtraFields(originFields);
      }
      request.getOrigins().add(update);
    }

    // A terminal operation (noOutput, e.g. Destroy) creates no sample: it only acts on its origins.
    if (!operation.noOutput()
        && effect.nameFrom() != null
        && effect.countFrom() != null
        && effect.eachAmountFrom() != null) {
      request.setNewSample(newSample(effect, values, origins, resolveLabel, params));
    }
    return request;
  }

  private static ApiSampleWithFullSubSamples newSample(
      InventoryOperationConfig.Effect effect,
      Map<String, Object> values,
      List<Origin> origins,
      LabelResolver resolveLabel,
      Params params) {
    int count = subSampleCount(values.get(effect.countFrom()));
    ApiQuantityInfo eachAmount = (ApiQuantityInfo) values.get(effect.eachAmountFrom());

    // Provenance links point back to each origin; the display name may interpolate inputs
    // (e.g. {processName}) and the origin's own name as {originName}. Each link spec fans out to
    // one link per origin. Uniqueness of the resolved names is enforced by withUniqueFieldNames,
    // not assumed (two pooled subsamples may share a name).
    List<ApiExtraField> fields = new ArrayList<>();
    for (InventoryOperationConfig.Link spec : effect.links()) {
      for (Origin origin : origins) {
        Map<String, Object> args = new HashMap<>(values);
        args.put("originName", origin.name());
        fields.add(
            linkField(
                resolveLabel.resolve(spec.fieldNameKey(), args),
                spec.fieldNameKey(),
                spec.relationType(),
                origin.globalId()));
      }
    }
    if (params.getDocumentationLink() != null) {
      fields.add(
          linkField(
              params.getDocumentationLink().fieldName(),
              DOCUMENTATION_LINK_KEY,
              "IsDocumentedBy",
              params.getDocumentationLink().targetGlobalId()));
    }
    for (InventoryOperationConfig.TextField spec : effect.textFields()) {
      ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.TEXT);
      field.setName(resolveLabel.resolve(spec.nameKey(), Map.of()));
      field.setNewFieldRequest(true);
      field.setOperationFieldKey(spec.nameKey());
      field.setContent(contentOf(values, spec.contentFrom()));
      fields.add(field);
    }

    ApiSampleWithFullSubSamples sample =
        new ApiSampleWithFullSubSamples(String.valueOf(values.get(effect.nameFrom())));
    sample.setTemplateId(params.getTemplateId());
    sample.getExtraFields().addAll(withUniqueFieldNames(fields));
    // The process links belong on the created sample, not on the subsamples it creates, so each
    // subsample carries only its quantity.
    for (int i = 0; i < count; i++) {
      ApiSubSample subSample = new ApiSubSample();
      subSample.setQuantity(copy(eachAmount));
      sample.getSubSamples().add(subSample);
    }
    if (effect.storageTempFrom() != null
        && values.get(effect.storageTempFrom()) instanceof ApiQuantityInfo temp) {
      sample.setStorageTempMin(copy(temp));
      sample.setStorageTempMax(copy(temp));
    }
    return sample;
  }

  /**
   * The amount to take from a given origin, exactly as the wizard decides it: an origin-emptying
   * operation and the runtime "take all" mode snapshot the origin's own full quantity;
   * PER_SUBSAMPLE takes the amount chosen for this origin (zero when none was); otherwise the
   * configured shared amount, or a zero no-op decrement for an operation with no amountTakenFrom
   * (Passage).
   */
  private static ApiQuantityInfo amountTakenFor(
      Origin origin,
      InventoryOperationConfig.Effect effect,
      Map<String, Object> values,
      AmountMode amountMode,
      Map<String, ApiQuantityInfo> perSubsampleAmounts,
      Integer eachAmountUnit,
      boolean takesWholeOrigin) {
    int fallbackUnit =
        origin.quantity() != null
            ? origin.quantity().getUnitId()
            : (eachAmountUnit != null ? eachAmountUnit : UNSET_UNIT);
    if (takesWholeOrigin) {
      return origin.quantity() != null
          ? copy(origin.quantity())
          : new ApiQuantityInfo(
              BigDecimal.ZERO, eachAmountUnit != null ? eachAmountUnit : UNSET_UNIT);
    }
    if (amountMode == AmountMode.PER_SUBSAMPLE) {
      ApiQuantityInfo chosen = perSubsampleAmounts.get(origin.globalId());
      return chosen != null ? copy(chosen) : new ApiQuantityInfo(BigDecimal.ZERO, fallbackUnit);
    }
    if (effect.amountTakenFrom() != null) {
      return copy((ApiQuantityInfo) values.get(effect.amountTakenFrom()));
    }
    return new ApiQuantityInfo(BigDecimal.ZERO, fallbackUnit);
  }

  /** Custom fields added to each origin subsample itself (Destroy's disposed date). */
  private static List<ApiExtraField> originFields(
      InventoryOperationConfig.Effect effect,
      Map<String, Object> values,
      LabelResolver resolveLabel) {
    List<ApiExtraField> fields = new ArrayList<>();
    for (InventoryOperationConfig.OriginField spec : effect.originFields()) {
      String type = spec.type() == null ? "text" : spec.type();
      ApiExtraField field =
          new ApiExtraField(
              ApiExtraField.ExtraFieldTypeEnum.valueOf(type.toUpperCase(Locale.ROOT)));
      field.setName(resolveLabel.resolve(spec.nameKey(), Map.of()));
      field.setNewFieldRequest(true);
      field.setOperationFieldKey(spec.nameKey());
      field.setContent(contentOf(values, spec.contentFrom()));
      fields.add(field);
    }
    return fields;
  }

  /**
   * Applies the operation's computed values in config order onto a copy of the input values, so a
   * later computed value can read an earlier one via an {@code input} arg. The registry guarantees
   * every {@code fn} is interpreted.
   */
  private static Map<String, Object> applyComputedValues(
      InventoryOperationConfig operation, Params params) {
    Map<String, Object> values = new LinkedHashMap<>(params.getValues());
    for (InventoryOperationConfig.Computed computed : operation.effect().computed()) {
      Map<String, Object> args = new HashMap<>();
      for (Map.Entry<String, InventoryOperationConfig.ArgSource> entry :
          computed.args().entrySet()) {
        args.put(entry.getKey(), resolveArg(entry.getValue(), params, values));
      }
      Object result =
          switch (computed.fn()) {
            case "increment" -> increment(args.get("current"), args.get("start"));
            case "today" -> params.getClientToday().toString();
            default ->
                throw new IllegalStateException("uninterpreted computed function " + computed.fn());
          };
      values.put(computed.into(), result);
    }
    return values;
  }

  private static Object resolveArg(
      InventoryOperationConfig.ArgSource source, Params params, Map<String, Object> values) {
    if (source.parentSampleField() != null) {
      return parentFieldValue(params, source.parentSampleField());
    }
    if (source.constant() != null) {
      return source.constant();
    }
    return source.input() == null ? null : values.get(source.input());
  }

  /**
   * The content of the parent-sample field a {@code parentSampleField} arg refers to, matched by
   * definition KEY first and by localized name second - the key keeps a lineage intact across
   * locales and rewordings; the name fallback picks up a user's own hand-created field (both
   * deliberate, see computedValues.ts). A computed value reads the FIRST origin's parent: every
   * operation that declares one is single-origin.
   */
  private static Object parentFieldValue(Params params, String key) {
    List<ParentField> fields =
        params.getOrigins().isEmpty() || params.getOrigins().get(0).parentSampleFields() == null
            ? List.of()
            : params.getOrigins().get(0).parentSampleFields();
    String wanted = params.getResolveLabel().resolve(key, Map.of()).trim().toLowerCase(Locale.ROOT);
    return fields.stream()
        .filter(field -> key.equals(field.operationFieldKey()))
        .findFirst()
        .or(
            () ->
                fields.stream()
                    .filter(
                        field ->
                            field.name() != null
                                && field.name().trim().toLowerCase(Locale.ROOT).equals(wanted))
                    .findFirst())
        .<Object>map(ParentField::content)
        .orElse(null);
  }

  /**
   * A running counter: {@code current + 1}, or {@code start} when {@code current} is not a count to
   * carry on from (see operationFunctions.ts).
   */
  // ponytail: Long.parseLong after trim, not JS Number(); exotic contents JS would coerce
  // ("1e3", "0x10", "") restart from start instead. A passage number is never written that way.
  private static Object increment(Object current, Object start) {
    Object startValue = start instanceof BigDecimal decimal ? decimal.longValue() : start;
    try {
      long n = Long.parseLong(String.valueOf(current).trim());
      return (n >= 0 && n < MAX_SAFE_INTEGER) ? n + 1 : startValue;
    } catch (NumberFormatException notACount) {
      return startValue;
    }
  }

  /**
   * Makes every generated field name unique, the way the backend judges uniqueness (trimmed,
   * case-insensitive): every member of a colliding group is suffixed, a link by the global id it
   * targets, anything else by an ordinal. Port of the wizard's withUniqueFieldNames (Codex review,
   * PR #1090: two pooled origins sharing a name produced two "Pooled from: X" fields and a
   * guaranteed 400).
   */
  static List<ApiExtraField> withUniqueFieldNames(List<ApiExtraField> fields) {
    Map<String, Integer> occurrences = new HashMap<>();
    for (ApiExtraField field : fields) {
      occurrences.merge(comparable(field.getName()), 1, Integer::sum);
    }
    Set<String> used = new HashSet<>();
    for (ApiExtraField field : fields) {
      String base =
          occurrences.get(comparable(field.getName())) > 1
                  && field.getType() == ApiExtraField.ExtraFieldTypeEnum.LINK
              ? field.getName() + " (" + field.getLink().getTargetGlobalId() + ")"
              : field.getName();
      String candidate = base;
      for (int ordinal = 2; used.contains(comparable(candidate)); ordinal++) {
        candidate = base + " (" + ordinal + ")";
      }
      used.add(comparable(candidate));
      field.setName(candidate);
    }
    return fields;
  }

  /**
   * The production LabelResolver: raw pattern from the shared i18next catalogs (namespace {@code
   * inventory:}, loaded by JsonMessageSource), formatted with ICU named arguments to match the
   * frontend's i18next-icu. A missing key resolves to the key itself, as the wizard's t() would. A
   * pattern is only ICU-formatted when arguments are supplied, so a literal apostrophe in an
   * argument-free name survives.
   */
  public static LabelResolver messageSourceResolver(MessageSource messages, Locale locale) {
    return (key, args) -> {
      String pattern = messages.getMessage("inventory:" + key, null, "inventory:" + key, locale);
      if (args == null || args.isEmpty()) {
        return pattern;
      }
      return new com.ibm.icu.text.MessageFormat(pattern, locale).format(args);
    };
  }

  private static int subSampleCount(Object value) {
    try {
      long count = Long.parseLong(String.valueOf(value).trim());
      if (count >= 1 && count <= MAX_SUBSAMPLES) {
        return (int) count;
      }
    } catch (NumberFormatException notACount) {
      // fall through
    }
    throw new IllegalArgumentException("Invalid subsample count " + value);
  }

  private static String contentOf(Map<String, Object> values, String key) {
    Object value = values.get(key);
    return value == null ? "" : String.valueOf(value);
  }

  private static ApiQuantityInfo copy(ApiQuantityInfo quantity) {
    return new ApiQuantityInfo(quantity.getNumericValue(), quantity.getUnitId());
  }

  private static ApiExtraField linkField(
      String name, String fieldKey, String relationType, String targetGlobalId) {
    ApiExtraField field = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.LINK);
    field.setName(name);
    field.setNewFieldRequest(true);
    field.setOperationFieldKey(fieldKey);
    ApiInventoryLink link = new ApiInventoryLink();
    link.setRelationType(relationType);
    link.setTargetGlobalId(targetGlobalId);
    field.setLink(link);
    return field;
  }

  private static String comparable(String name) {
    return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
  }
}
