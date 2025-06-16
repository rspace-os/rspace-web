package com.researchspace.api.v1.controller;

import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResult;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequest;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalImporter;
import java.util.List;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@ApiController
@RequestMapping("/api/v1")
public class ChemicalImportApiController extends BaseApiController {

  private final ChemicalImporter chemicalImporter;

  @Autowired
  public ChemicalImportApiController(ChemicalImporter chemicalImporter) {
    this.chemicalImporter = chemicalImporter;
  }

  @PostMapping("/chemical/import")
  public ResponseEntity<?> importChemicals(@Valid @RequestBody List<String> casNumbers, BindingResult bindingResult) throws BindException {
    throwBindExceptionIfErrors(bindingResult);

    if (casNumbers == null || casNumbers.isEmpty()) {
      return ResponseEntity.badRequest().body("At least one CAS number is required");
    }

    try {
      chemicalImporter.importChemicals(casNumbers);
      log.info("Successfully imported {} chemical(s)", casNumbers.size());
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

  @PostMapping("/chemical/search")
  public ResponseEntity<?> searchChemicals(
      @Valid @RequestBody ChemicalSearchRequest request, BindingResult bindingResult) throws BindException {
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

  @NotNull
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
