package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.record.IRecordFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Only an Inventory operation may persist an {@code operationFieldKey} (RSDEV-1231).
 *
 * <p>The key is stored as the claim "an operation definition generated this field", and a later run
 * of that operation trusts it to identify the previous generation and continue a computed counter
 * from it. A caller able to set one could therefore have an unrelated field picked up as a previous
 * generation.
 *
 * <p>The rule is enforced HERE, at the single point that writes the column, gated on a
 * {@code @JsonIgnore} flag only the operations endpoint's validator sets. That is deliberately not
 * the same as asking every other endpoint's validator to reject a key: that was the first design
 * and it leaked, because the sample- and instrument-template validators never call the shared
 * extra-field validation at all (parallel review, C1). A rule that must be remembered in six
 * sibling validators is a rule the seventh will miss.
 */
class OperationFieldKeyPersistenceTest {

  private ApiExtraFieldsHelper helper;
  private final User user = new User("anyUser");

  @BeforeEach
  void setUp() {
    IRecordFactory recordFactory = mock(IRecordFactory.class);
    when(recordFactory.createExtraField(anyString(), any(), any(), any()))
        .thenAnswer(
            call -> {
              ExtraTextField field = new ExtraTextField();
              field.setName(call.getArgument(0));
              return field;
            });
    helper = new ApiExtraFieldsHelper(recordFactory);
  }

  private static ApiExtraField incomingField(String operationFieldKey, boolean verified) {
    ApiExtraField field = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    field.setName("Passage number");
    field.setContent("999");
    field.setNewFieldRequest(true);
    field.setOperationFieldKey(operationFieldKey);
    field.setOperationFieldKeyVerified(verified);
    return field;
  }

  /** The key actually written to the entity for the given incoming field. */
  private String persistedKeyFor(ApiExtraField incoming) {
    SubSample parent = new SubSample();
    helper.addExtraFieldsForNewInventoryRecord(List.of(incoming), parent, user);
    return parent.getActiveExtraFields().get(0).getOperationFieldKey();
  }

  @Test
  void persistsTheKeyWhenTheOperationsValidatorHasVerifiedIt() {
    assertEquals(
        "operations.passage.numberField",
        persistedKeyFor(incomingField("operations.passage.numberField", true)));
  }

  @Test
  void ignoresAKeyNoOperationVerified() {
    // The forgery route: any endpoint binding extraFields can send this, and several never reach
    // the shared extra-field validation. The write point drops it, so the field is stored
    // truthfully as one that no operation generated.
    assertNull(persistedKeyFor(incomingField("operations.passage.numberField", false)));
  }

  @Test
  void storesNullForAnOrdinaryFieldCarryingNoKey() {
    ApiExtraField plain = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    plain.setName("Batch");
    plain.setContent("B1");
    plain.setNewFieldRequest(true);
    assertNull(persistedKeyFor(plain));
  }

  @Test
  void theVerifiedFlagCannotBeSetFromJson() throws Exception {
    // The whole construction rests on this. If a request could verify its own key, gating the
    // write on the flag would be no protection at all.
    ApiExtraField bound =
        new ObjectMapper()
            .readValue(
                "{\"type\":\"text\",\"name\":\"X\",\"content\":\"1\",\"newFieldRequest\":true,"
                    + "\"operationFieldKey\":\"operations.passage.numberField\","
                    + "\"operationFieldKeyVerified\":true}",
                ApiExtraField.class);

    assertEquals("operations.passage.numberField", bound.getOperationFieldKey());
    assertFalse(
        bound.isOperationFieldKeyVerified(), "a request must not be able to verify its own key");
    assertNull(persistedKeyFor(bound));
  }
}
