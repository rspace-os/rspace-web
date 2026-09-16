package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.record.BaseRecord;
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
 * Builds the request the operations endpoint executes from an operation definition and the
 * user-supplied input values.
 *
 * <ul>
 *   <li>No aggregate {@code newSample.quantity}: SampleApiManagerImpl reads it only when a
 *       subsample lacks its own quantity, and every subsample built here carries one.
 *   <li>No {@code amountMode}: only the post validator reads it, and a built request never passes
 *       through that validator.
 * </ul>
 */
public final class InventoryOperationRequestBuilder {

  /** Fixed key, not read from config: the documentation link is added by the wizard. */
  public static final String DOCUMENTATION_LINK_KEY = "operations.documentationLink";

  /** Used when a defaulted zero amount has no unit to inherit. */
  private static final int UNSET_UNIT = 0;

  /** JS's Number.MAX_SAFE_INTEGER: the ceiling above which the counter stops incrementing. */
  private static final long MAX_SAFE_INTEGER = 9007199254740991L;

  /** Upper bound enforced by {@link #subSampleCount}; config must keep countFrom within it. */
  static final int MAX_SUBSAMPLES = 100;

  private InventoryOperationRequestBuilder() {}

  /** Resolves an i18n key to a display name, interpolating args as ICU MessageFormat. */
  @FunctionalInterface
  public interface LabelResolver {
    String resolve(String key, Map<String, Object> args);
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

  /** The optional SOP link, if the user chose one. */
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

    /**
     * Per-origin amounts by origin global id. A missing entry takes zero. Ignored when the
     * operation empties its origin, which snapshots the origin's own quantity instead.
     */
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

    boolean takesWholeOrigin = effect.emptiesOrigin();

    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType(operation.key());

    for (Origin origin : origins) {
      ApiInventoryOperationOriginUpdate update = new ApiInventoryOperationOriginUpdate();
      update.setId(origin.id());
      update.setAmountTaken(
          amountTakenFor(origin, perSubsampleAmounts, eachAmountUnit, takesWholeOrigin));
      List<ApiExtraField> originFields = originFields(effect, values, resolveLabel);
      if (!originFields.isEmpty()) {
        update.setExtraFields(originFields);
      }
      request.getOrigins().add(update);
    }

    // A noOutput operation creates no sample; it only acts on its origins.
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

    // Origins can share a name (e.g. pooling); withUniqueFieldNames below deduplicates the
    // resulting field names rather than assuming they're already unique.
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
   * The full origin quantity when the operation empties its origin, otherwise the chosen per-origin
   * amount, defaulting to zero when none was chosen.
   */
  private static ApiQuantityInfo amountTakenFor(
      Origin origin,
      Map<String, ApiQuantityInfo> perSubsampleAmounts,
      Integer eachAmountUnit,
      boolean takesWholeOrigin) {
    if (takesWholeOrigin) {
      return origin.quantity() != null
          ? copy(origin.quantity())
          : new ApiQuantityInfo(
              BigDecimal.ZERO, eachAmountUnit != null ? eachAmountUnit : UNSET_UNIT);
    }
    ApiQuantityInfo chosen = perSubsampleAmounts.get(origin.globalId());
    if (chosen != null) {
      return copy(chosen);
    }
    int fallbackUnit =
        origin.quantity() != null
            ? origin.quantity().getUnitId()
            : (eachAmountUnit != null ? eachAmountUnit : UNSET_UNIT);
    return new ApiQuantityInfo(BigDecimal.ZERO, fallbackUnit);
  }

  /** Custom fields added to each origin subsample itself, not the created sample. */
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
   * Applies computed values in config order onto a copy of the input values, so a later computed
   * value can read an earlier one via an {@code input} arg.
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
   * Matches by definition key first (stable across locale/rewording), falling back to localized
   * name (picks up a user's own hand-created field). Reads only the first origin's parent, so any
   * operation using this must be single-origin.
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

  /** {@code current + 1}, or {@code start} when {@code current} isn't parseable as a count. */
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
   * Suffixes each name in a colliding group to make it unique - a link by the target global id,
   * everything else by an ordinal - since two pooled origins sharing a name previously produced
   * duplicate field names and a guaranteed 400.
   */
  static List<ApiExtraField> withUniqueFieldNames(List<ApiExtraField> fields) {
    Map<String, Integer> occurrences = new HashMap<>();
    for (ApiExtraField field : fields) {
      occurrences.merge(comparable(field.getName()), 1, Integer::sum);
    }
    Set<String> used = new HashSet<>();
    for (ApiExtraField field : fields) {
      String resolved = field.getName();
      String suffix =
          occurrences.get(comparable(resolved)) > 1
                  && field.getType() == ApiExtraField.ExtraFieldTypeEnum.LINK
              ? " (" + field.getLink().getTargetGlobalId() + ")"
              : "";
      String candidate = fit(resolved, suffix.length()) + suffix;
      for (int ordinal = 2; used.contains(comparable(candidate)); ordinal++) {
        String ordinalSuffix = suffix + " (" + ordinal + ")";
        candidate = fit(resolved, ordinalSuffix.length()) + ordinalSuffix;
      }
      used.add(comparable(candidate));
      field.setName(candidate);
    }
    return fields;
  }

  /**
   * Truncates a composed name to leave {@code reserve} characters for the uniqueness suffix.
   * Generated names can interpolate an origin's own name, which may already be at the 255-char
   * column limit; without this bound, an over-long name failed at the INSERT as a 500 after the
   * origins had already been decremented. Truncated rather than rejected because pooling near-limit
   * names must stay possible and only the display name is affected, not the link target. Cutting
   * before the suffix, not after, keeps the suffix that makes the name unique.
   */
  private static String fit(String name, int reserve) {
    int room = Math.max(0, BaseRecord.DEFAULT_VARCHAR_LENGTH - reserve);
    return name.length() <= room ? name : name.substring(0, room).stripTrailing();
  }

  /**
   * Reads the pattern from the {@code inventory:} catalog, falling back to the key itself when
   * missing, and ICU-formats it only when args are supplied - an unconditional format() would
   * mangle a literal apostrophe in an argument-free name, since ICU MessageFormat treats it as an
   * escape character.
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
