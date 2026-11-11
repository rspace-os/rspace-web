package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.dao.InstrumentCustomFieldDao;
import com.researchspace.dao.InstrumentCustomFieldDao.ValueRow;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentCustomFieldManagerImplTest {

  @Mock private InstrumentCustomFieldDao dao;
  @Mock private InstrumentReadAccess readAccess;

  private InstrumentCustomFieldManagerImpl manager;
  private final User actor = new User("actor");

  @BeforeEach
  void setUp() {
    manager = new InstrumentCustomFieldManagerImpl(dao, readAccess);
  }

  @ParameterizedTest
  @MethodSource("publishedValues")
  void serializesScalarRowsPreservingEmptyAndMissingDefinitions(
      FieldType type, String stored, Object expected) {
    Instrument first = instrument(11L);
    Instrument withoutValues = instrument(12L);
    when(dao.valuesByInstrument(Set.of(11L, 12L), Set.of(21L, 22L, 23L, 24L)))
        .thenReturn(
            Map.of(
                11L,
                Map.of(
                    21L, new ValueRow(11L, 21L, type, stored),
                    22L, new ValueRow(11L, 22L, type, null),
                    23L, new ValueRow(11L, 23L, type, ""))));

    Map<Object, Map<String, Object>> values =
        manager.values(
            List.of(first, withoutValues), Set.of("SF21", "SF22", "SF23", "SF24"), actor);

    Map<String, Object> document = values.get(11L);
    assertEquals(Set.of("SF21", "SF22", "SF23"), document.keySet());
    assertEquals(expected, document.get("SF21"));
    assertNull(document.get("SF22"));
    assertNull(document.get("SF23"));
    assertFalse(values.containsKey(12L));
  }

  private static Stream<Arguments> publishedValues() {
    return Stream.of(
        Arguments.of(FieldType.STRING, "string", "string"),
        Arguments.of(FieldType.TEXT, "<p>text</p>", "<p>text</p>"),
        Arguments.of(FieldType.URI, "https://example.org", "https://example.org"),
        Arguments.of(FieldType.IDENTIFIER, "10.1234/example", "10.1234/example"),
        Arguments.of(FieldType.NUMBER, "-80.50", new BigDecimal("-80.50")),
        Arguments.of(FieldType.NUMBER, "n/a", null),
        Arguments.of(FieldType.NUMBER, "1e-31", null),
        Arguments.of(FieldType.DATE, "2026-10-14", "2026-10-14"),
        Arguments.of(FieldType.TIME, "09:45", "09:45"),
        Arguments.of(FieldType.RADIO, "BSL-2", "BSL-2"),
        Arguments.of(
            FieldType.CHOICE, "[\"one\",\"two, with comma\"]", List.of("one", "two, with comma")),
        Arguments.of(FieldType.CHOICE, "legacy choice", List.of("legacy choice")),
        Arguments.of(FieldType.CHOICE, "[]", List.of()));
  }

  @Test
  void restrictsRelationshipProjectionToReadableInstrumentIds() {
    FilterExpression constraint =
        new FilterExpression.Comparison("deleted", Operator.EQUAL, List.of(false), false);
    when(readAccess.check(any())).thenReturn(AccessResult.allowedWhere(constraint));
    when(dao.readableInstruments(constraint, Set.of(11L, 12L))).thenReturn(Set.of(11L));
    when(dao.valuesByInstrument(Set.of(11L), Set.of(21L)))
        .thenReturn(Map.of(11L, Map.of(21L, new ValueRow(11L, 21L, FieldType.TEXT, "visible"))));

    assertEquals(
        Map.of(11L, Map.of("SF21", "visible")),
        manager.valuesForIds(List.of(11L, 12L), Set.of("SF21"), actor));
    verify(readAccess)
        .check(
            argThat(
                context ->
                    context.user() == actor
                        && context.operation() == AccessContext.Operation.READ
                        && context.resourceName().equals("instruments")));
    verify(dao).valuesByInstrument(Set.of(11L), Set.of(21L));
  }

  @Test
  void deniedRelationshipProjectionDoesNotReadValues() {
    when(readAccess.check(any())).thenReturn(AccessResult.denied("authentication.required"));

    assertEquals(Map.of(), manager.valuesForIds(List.of(11L), Set.of("SF21"), actor));

    verifyNoInteractions(dao);
  }

  private static Instrument instrument(Long id) {
    Instrument instrument = mock(Instrument.class);
    when(instrument.getId()).thenReturn(id);
    return instrument;
  }
}
