package com.researchspace.service.inventory.operations;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import org.jsoup.Jsoup;
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
        OperationFieldNames.number(
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
    String text = passageNumberText(origins.get(0), labels).orElse("");
    OptionalLong current;
    try {
      current = parsePassageNumber(text);
    } catch (NumberFormatException unreadable) {
      errors.rejectValue(
          "origin.globalId",
          "errors.inventory.operation.passageNumberUnreadable",
          new Object[] {text},
          null);
      return;
    }
    if (current.isPresent() && current.getAsLong() >= MAX_PASSAGE_NUMBER) {
      errors.rejectValue(
          "origin.globalId",
          "errors.inventory.operation.passageLimitReached",
          new Object[] {String.valueOf(MAX_PASSAGE_NUMBER)},
          null);
    }
  }

  /** Only called after {@link #validateOrigins} passed, so the number is readable. */
  private static String nextPassageNumber(OriginState origin, LabelResolver labels) {
    String text = passageNumberText(origin, labels).orElse("");
    try {
      OptionalLong current = parsePassageNumber(text);
      return current.isPresent() ? String.valueOf(current.getAsLong() + 1) : FIRST_PASSAGE;
    } catch (NumberFormatException unreadable) {
      throw new IllegalStateException("Unvalidated passage number: " + text, unreadable);
    }
  }

  /** The parent's passage number as plain text, or empty when it has none. */
  private static Optional<String> passageNumberText(OriginState origin, LabelResolver labels) {
    String wanted = labels.resolve(NUMBER_FIELD_KEY).trim().toLowerCase(Locale.ROOT);
    return origin.parentSampleFields().stream()
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
        // A number edited in the UI is saved as HTML ("<p>9998</p>"), so the markup is dropped.
        .map(content -> Jsoup.parse(content).text().trim());
  }

  /**
   * Empty when blank. Throws {@link NumberFormatException} for anything but a whole number from 0,
   * so a bad value is refused instead of restarting the count at 1.
   */
  private static OptionalLong parsePassageNumber(String text) {
    if (text.isBlank()) {
      return OptionalLong.empty();
    }
    long parsed;
    try {
      parsed = new BigDecimal(text).longValueExact();
    } catch (ArithmeticException fractionalOrTooLarge) {
      throw new NumberFormatException(text);
    }
    if (parsed < 0) {
      throw new NumberFormatException(text);
    }
    return OptionalLong.of(parsed);
  }
}
