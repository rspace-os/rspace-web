package com.researchspace.api.v1.controller;

import com.researchspace.model.dtos.chemistry.ChemicalImportRequest;
import com.researchspace.model.dtos.chemistry.ChemicalImportSearchResults;
import com.researchspace.service.ChemicalImportException;
import com.researchspace.service.ChemicalImporter;
import java.util.List;
import javax.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class ChemicalImportApiController extends BaseApiController {

  private final ChemicalImporter chemicalImporter;

  @Autowired
  public ChemicalImportApiController(ChemicalImporter chemicalImporter) {
    this.chemicalImporter = chemicalImporter;
  }

  @PostMapping("/chemical/import")
  public ResponseEntity<?> importChemicals(
      @Valid @RequestBody ChemicalImportRequest request, BindingResult bindingResult) {

    if (bindingResult.hasErrors()) {
      log.warn("Validation errors in chemical import request: {}", bindingResult.getAllErrors());
      return ResponseEntity.badRequest().body(createValidationErrorResponse(bindingResult));
    }

    try {
      List<ChemicalImportSearchResults> results =
          chemicalImporter.importChemicals(request.getSearchType(), request.getSearchTerm());

      log.info(
          "Successfully imported {} chemical results for search type: {}, term: {}",
          results.size(),
          request.getSearchType(),
          request.getSearchTerm());

      return ResponseEntity.ok(results);

    } catch (ChemicalImportException e) {
      log.error("Error importing chemicals: {}", e.getMessage(), e);

      if (e.getMessage().contains("Rate limit exceeded")) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(createErrorResponse("Rate limit exceeded. Please try again later."));
      } else if (e.getMessage().contains("timeout") || e.getMessage().contains("connection")) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(createErrorResponse("External service temporarily unavailable."));
      } else {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(createErrorResponse("Error importing chemical data."));
      }
    } catch (Exception e) {
      log.error("Unexpected error during chemical import", e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(createErrorResponse("An unexpected error occurred."));
    }
  }

  private Object createValidationErrorResponse(BindingResult bindingResult) {
    return new ValidationErrorResponse(
        "Validation failed",
        bindingResult.getAllErrors().stream()
            .map(error -> error.getDefaultMessage())
            .toArray(String[]::new));
  }

  private Object createErrorResponse(String message) {
    return new ErrorResponse(message);
  }

  private static class ValidationErrorResponse {
    public final String message;
    public final String[] errors;

    public ValidationErrorResponse(String message, String[] errors) {
      this.message = message;
      this.errors = errors;
    }
  }

  private static class ErrorResponse {
    public final String message;

    public ErrorResponse(String message) {
      this.message = message;
    }
  }
}
