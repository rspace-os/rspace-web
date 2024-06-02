package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.ApiGenericSearchConfig;
import com.researchspace.api.v1.controller.DocumentApiPaginationCriteria;
import com.researchspace.api.v1.model.ApiShareSearchResult;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Sharing documents/notebooks with groups and users */
@RequestMapping("/api/v1/share")
public interface ShareApi {

  /**
   * Lists groups to which user belongs
   *
   * @param user
   * @return
   * @throws BindException
   */
  @PostMapping
  @ResponseStatus(code = HttpStatus.CREATED)
  ApiSharingResult shareItems(SharePost shareConfig, BindingResult errors, User user)
      throws BindException;

  @DeleteMapping("/{id}")
  @ResponseStatus(code = HttpStatus.NO_CONTENT)
  void deleteShare(Long id, User user);

  @GetMapping
  public ApiShareSearchResult getShares(
      DocumentApiPaginationCriteria pgCrit,
      ApiGenericSearchConfig apiSrchConfig,
      BindingResult errors,
      User user)
      throws BindException;
}
