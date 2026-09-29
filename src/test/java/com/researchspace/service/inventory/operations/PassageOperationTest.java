package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.KEYS;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.requestOrigin;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

class PassageOperationTest {

  private static final PassageOperation PASSAGE = new PassageOperation();
  private static final LocalDate TODAY = LocalDate.parse("2026-08-20");
  private static final String NUMBER_FIELD = "inventory:operations.passage.numberField";
  private static final String UNREADABLE = "errors.inventory.operation.passageNumberUnreadable";
  private static final String LIMIT = "errors.inventory.operation.passageLimitReached";

  private static ApiInventoryOperationRequests.Passage request() {
    ApiInventoryOperationRequests.Passage request = new ApiInventoryOperationRequests.Passage();
    request.setSampleName("HeLa p4");
    request.setCount(new BigDecimal("2"));
    request.setEachAmount(millilitres("5"));
    request.setOrigin(requestOrigin(100, null));
    return request;
  }

  private static OriginState originWithParentFields(List<OriginState.ParentField> fields) {
    return new OriginState(100L, "SS100", "flask", millilitres("10"), fields);
  }

  private static String passageNumber(OriginState origin) {
    ApiInventoryOperationPost built = PASSAGE.build(request(), List.of(origin), KEYS, TODAY);
    return OperationTestFixtures.fieldNamed(built.getNewSample().getExtraFields(), NUMBER_FIELD)
        .getContent();
  }

  @Test
  void leavesTheOriginUntouched() {
    ApiInventoryOperationPost built =
        PASSAGE.build(request(), List.of(originWithParentFields(List.of())), KEYS, TODAY);

    assertEquals(BigDecimal.ZERO, built.getOrigins().get(0).getAmountTaken().getNumericValue());
    assertEquals(
        millilitres("10").getUnitId(),
        built.getOrigins().get(0).getAmountTaken().getUnitId(),
        "the no-op decrement keeps the origin's own unit");
    assertFalse(built.isEmptiesOrigin());
  }

  @Test
  void incrementsTheParentsPassageNumber() {
    assertEquals(
        "5",
        passageNumber(
            originWithParentFields(
                List.of(new OriginState.ParentField("Passage number", "4", NUMBER_FIELD)))));
  }

  @Test
  void recordsThePassageNumberInANumberField() {
    ApiInventoryOperationPost built =
        PASSAGE.build(request(), List.of(parentAtPassage("4")), KEYS, TODAY);

    assertEquals(
        ApiExtraField.ExtraFieldTypeEnum.NUMBER,
        OperationTestFixtures.fieldNamed(built.getNewSample().getExtraFields(), NUMBER_FIELD)
            .getType());
  }

  @Test
  void matchesTheParentFieldByItsDisplayNameWhenItCarriesNoOperationKey() {
    assertEquals(
        "3",
        passageNumber(
            originWithParentFields(List.of(new OriginState.ParentField(NUMBER_FIELD, "2", null)))),
        "a user's own hand-created field has no key, so it is matched by its resolved name");
  }

  private static OriginState parentAtPassage(String number) {
    return originWithParentFields(
        List.of(new OriginState.ParentField("Passage number", number, NUMBER_FIELD)));
  }

  private static BeanPropertyBindingResult originErrors(OriginState origin) {
    ApiInventoryOperationRequests.Passage request = request();
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request, "request");
    PASSAGE.validateOrigins(request, List.of(origin), KEYS, errors);
    return errors;
  }

  @Test
  void aParentOneBelowThePassageLimitIsPassagedToTheLimit() {
    assertFalse(originErrors(parentAtPassage("9998")).hasErrors());
    assertEquals("9999", passageNumber(parentAtPassage("9998")));
  }

  @Test
  void countsOnFromANumberTheUiSavedAsHtml() {
    assertEquals("9999", passageNumber(parentAtPassage("<p>9998</p>")));
  }

  @Test
  void refusesToPassageAParentAtOrAboveThePassageLimit() {
    for (String number : List.of("9999", "10000", "<p>9999</p>")) {
      FieldError refusal = originErrors(parentAtPassage(number)).getFieldError("origin.globalId");
      assertNotNull(refusal, number);
      assertEquals("errors.inventory.operation.passageLimitReached", refusal.getCode());
    }
  }

  private static void assertRefused(String code, Object shownAs, OriginState origin) {
    FieldError refusal = originErrors(origin).getFieldError("origin.globalId");
    assertNotNull(refusal, String.valueOf(shownAs));
    assertEquals(code, refusal.getCode(), String.valueOf(shownAs));
    assertArrayEquals(new Object[] {shownAs}, refusal.getArguments());
  }

  private static void assertUnreadable(String shownAs, OriginState origin) {
    assertRefused(UNREADABLE, shownAs, origin);
  }

  private static void assertCountsOnTo(String next, OriginState origin) {
    assertFalse(originErrors(origin).hasErrors(), next);
    assertEquals(next, passageNumber(origin));
  }

  @Nested
  class AKeyedNumberField {

    @ParameterizedTest
    @CsvSource({"9998.0, 9999", "+5, 6", "05, 6", "1e3, 1001", "'', 1"})
    void countsOnFromAWholeNumberInAnyNotation(String number, String next) {
      assertCountsOnTo(next, parentAtPassage(number));
    }

    @ParameterizedTest
    @ValueSource(strings = {"9999.0", "1E+4"})
    void refusesAWholeNumberAtTheLimitInAnyNotation(String number) {
      assertRefused(
          LIMIT, String.valueOf(PassageOperation.MAX_PASSAGE_NUMBER), parentAtPassage(number));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5.5", "10000.5", "12345678901234567890", "-1"})
    void refusesAnythingButAWholeNumberFromZeroAndNamesIt(String number) {
      assertUnreadable(number, parentAtPassage(number));
    }
  }

  @Nested
  class AKeyedTextFieldTheUiSavedAsHtml {

    @ParameterizedTest
    @ValueSource(
        strings = {"<p>12&nbsp;</p>", "<p> 12 </p>", "<p><strong>12</strong></p>", "  12\n"})
    void countsOnFromTheNumberInsideTheMarkup(String number) {
      assertCountsOnTo("13", parentAtPassage(number));
    }

    @Test
    void refusesTwoParagraphsAndText() {
      assertUnreadable("12 13", parentAtPassage("<p>12</p><p>13</p>"));
      assertUnreadable("abc", parentAtPassage("abc"));
    }
  }

  @Nested
  class AnUnkeyedTemplateFieldNamedPassageNumber {

    private final LabelResolver named =
        (key, args) -> NUMBER_FIELD.equals(key) ? "Passage number" : key;

    private String passageNumberNamed(OriginState origin) {
      BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request(), "request");
      PASSAGE.validateOrigins(request(), List.of(origin), named, errors);
      assertFalse(errors.hasErrors());
      return OperationTestFixtures.fieldNamed(
              PASSAGE
                  .build(request(), List.of(origin), named, TODAY)
                  .getNewSample()
                  .getExtraFields(),
              "Passage number")
          .getContent();
    }

    @Test
    void losesToAKeyedField() {
      assertEquals(
          "8",
          passageNumberNamed(
              originWithParentFields(
                  List.of(
                      new OriginState.ParentField("Passage number", "abc", null),
                      new OriginState.ParentField("Passage number", "7", NUMBER_FIELD)))));
    }

    @Test
    void theFirstMatchWinsWhateverItsCaseAndSpacing() {
      assertEquals(
          "4",
          passageNumberNamed(
              originWithParentFields(
                  List.of(
                      new OriginState.ParentField("  PASSAGE NUMBER ", "3", null),
                      new OriginState.ParentField("Passage number", "abc", null)))));
    }

    @Test
    void refusesADecimal() {
      OriginState origin =
          originWithParentFields(
              List.of(new OriginState.ParentField("Passage number", "5.5", null)));
      BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request(), "request");
      PASSAGE.validateOrigins(request(), List.of(origin), named, errors);
      assertEquals(UNREADABLE, errors.getFieldError("origin.globalId").getCode());
    }
  }

  @Test
  void startsAtOneWhenThereIsNoPassageNumber() {
    assertCountsOnTo("1", originWithParentFields(List.of()));
    assertCountsOnTo("1", parentAtPassage(null));
  }

  @Test
  void neverBuildsFromAnUnreadableNumber() {
    assertUnreadable("abc", parentAtPassage("abc"));
    assertThrows(IllegalStateException.class, () -> passageNumber(parentAtPassage("abc")));
  }

  @Test
  void derivesTheCreatedSampleFromItsOrigin() {
    ApiExtraField link =
        PASSAGE
            .build(request(), List.of(originWithParentFields(List.of())), KEYS, TODAY)
            .getNewSample()
            .getExtraFields()
            .get(0);

    assertEquals("IsDerivedFrom", link.getLink().getRelationType());
    assertEquals("inventory:operations.passage.linkFieldName", link.getOperationFieldKey());
  }
}
