package com.researchspace.files.service.egnyte;

import com.researchspace.egnyte.api.clients.auth.Token;
import com.researchspace.egnyte.api2.EgnyteApiImpl;
import com.researchspace.egnyte.api2.EgnyteFileApi;
import com.researchspace.model.FileProperty;
import com.researchspace.model.oauth.UserConnection;
import java.net.MalformedURLException;
import java.net.URL;
import lombok.Getter;

@Getter
class AbstractEgnyteAdapter {
  String fileStoreBaseUrl;
  URL fileStoreBaseUrlUrl;
  String fileStoreRoot;
  EgnyteFileApi egnyteApi;

  /**
   * @param fileStoreBaseUrl Base URL of Egnyte instance, can be injected at server startup from
   *     property file
   * @param fileStoreRoot Root path to toplevel of Filestore, injected by Spring Config
   * @throws IllegalArgumentException if is not a syntactically valid URL
   */
  AbstractEgnyteAdapter(String fileStoreBaseUrl, String fileStoreRoot) {
    this.fileStoreBaseUrl = fileStoreBaseUrl;
    this.fileStoreRoot = fileStoreRoot.endsWith("/") ? fileStoreRoot : fileStoreRoot + "/";
    try {
      this.fileStoreBaseUrlUrl = new URL(fileStoreBaseUrl);
    } catch (MalformedURLException e) {
      throw new IllegalArgumentException(String.format("%s is not a valid URL", fileStoreBaseUrl));
    }

    this.egnyteApi = new EgnyteApiImpl(fileStoreBaseUrlUrl);
  }

  String createFilePath(FileProperty fileProperty, boolean fileNameInPath) {
    return fileStoreRoot + fileProperty.makeTargetPath(fileNameInPath);
  }

  Token createToken(UserConnection userConnection) {
    return new Token(
        userConnection.getAccessToken(), userConnection.getExpireTime().intValue(), "bearer");
  }
}
