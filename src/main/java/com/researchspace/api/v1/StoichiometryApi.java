package com.researchspace.api.v1;

import com.researchspace.api.v1.model.stoichiometry.StockDeductionRequest;
import com.researchspace.api.v1.model.stoichiometry.StockDeductionResult;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.service.DocumentAlreadyEditedException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/v1/stoichiometry")
public interface StoichiometryApi {

  @PostMapping("/molecule/info")
  StoichiometryMoleculeDTO getMoleculeInfo(@RequestBody ChemicalDTO chemicalDTO);

  @GetMapping
  StoichiometryDTO getStoichiometryById(
      @RequestParam("stoichiometryId") long stoichiometryId,
      @RequestParam(value = "revision", required = false) Long revision,
      @RequestAttribute(name = "user") User user);

  @PostMapping
  StoichiometryDTO createStoichiometry(
      @RequestParam(value = "recordId") Long recordId,
      @RequestParam(value = "chemId", required = false) Long chemId,
      @RequestAttribute(name = "user") User user);

  @PutMapping
  StoichiometryDTO updateStoichiometry(
      @RequestParam("stoichiometryId") long stoichiometryId,
      @RequestBody StoichiometryUpdateDTO stoichiometryUpdateDTO,
      @RequestParam(value = "updateFieldHtml", required = false, defaultValue = "false")
          boolean updateFieldHtml,
      @RequestAttribute(name = "user") User user)
      throws DocumentAlreadyEditedException;

  @DeleteMapping
  Boolean deleteStoichiometry(
      @RequestParam("stoichiometryId") long stoichiometryId,
      @RequestParam(value = "updateFieldHtml", required = false, defaultValue = "false")
          boolean updateFieldHtml,
      @RequestAttribute(name = "user") User user)
      throws DocumentAlreadyEditedException;

  @PostMapping("/link/deductStock")
  StockDeductionResult deductStock(
      @RequestBody @Valid StockDeductionRequest request, @RequestAttribute(name = "user") User user)
      throws DocumentAlreadyEditedException;

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
