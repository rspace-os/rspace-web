package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Only an Inventory operation may persist an {@code operationFieldKey} (RSDEV-1231).
 *
 * <p>The key is stored as the claim "an operation definition generated this field", and a later run
 * of that operation trusts it to identify the previous generation and continue a computed counter
 * from it. A caller able to set one could therefore have an unrelated field picked up as a previous
 * generation.
 *
 * <p>The rule is enforced at binding: the DTO property is READ_ONLY, so no request body on any
 * endpoint can put a key on a field, and the write point persists whatever the server's request
 * builder set. That is deliberately not the same as asking every other endpoint's validator to
 * reject a key: that was the first design and it leaked, because the sample- and
 * instrument-template validators never call the shared extra-field validation at all (parallel
 * review, C1). A rule that must be remembered in six sibling validators is a rule the seventh will
 * miss.
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

  /** The key actually written to the entity for the given incoming field. */
  private String persistedKeyFor(ApiExtraField incoming) {
    SubSample parent = new SubSample();
    helper.addExtraFieldsForNewInventoryRecord(List.of(incoming), parent, user);
    return parent.getActiveExtraFields().get(0).getOperationFieldKey();
  }

  @Test
  void persistsTheKeyTheServerSet() {
    // What the request builder does when it generates a field.
    ApiExtraField generated = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    generated.setName("Passage number");
    generated.setContent("4");
    generated.setNewFieldRequest(true);
    generated.setOperationFieldKey("operations.passage.numberField");
    assertEquals("operations.passage.numberField", persistedKeyFor(generated));
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
  void theKeyCannotBeSetFromJson() throws Exception {
    // The whole construction rests on this. The forgery route is any endpoint binding extraFields,
    // several of which never reach the shared extra-field validation, so the key is dropped at
    // binding itself, by the API's mapper and by a strict one alike.
    for (ObjectMapper mapper :
        new ObjectMapper[] {Jackson2ObjectMapperBuilder.json().build(), new ObjectMapper()}) {
      ApiExtraField bound =
          mapper.readValue(
              "{\"type\":\"text\",\"name\":\"X\",\"content\":\"1\",\"newFieldRequest\":true,"
                  + "\"operationFieldKey\":\"operations.passage.numberField\"}",
              ApiExtraField.class);
      assertNull(bound.getOperationFieldKey(), "a request must not be able to set the key");
      assertNull(persistedKeyFor(bound));
    }
  }
}
