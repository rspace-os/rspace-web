package com.researchspace.api.v1;

import com.researchspace.model.dtos.chemistry.ChemicalSearchRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/pubchem")
public interface PubchemSearchApi {

  @PostMapping("/search")
  ResponseEntity<?> searchChemicals(ChemicalSearchRequest request, BindingResult bindingResult)
      throws BindException;
}
