package com.researchspace.api.v1;

import com.researchspace.model.dtos.chemistry.ChemicalSearchRequest;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/chemical")
public interface ChemicalImportApi {

  /**
   * Imports chemicals by PubChem Compound IDs (CIDs)
   *
   * @param cids List of PubChem Compound IDs to import
   * @param bindingResult Validation result
   * @return ResponseEntity indicating success or failure
   * @throws BindException if validation errors occur
   */
  @PostMapping("/import")
  ResponseEntity<?> importChemicals(
      @Valid @RequestBody List<String> cids, BindingResult bindingResult) throws BindException;

  /**
   * Searches for chemicals using various search criteria
   *
   * @param request Search request containing search type and term
   * @param bindingResult Validation result
   * @return ResponseEntity containing search results
   * @throws BindException if validation errors occur
   */
  @PostMapping("/search")
  ResponseEntity<?> searchChemicals(
      @Valid @RequestBody ChemicalSearchRequest request, BindingResult bindingResult)
      throws BindException;
}
