package com.researchspace.files.service.egnyte;

import static java.util.stream.Collectors.toList;

import com.axiope.search.FileSearchResult;
import com.axiope.search.FileSearchStrategy;
import com.researchspace.egnyte.api.clients.auth.Token;
import com.researchspace.egnyte.api.clients.requests.SearchRequest;
import com.researchspace.egnyte.api.model.EgnyteSearchResult;
import com.researchspace.egnyte.api2.EgnyteResult;
import com.researchspace.files.service.ExternalFileStoreLocator;
import com.researchspace.files.service.ExternalFileStoreWithCredentials;
import com.researchspace.model.User;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class EgnyteFileSearcher extends AbstractEgnyteAdapter implements FileSearchStrategy {
  private ExternalFileStoreLocator externalFileStoreLocator;
  private int defaultReturnDocs = 20;
  private @Autowired Validator validator;

  public EgnyteFileSearcher(
      String fileStoreBaseUrl,
      String fileStoreRoot,
      ExternalFileStoreLocator externalFileStoreLocator) {
    super(fileStoreBaseUrl, fileStoreRoot);
    this.externalFileStoreLocator = externalFileStoreLocator;
  }

  @Override
  public List<FileSearchResult> searchFiles(String searchTerm, User user) throws IOException {
    Optional<ExternalFileStoreWithCredentials> extFileStoreOpt =
        externalFileStoreLocator.getExternalFileStoreForUser(user.getUsername());
    Token token = createToken(extFileStoreOpt.get().getUserConnection());
    SearchRequest request =
        SearchRequest.builder()
            .query(searchTerm)
            .folder(fileStoreRoot)
            .count(defaultReturnDocs)
            .build();
    Set<ConstraintViolation<SearchRequest>> validation = validator.validate(request);
    if (!validation.isEmpty()) {
      throw new IllegalArgumentException("Invalid search request!: " + request.toString());
    }
    EgnyteResult<EgnyteSearchResult> result = egnyteApi.search(token, request);
    if (result.isSuccessful()) {
      List<FileSearchResult> rc = adaptResults(result);
      return rc;
    } else {
      log.error(
          "Error during searching for files with term {} by user {}. Returning empty result set",
          searchTerm,
          user.getUsername());
      return Collections.emptyList();
    }
  }

  List<FileSearchResult> adaptResults(EgnyteResult<EgnyteSearchResult> result) {
    List<FileSearchResult> rc =
        result.getResult().getResults().stream()
            .map(
                r ->
                    FileSearchResult.builder()
                        .fileName(r.getName())
                        .filePath(r.getPath())
                        .rspaceRelativePath(r.getPath().replaceAll(fileStoreRoot, ""))
                        .build())
            .collect(toList());
    return rc;
  }

  @Override
  public void setDefaultReturnDocs(int nbrDocs) {
    this.defaultReturnDocs = nbrDocs;
  }

  @Override
  public boolean isLocal() {
    return false;
  }
}
