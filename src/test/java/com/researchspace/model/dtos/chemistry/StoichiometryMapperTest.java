package com.researchspace.model.dtos.chemistry;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.Stoichiometry;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class StoichiometryMapperTest {

  @Test
  void toDTO_withReactionAndRecord_populatesBothIds() {
    Record record = mock(Record.class);
    when(record.getId()).thenReturn(123L);

    RSChemElement reaction = mock(RSChemElement.class);
    when(reaction.getId()).thenReturn(456L);
    when(reaction.getRecord()).thenReturn(record);

    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(789L);
    stoichiometry.setParentReaction(reaction);
    stoichiometry.setMolecules(Collections.emptyList());

    StoichiometryDTO dto = StoichiometryMapper.toDTO(stoichiometry, 1L);

    assertNotNull(dto);
    assertEquals(789L, dto.getId());
    assertEquals(456L, dto.getParentReactionId());
    assertEquals(123L, dto.getRecordId());
    assertEquals(1L, dto.getRevision());
  }

  @Test
  void toDTO_withNullStoichiometry_returnsNull() {
    assertNull(StoichiometryMapper.toDTO(null, 1L));
  }

  @Test
  void toDTO_withNullParentReaction_hasNullIds() {
    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(789L);
    stoichiometry.setParentReaction(null);

    StoichiometryDTO dto = StoichiometryMapper.toDTO(stoichiometry, 1L);

    assertNotNull(dto);
    assertNull(dto.getParentReactionId());
    assertNull(dto.getRecordId());
  }

  @Test
  void toDTO_withNullRecord_hasNullRecordId() {
    RSChemElement reaction = mock(RSChemElement.class);
    when(reaction.getId()).thenReturn(456L);
    when(reaction.getRecord()).thenReturn(null);

    Stoichiometry stoichiometry = new Stoichiometry();
    stoichiometry.setId(789L);
    stoichiometry.setParentReaction(reaction);

    StoichiometryDTO dto = StoichiometryMapper.toDTO(stoichiometry, 1L);

    assertNotNull(dto);
    assertEquals(456L, dto.getParentReactionId());
    assertNull(dto.getRecordId());
  }
}
