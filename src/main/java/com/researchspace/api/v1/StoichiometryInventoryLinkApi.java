package com.researchspace.api.v1;

import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryLinkQuantityUpdateRequest;
import com.researchspace.model.User;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/stoichiometry")
public interface StoichiometryInventoryLinkApi {

  @PostMapping("/link")
  StoichiometryInventoryLinkDTO create(
      @RequestBody StoichiometryInventoryLinkRequest request,
      @RequestAttribute(name = "user") User user);

  @GetMapping("/link/{id}")
  StoichiometryInventoryLinkDTO get(
      @PathVariable("id") long id, @RequestAttribute(name = "user") User user);

  @PutMapping("/link")
  StoichiometryInventoryLinkDTO updateQuantity(
      @RequestBody StoichiometryLinkQuantityUpdateRequest request,
      @RequestAttribute(name = "user") User user);

  @DeleteMapping("/link/{id}")
  void delete(@PathVariable("id") long id, @RequestAttribute(name = "user") User user);
}
