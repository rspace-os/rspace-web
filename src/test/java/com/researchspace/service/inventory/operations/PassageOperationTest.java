package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.KEYS;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.requestOrigin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PassageOperationTest {

  private static final PassageOperation PASSAGE = new PassageOperation();
  private static final LocalDate TODAY = LocalDate.parse("2026-08-20");
  private static final String NUMBER_FIELD = "operations.passage.numberField";

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
  void matchesTheParentFieldByItsDisplayNameWhenItCarriesNoOperationKey() {
    assertEquals(
        "3",
        passageNumber(
            originWithParentFields(List.of(new OriginState.ParentField(NUMBER_FIELD, "2", null)))),
        "a user's own hand-created field has no key, so it is matched by its resolved name");
  }

  @Test
  void startsAtOneWhenTheParentHasNoUsableNumber() {
    assertEquals("1", passageNumber(originWithParentFields(List.of())));
    assertEquals(
        "1",
        passageNumber(
            originWithParentFields(
                List.of(new OriginState.ParentField("Passage number", "n/a", NUMBER_FIELD)))));
  }

  @Test
  void restartsAtOneRatherThanCarryForwardANumberItCannotIncrementSafely() {
    // A negative is not a passage count, and one at the JS safe-integer ceiling could not survive
    // the round trip through the wizard, so both restart rather than store a number the next
    // passage would read back wrong.
    assertEquals(
        "1",
        passageNumber(
            originWithParentFields(
                List.of(new OriginState.ParentField("Passage number", "-1", NUMBER_FIELD)))));
    assertEquals(
        "1",
        passageNumber(
            originWithParentFields(
                List.of(
                    new OriginState.ParentField(
                        "Passage number", "9007199254740991", NUMBER_FIELD)))));
    assertEquals(
        "9007199254740990",
        passageNumber(
            originWithParentFields(
                List.of(
                    new OriginState.ParentField(
                        "Passage number", "9007199254740989", NUMBER_FIELD)))),
        "just below the ceiling still increments");
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
    assertEquals("operations.passage.linkFieldName", link.getOperationFieldKey());
  }
}
