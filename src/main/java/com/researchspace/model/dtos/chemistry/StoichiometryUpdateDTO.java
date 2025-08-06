package com.researchspace.model.dtos.chemistry;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for updating a Stoichiometry. Contains only the information necessary to update a
 * StoichiometryMolecule: the id of the molecule, and all of the updateable fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoichiometryUpdateDTO {
  private Long id;
  private List<StoichiometryMoleculeUpdateDTO> molecules;
}
