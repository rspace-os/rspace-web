package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.ChemicalImportApi;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequest;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalImporter;
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
public class ChemicalImportApiController extends BaseApiController implements ChemicalImportApi {

  private final ChemicalImporter chemicalImporter;

  @Autowired
  public ChemicalImportApiController(ChemicalImporter chemicalImporter) {
    this.chemicalImporter = chemicalImporter;
  }

  @Override
  public ResponseEntity<?> searchChemicals(
      @Valid @RequestBody ChemicalSearchRequest request, BindingResult bindingResult)
      throws BindException {
    throwBindExceptionIfErrors(bindingResult);

    try {
      List<ChemicalImportSearchResult> results =
          chemicalImporter.searchChemicals(request.getSearchType(), request.getSearchTerm());
      log.info(
          "Found {} chemical results for search type: {}, term: {}",
          results.size(),
          request.getSearchType(),
          request.getSearchTerm());
      return ResponseEntity.ok(results);
    } catch (ChemicalImportException e) {
      log.error("Error importing chemicals: {}", e.getMessage(), e);
      return handleChemImportException(e);
    } catch (Exception e) {
      log.error("Unexpected error during chemical import", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("An unexpected error occurred.");
    }
  }

  @Override
  public ResponseEntity<?> importChemicals(
      @RequestBody List<String> cids, BindingResult bindingResult) throws BindException {
    throwBindExceptionIfErrors(bindingResult);

    if (cids == null || cids.isEmpty()) {
      return ResponseEntity.badRequest().body("At least one PubChem CID is required");
    }

    try {
      chemicalImporter.importChemicals(cids);
      log.info("Successfully imported {} chemical(s)", cids.size());
      return ResponseEntity.status(HttpStatus.CREATED).build();
    } catch (ChemicalImportException e) {
      log.error("Error importing chemicals: {}", e.getMessage(), e);
      return handleChemImportException(e);
    } catch (Exception e) {
      log.error("Unexpected error during chemical import", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("An unexpected error occurred.");
    }
  }

  private static ResponseEntity<String> handleChemImportException(ChemicalImportException e) {
    if (e.getMessage().contains("Rate limit exceeded")) {
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body("Rate limit exceeded. Please try again later.");
    } else if (e.getMessage().contains("timeout") || e.getMessage().contains("connection")) {
      return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
          .body("External service temporarily unavailable.");
    } else {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Error importing chemical data.");
    }
  }
}
