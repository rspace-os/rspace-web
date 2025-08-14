package com.researchspace.api.v1;

import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import org.springframework.web.bind.annotation.*;

/** Stoichiometry endpoints */
@RequestMapping("/api/v1/stoichiometry")
public interface StoichiometryApi {

  @PostMapping("/molecule/info")
  StoichiometryMoleculeDTO getMoleculeInfo(@RequestBody ChemicalDTO chemicalDTO);

  @GetMapping
  StoichiometryDTO getStoichiometry(
      @RequestParam("chemId") long chemId,
      @RequestParam(value = "revision", required = false) Integer revision,
      @RequestAttribute(name = "user") User user);

  @PostMapping
  StoichiometryDTO saveStoichiometry(
      @RequestParam("chemId") long chemId,
      @RequestParam(value = "revision", required = false) Integer revision,
      @RequestAttribute(name = "user") User user);

  @PutMapping
  StoichiometryDTO updateStoichiometry(
      @RequestParam("stoichiometryId") long stoichiometryId,
      @RequestBody StoichiometryUpdateDTO stoichiometryUpdateDTO,
      @RequestAttribute(name = "user") User user);

  @DeleteMapping
  Boolean deleteStoichiometry(
      @RequestParam("stoichiometryId") long stoichiometryId,
      @RequestAttribute(name = "user") User user);

  class ChemicalDTO {
    private String chemical;

    public String getChemical() {
      return chemical;
    }

    public void setChemical(String chemical) {
      this.chemical = chemical;
    }
  }
}
