package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Passage: carries a culture forward a generation. It creates a new sample derived from the origin
 * and stamps it with the next passage number, but takes nothing from the origin.
 */
@Component
public class PassageOperation extends CreatingOperation<ApiInventoryOperationRequests.Passage> {

  static final String NUMBER_FIELD_KEY = "operations.passage.numberField";

  /** JS's Number.MAX_SAFE_INTEGER: the ceiling above which the counter restarts. */
  private static final long MAX_SAFE_INTEGER = 9007199254740991L;

  private static final String FIRST_PASSAGE = "1";

  @Override
  public String key() {
    return "passage";
  }

  @Override
  public boolean takesAmount(ApiInventoryOperationRequests.Passage request) {
    return false;
  }

  @Override
  protected String linkRelation() {
    return "IsDerivedFrom";
  }

  @Override
  protected String linkFieldNameKey() {
    return "operations.passage.linkFieldName";
  }

  @Override
  protected ApiQuantityInfo amountTakenFrom(
      ApiInventoryOperationRequests.Passage request, OriginState origin, int index) {
    return Amounts.noneFrom(origin, request.getEachAmount());
  }

  @Override
  protected List<ApiExtraField> textFields(
      ApiInventoryOperationRequests.Passage request,
      List<OriginState> origins,
      LabelResolver labels) {
    return List.of(
        OperationFieldNames.text(
            labels.resolve(NUMBER_FIELD_KEY),
            NUMBER_FIELD_KEY,
            nextPassageNumber(origins.get(0), labels)));
  }

  /**
   * The parent's passage number plus one, or 1 when it has none that reads as a count.
   *
   * <p>Matched by operation key first (stable across locale and rewording), falling back to the
   * localized name, which picks up a field the user created by hand.
   */
  private static String nextPassageNumber(OriginState origin, LabelResolver labels) {
    String wanted = labels.resolve(NUMBER_FIELD_KEY, Map.of()).trim().toLowerCase(Locale.ROOT);
    String current =
        origin.parentSampleFields().stream()
            .filter(field -> NUMBER_FIELD_KEY.equals(field.operationFieldKey()))
            .findFirst()
            .or(
                () ->
                    origin.parentSampleFields().stream()
                        .filter(
                            field ->
                                field.name() != null
                                    && field.name().trim().toLowerCase(Locale.ROOT).equals(wanted))
                        .findFirst())
            .map(OriginState.ParentField::content)
            .orElse(null);
    // ponytail: Long.parseLong after trim, not JS Number(); exotic contents JS would coerce
    // ("1e3", "0x10", "") restart from 1 instead. A passage number is never written that way.
    try {
      long parsed = Long.parseLong(String.valueOf(current).trim());
      return (parsed >= 0 && parsed < MAX_SAFE_INTEGER)
          ? String.valueOf(parsed + 1)
          : FIRST_PASSAGE;
    } catch (NumberFormatException notACount) {
      return FIRST_PASSAGE;
    }
  }
}
