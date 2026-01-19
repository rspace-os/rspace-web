package com.researchspace.model.dtos.chemistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.Stoichiometry;
import org.junit.jupiter.api.Test;

class StoichiometryMapperTest {

  @Test
  void toDTO_withRecord_populatesRecordId() {
    Record record = mock(Record.class);
    when(record.getId()).thenReturn(123L);

    RSChemElement reaction = mock(RSChemElement.class);
    when(reaction.getId()).thenReturn(456L);

    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(789L);
    stoichiometry.setParentReaction(reaction);
    stoichiometry.setRecord(record);

    StoichiometryDTO dto = StoichiometryMapper.toDTO(stoichiometry, 1L);

    assertNotNull(dto);
    assertEquals(456L, dto.getParentReactionId());
    assertEquals(123L, dto.getRecordId());
  }

  @Test
  void toDTO_withNullStoichiometry_returnsNull() {
    assertNull(StoichiometryMapper.toDTO(null, 1L));
  }

  @Test
  void toDTO_withNullParentReaction_hasNullParentIdAndCorrectRecordId() {
    Record record = mock(Record.class);
    when(record.getId()).thenReturn(123L);

    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(789L);
    stoichiometry.setRecord(record);
    stoichiometry.setParentReaction(null);

    StoichiometryDTO dto = StoichiometryMapper.toDTO(stoichiometry, 1L);

    assertNotNull(dto);
    assertNull(dto.getParentReactionId());
    assertEquals(123L, dto.getRecordId());
  }
}
