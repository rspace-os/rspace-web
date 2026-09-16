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

// See DevDocs/adr/RSDEV-1231-no-concurrency-comments.md for the design rationale behind this test.
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

  private String persistedKeyFor(ApiExtraField incoming) {
    SubSample parent = new SubSample();
    helper.addExtraFieldsForNewInventoryRecord(List.of(incoming), parent, user);
    return parent.getActiveExtraFields().get(0).getOperationFieldKey();
  }

  @Test
  void persistsTheKeyTheServerSet() {
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
