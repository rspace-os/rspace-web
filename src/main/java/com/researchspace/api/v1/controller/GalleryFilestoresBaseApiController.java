package com.researchspace.api.v1.controller;

import com.researchspace.model.User;
import com.researchspace.service.NfsManager;
import org.springframework.beans.factory.annotation.Autowired;

public class GalleryFilestoresBaseApiController extends BaseApiController {

  @Autowired
  protected NfsManager nfsManager;

  @Autowired
  protected GalleryFilestoresCredentialsStore credentialsStore;

  protected void assertFilestoresApiEnabled(User subject) {
    if (!properties.isNetFileStoresEnabled() && !subject.hasSysadminRole()) {
      throw new UnsupportedOperationException(
          "Gallery Filestores API is not enabled for use on this RSpace instance "
              + "(netfilestores.enabled=false)");
    }
  }

}

