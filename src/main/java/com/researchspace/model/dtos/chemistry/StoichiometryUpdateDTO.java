package com.researchspace.model.dtos.chemistry;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoichiometryUpdateDTO {
  private Long id;
  private List<StoichiometryMoleculeUpdateDTO> molecules;
}
