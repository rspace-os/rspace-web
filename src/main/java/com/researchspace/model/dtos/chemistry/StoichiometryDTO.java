package com.researchspace.model.dtos.chemistry;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoichiometryDTO {
  private Long id;
  private Long parentReactionId;
  private List<StoichiometryMoleculeDTO> molecules;
}
