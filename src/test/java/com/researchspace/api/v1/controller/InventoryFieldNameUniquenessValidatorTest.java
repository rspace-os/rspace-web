package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.MapBindingResult;

/**
 * Pure unit tests for {@link InventoryFieldNameUniquenessValidator}. No Spring context — exercises
 * the static methods directly with hand-built API objects. All edge cases (case-insensitive
 * comparison, whitespace trimming, deleteFieldRequest skip, null/blank skip, cross-collection
 * collision, error-path naming, original-casing in error args) are pinned here; the
 * Spring-transactional layer ({@code InventoryExtraFieldUniquenessTest}) only needs to prove the
 * managers wire this validator (and core-model's verifyFieldNameAllowed) into the request path.
 */
class InventoryFieldNameUniquenessValidatorTest {

  // ---------- rejectDuplicatesInPayload (validator path; writes to Errors) ----------

  @Test
  void rejectDuplicatesInPayload_exactDuplicateInExtraFields_addsErrorAtSecondIndex() {
    MapBindingResult errors = newErrors();

    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        null, null, List.of(extraField("dup"), extraField("dup")), errors);

    assertEquals(1, errors.getErrorCount());
    assertEquals("extraFields[1].name", errors.getFieldErrors().get(0).getField());
    assertEquals(
        InventoryFieldNameUniquenessValidator.DUPLICATE_NAME_ERROR_CODE,
        errors.getFieldErrors().get(0).getCode());
    assertEquals("dup", errors.getFieldErrors().get(0).getArguments()[0]);
  }

  @Test
  void rejectDuplicatesInPayload_caseOnlyDuplicate_addsError() {
    MapBindingResult errors = newErrors();

    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        null, null, List.of(extraField("Foo"), extraField("foo")), errors);

    assertEquals(1, errors.getErrorCount());
    assertEquals("extraFields[1].name", errors.getFieldErrors().get(0).getField());
    // Original casing of the offending entry preserved in the message args
    assertEquals("foo", errors.getFieldErrors().get(0).getArguments()[0]);
  }

  @Test
  void rejectDuplicatesInPayload_whitespaceDuplicate_addsError() {
    MapBindingResult errors = newErrors();

    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        null, null, List.of(extraField("Foo"), extraField(" Foo ")), errors);

    assertEquals(1, errors.getErrorCount());
    assertEquals("extraFields[1].name", errors.getFieldErrors().get(0).getField());
    // Trimmed value in the message args (not the raw " Foo ")
    assertEquals("Foo", errors.getFieldErrors().get(0).getArguments()[0]);
  }

  @Test
  void rejectDuplicatesInPayload_deleteFieldRequestSkipped_noError() {
    MapBindingResult errors = newErrors();

    ApiInventoryEntityField fieldToDelete = sampleField("A");
    fieldToDelete.setDeleteFieldRequest(true);

    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        null, List.of(fieldToDelete, sampleField("A")), null, errors);

    // The first occurrence is marked for delete, so the second "A" is the only live entry.
    assertFalse(errors.hasErrors());
  }

  @Test
  void rejectDuplicatesInPayload_nullAndBlankNamesSkipped_noError() {
    MapBindingResult errors = newErrors();

    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        null,
        null,
        List.of(extraField(null), extraField(""), extraField("   "), extraField("only")),
        errors);

    assertFalse(errors.hasErrors());
  }

  @Test
  void rejectDuplicatesInPayload_crossCollisionFieldVsExtraField_addsError() {
    MapBindingResult errors = newErrors();

    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        null, List.of(sampleField("X")), List.of(extraField("x")), errors);

    assertEquals(1, errors.getErrorCount());
    assertEquals("extraFields[0].name", errors.getFieldErrors().get(0).getField());
  }

  @Test
  void rejectDuplicatesInPayload_duplicateInFieldsList_addsErrorAtFieldsIndex() {
    MapBindingResult errors = newErrors();

    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        null, List.of(sampleField("A"), sampleField("a")), null, errors);

    assertEquals(1, errors.getErrorCount());
    assertEquals("fields[1].name", errors.getFieldErrors().get(0).getField());
  }

  // ---------- assertNoDuplicateFieldNamesInRequest (manager path; throws ApiRuntimeException) ----

  @Test
  void assertNoDuplicateFieldNamesInRequest_exactDuplicate_throws() {
    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () ->
                InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
                    null, null, List.of(extraField("dup"), extraField("dup"))));
    assertEquals(
        InventoryFieldNameUniquenessValidator.DUPLICATE_NAME_ERROR_CODE, ex.getErrorCode());
    assertEquals("dup", ex.getArgs()[0]);
  }

  @Test
  void assertNoDuplicateFieldNamesInRequest_caseOnlyDuplicate_throws() {
    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () ->
                InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
                    null, null, List.of(extraField("Foo"), extraField("foo"))));
    // Original casing of the second occurrence preserved
    assertEquals("foo", ex.getArgs()[0]);
  }

  @Test
  void assertNoDuplicateFieldNamesInRequest_whitespaceDuplicate_throws() {
    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () ->
                InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
                    null, null, List.of(extraField("Foo"), extraField(" Foo "))));
    assertEquals("Foo", ex.getArgs()[0]);
  }

  @Test
  void assertNoDuplicateFieldNamesInRequest_deleteFieldRequestSkipped_doesNotThrow() {
    ApiInventoryEntityField fieldToDelete = sampleField("A");
    fieldToDelete.setDeleteFieldRequest(true);

    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
        null, List.of(fieldToDelete, sampleField("A")), null);
  }

  @Test
  void assertNoDuplicateFieldNamesInRequest_nullAndBlankNamesSkipped_doesNotThrow() {
    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
        null,
        null,
        List.of(extraField(null), extraField(""), extraField("   "), extraField("only")));
  }

  @Test
  void assertNoDuplicateFieldNamesInRequest_crossCollisionFieldVsExtraField_throws() {
    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class,
            () ->
                InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(
                    null, List.of(sampleField("X")), List.of(extraField("x"))));
    assertEquals("x", ex.getArgs()[0]);
  }

  @Test
  void assertNoDuplicateFieldNamesInRequest_nullLists_doesNotThrow() {
    InventoryFieldNameUniquenessValidator.assertNoDuplicateFieldNamesInRequest(null, null, null);
  }

  // ---------- helpers ----------

  private static ApiExtraField extraField(String name) {
    ApiExtraField f = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    f.setName(name);
    return f;
  }

  private static ApiInventoryEntityField sampleField(String name) {
    ApiInventoryEntityField f = new ApiInventoryEntityField();
    f.setName(name);
    return f;
  }

  private static MapBindingResult newErrors() {
    return new MapBindingResult(new HashMap<>(), "request");
  }

  // Defensive consistency check: error path naming for fields[] vs extraFields[] must use the
  // correct list index even when both lists are present.
  @Test
  void rejectDuplicatesInPayload_indexesAreListLocal() {
    MapBindingResult errors = newErrors();

    InventoryFieldNameUniquenessValidator.rejectDuplicatesInPayload(
        null,
        List.of(sampleField("A"), sampleField("B")),
        List.of(extraField("B"), extraField("C")),
        errors);

    assertEquals(1, errors.getErrorCount());
    assertEquals("extraFields[0].name", errors.getFieldErrors().get(0).getField());
    assertTrue(
        errors.getFieldErrors().get(0).getDefaultMessage().contains("B"),
        "Expected default message to mention duplicated name 'B'");
  }
}
