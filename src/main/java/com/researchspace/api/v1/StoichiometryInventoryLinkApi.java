package com.researchspace.api.v1;

import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryLinkQuantityUpdateRequest;
import com.researchspace.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

@RequestMapping("/api/v1/stoichiometry/link")
public interface StoichiometryInventoryLinkApi {

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  StoichiometryInventoryLinkDTO create(
      @RequestBody StoichiometryInventoryLinkRequest request,
      @RequestAttribute(name = "user") User user);

  @GetMapping("/{id}")
  StoichiometryInventoryLinkDTO get(
      @PathVariable("id") long id, @RequestAttribute(name = "user") User user);

  @PutMapping
  StoichiometryInventoryLinkDTO updateQuantity(
      @RequestBody StoichiometryLinkQuantityUpdateRequest request,
      @RequestAttribute(name = "user") User user);

  @DeleteMapping("/{id}")
  void delete(@PathVariable("id") long id, @RequestAttribute(name = "user") User user);
}
