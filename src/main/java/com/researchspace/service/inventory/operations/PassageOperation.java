package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;

/**
 * Passage: carries a culture forward a generation. It creates a new sample derived from the origin
 * and stamps it with the next passage number.
 */
@Component
public class PassageOperation extends CreatingOperation<ApiInventoryOperationRequests.Passage> {

  static final String NUMBER_FIELD_KEY = "inventory:operations.passage.numberField";

  /** The highest passage number; a parent already at it cannot be passaged again. */
  static final long MAX_PASSAGE_NUMBER = 9999;

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
    return "inventory:operations.passage.linkFieldName";
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

  @Override
  public void validateOrigins(
      ApiInventoryOperationRequests.Passage request,
      List<OriginState> origins,
      LabelResolver labels,
      Errors errors) {
    OptionalLong current = currentPassageNumber(origins.get(0), labels);
    if (current.isPresent() && current.getAsLong() >= MAX_PASSAGE_NUMBER) {
      errors.rejectValue(
          "origin.globalId",
          "errors.inventory.operation.passageLimitReached",
          new Object[] {String.valueOf(MAX_PASSAGE_NUMBER)},
          null);
    }
  }

  private static String nextPassageNumber(OriginState origin, LabelResolver labels) {
    OptionalLong current = currentPassageNumber(origin, labels);
    return current.isPresent() ? String.valueOf(current.getAsLong() + 1) : FIRST_PASSAGE;
  }

  /** Empty when the parent has no passage number this could count on from, e.g. "abc" or -1. */
  private static OptionalLong currentPassageNumber(OriginState origin, LabelResolver labels) {
    String wanted = labels.resolve(NUMBER_FIELD_KEY).trim().toLowerCase(Locale.ROOT);
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
      return parsed >= 0 ? OptionalLong.of(parsed) : OptionalLong.empty();
    } catch (NumberFormatException notACount) {
      return OptionalLong.empty();
    }
  }
}
