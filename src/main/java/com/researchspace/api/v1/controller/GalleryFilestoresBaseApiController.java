package com.researchspace.api.v1.controller;

import com.researchspace.model.User;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.NfsManager;
import org.springframework.beans.factory.annotation.Autowired;

public class GalleryFilestoresBaseApiController extends BaseApiController {

  @Autowired protected NfsManager nfsManager;

  @Autowired protected GalleryFilestoresCredentialsStore credentialsStore;

  @Autowired protected FilestoreAclChecker aclChecker;

  protected void assertFilestoresApiEnabled(User subject) {
    if (!properties.isNetFileStoresEnabled() && !subject.hasSysadminRole()) {
      throw new UnsupportedOperationException(
          getMessage("netFileStores.errors.apiNotEnabled", new Object[] {}));
    }
  }
}
