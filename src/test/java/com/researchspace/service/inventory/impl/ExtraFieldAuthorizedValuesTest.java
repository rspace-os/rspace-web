package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.field.ExtraFieldIdentity;
import com.researchspace.model.inventory.field.ExtraFieldIdentity.Definition;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtraFieldAuthorizedValuesTest {

  @Test
  void readsSelectedValuesInOneBatchFromAuthorizedIdsWithoutEntities() {
    ExtraFieldDao dao = mock(ExtraFieldDao.class);
    CollectionDescription<Item> items =
        new CollectionDescription<>(
            "items",
            Item.class,
            List.of(Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Item::id)),
            "id",
            List.of(new Sort("id", true)));
    var provider =
        new ExtraFieldRuntimeManagerImpl<>(
            dao,
            Item.class,
            items,
            "item",
            context -> {
              throw new AssertionError("Authorized page IDs have already passed the read policy");
            });
    Definition text = new Definition("Room", FieldType.TEXT);
    Definition number = new Definition("Voltage", FieldType.NUMBER);
    Definition link = new Definition("Reference", FieldType.LINK);
    String textId = ExtraFieldIdentity.encode(text.name(), text.type());
    String numberId = ExtraFieldIdentity.encode(number.name(), number.type());
    String linkId = ExtraFieldIdentity.encode(link.name(), link.type());
    Map<Definition, String> stored = new LinkedHashMap<>();
    stored.put(text, null);
    stored.put(number, "12.5");
    stored.put(link, "https://example.test/reference");
    when(dao.valuesByParent(any(), eq(Set.of(1L, 2L)), eq(Set.of(text, number, link))))
        .thenReturn(Map.of(1L, stored));

    Map<Object, Map<String, Object>> result =
        provider.valuesForAuthorizedIds(
            List.of(1L, 2L, 1L), Set.of(textId, numberId, linkId), null);

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put(textId, null);
    expected.put(numberId, new BigDecimal("12.5"));
    expected.put(linkId, "https://example.test/reference");
    assertEquals(expected, result.get(1L));
    assertFalse(result.containsKey(2L));
    verify(dao).valuesByParent(any(), eq(Set.of(1L, 2L)), eq(Set.of(text, number, link)));
    verifyNoMoreInteractions(dao);
  }

  private record Item(Long id) {}
}
