package com.researchspace.files.service.egnyte;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.axiope.search.FileSearchResult;
import com.researchspace.egnyte.api.model.EgnyteSearchResult;
import com.researchspace.egnyte.api.model.SearchResultItem;
import com.researchspace.egnyte.api2.EgnyteResult;
import com.researchspace.files.service.ExternalFileStoreLocator;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class EgnyteFileSearcherTest {
  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock ExternalFileStoreLocator extFSLocator;
  EgnyteFileSearcher searcher;

  @Before
  public void setup() {
    searcher =
        new EgnyteFileSearcher("https://some.egnyte.deomain.com", "/filestore/root/", extFSLocator);
  }

  @Test
  public void testIsLocal() {
    assertFalse(searcher.isLocal());
  }

  @Test
  public void adaptResultsRemovesFileStoreRootPath() {
    EgnyteSearchResult result = new EgnyteSearchResult();

    SearchResultItem item = new SearchResultItem();
    item.setName("file.txt");
    item.setPath(searcher.getFileStoreRoot() + "/Images/f1/f2/" + "file.txt");
    result.setResults(toList(item));
    EgnyteResult<EgnyteSearchResult> wrappedResult =
        new EgnyteResult<EgnyteSearchResult>(
            new ResponseEntity<EgnyteSearchResult>(result, HttpStatus.OK));

    List<FileSearchResult> adapted = searcher.adaptResults(wrappedResult);
    assertEquals("/Images/f1/f2/" + "file.txt", adapted.get(0).getRspaceRelativePath());
  }
}
