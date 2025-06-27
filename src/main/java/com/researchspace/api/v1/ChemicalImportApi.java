package com.researchspace.api.v1;

import com.researchspace.model.dtos.chemistry.ChemicalSearchRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/chemical")
public interface ChemicalImportApi {

  @PostMapping("/search")
  ResponseEntity<?> searchChemicals(ChemicalSearchRequest request, BindingResult bindingResult)
      throws BindException;

  @PostMapping("/import")
  ResponseEntity<?> importChemicals(List<String> cids, BindingResult bindingResult)
      throws BindException;
}
