package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.PubchemSearchApi;
import com.researchspace.api.v1.model.ErrorResponse;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequest;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalSearcher;
import java.util.List;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestBody;

@Slf4j
@ApiController
public class PubchemSearchApiController extends BaseApiController implements PubchemSearchApi {

  private final ChemicalSearcher chemicalSearcher;

  @Autowired
  public PubchemSearchApiController(ChemicalSearcher chemicalSearcher) {
    this.chemicalSearcher = chemicalSearcher;
  }

  @Override
  public ResponseEntity<?> searchChemicals(
      @Valid @RequestBody ChemicalSearchRequest request, BindingResult bindingResult)
      throws BindException {
    throwBindExceptionIfErrors(bindingResult);

    try {
      List<ChemicalImportSearchResult> results =
          chemicalSearcher.searchChemicals(request.getSearchType(), request.getSearchTerm());
      log.info(
          "Found {} chemical results for search type: {}, term: {}",
          results.size(),
          request.getSearchType(),
          request.getSearchTerm());
      return ResponseEntity.ok(results);
    } catch (ChemicalImportException e) {
      log.error("Error importing chemicals: {}", e.getMessage(), e);
      return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getMessage()));
    } catch (Exception e) {
      log.error("Unexpected error during chemical import", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("An unexpected error occurred.");
    }
  }
}
