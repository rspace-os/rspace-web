/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.model.User;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/inventory/v1/instruments")
public interface InstrumentsApi {

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiInstrument createNewInstrument(
      @RequestBody @Valid ApiInstrument sample, BindingResult errors, User user)
      throws BindException;

  @GetMapping(value = "/{id}")
  ApiInstrument getInstrumentById(Long id, User user);
}
