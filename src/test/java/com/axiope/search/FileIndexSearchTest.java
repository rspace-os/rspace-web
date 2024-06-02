package com.axiope.search;

import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.EcatDocumentFileDao;
import com.researchspace.search.impl.FileIndexSearcher;
import com.researchspace.search.impl.FileIndexer;
import com.researchspace.search.impl.LuceneSearchStrategy;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FileIndexSearchTest {

  class FileIndexerTSS extends FileIndexer {
    /** Overrides real setter to provide a test instance of the file index folder */
    public String getIndexFolderPath() {
      return indexPath;
    }

    protected void extractFiles(File folder, List<File> fileList) {
      super.extractFiles(folder, fileList);
      // get a determined order of files: alphabetically ascending
      fileList.sort(Comparator.comparing(File::getName));
    }
  }

  class FileSearcherTSS extends FileIndexSearcher {
    public FileSearcherTSS() throws IOException {
      super();
    }

    /** Overrides real setter to provide a test instance of the file index folder */
    public String getIndexFolderPath() {
      return indexFolder.getAbsolutePath();
    }
  }

  private @Mock EcatDocumentFileDao docDao;

  FileSearchStrategy searcher = new LuceneSearchStrategy();

  final String pdfPath = "src/test/resources/TestResources/smartscotland3.pdf";
  // this is 2nd in list when sorted alphabeticallt
  final String pdfCorruptedPath = "src/test/resources/TestResources/NotIndexable.pdf";
  // this should be indexed, even if failing fast
  final String msPath = "src/test/resources/TestResources/MSattachment.doc";
  final String odtPath = "src/test/resources/TestResources/ODT.odt";
  final String odsPath = "src/test/resources/TestResources/ODS.ods";
  final String odpPath = "src/test/resources/TestResources/ODP.odp";
  final String indexPath = "src/test/resources/indextest/file_store";
  final String pdfSearch = "algorithm";
  final String msSearch = "Fedora";
  final String odtSearch = "Cycling";
  final String odsSearch = "primers";
  final String odpSearch = "Olympics";

  String[] pathsToIndex = new String[] {pdfPath, msPath, odpPath, odsPath, odtPath};
  // corrupted is 3rd in list
  String[] pathsToIndexWithCorruptFile =
      new String[] {pdfPath, msPath, pdfCorruptedPath, odpPath, odsPath, odtPath};

  @TempDir File dataFolder;

  @TempDir File indexFolder;

  @Test
  void testAccept() {
    FileIndexer idxer = new FileIndexerTSS();
    assertTrue(idxer.accept(new File(pdfPath)));
    assertTrue(idxer.accept(new File(odtPath)));
    assertTrue(idxer.accept(new File(odsPath)));
    assertTrue(idxer.accept(new File(odpPath)));
    assertFalse(idxer.accept(RSpaceTestUtils.getResource("Picture1.png")));
  }

  private void setUpindexFiles(boolean failFast, String... pathsToIndex) throws Exception {
    FileIndexer idxer = null;
    Stream.of(pathsToIndex)
        .forEach(
            (path) -> {
              File inF = new File(path);
              try {
                copyToDataFolder(inF);
              } catch (Exception e) {
                e.printStackTrace();
              }
            });

    idxer = new FileIndexerTSS();
    try {
      // int nbr = idxer.indexFileStore();
      idxer.init(true);
      int nbr = idxer.indexFolder(dataFolder, failFast);
      assertTrue(nbr == idxer.getWriter().numDocs());
    } finally {
      idxer.close();
    }
  }

  private void copyToDataFolder(File inF) throws IOException, FileNotFoundException {
    if (inF.exists()) {
      File ouF = new File(dataFolder, inF.getName());
      IOUtils.copy(new FileInputStream(inF), new FileOutputStream(ouF));
    }
  }

  @Test
  void failFastIndexerThrowsIAEForCorrupted() throws Exception {
    assertThrows(IOException.class, () -> setUpindexFiles(true, pathsToIndexWithCorruptFile));
    assertEquals(0, searcher.searchFiles(odtSearch, createAnyUser("any")).size());
    // this gets indexed first, before NonIdexable.pdf, and can still be searched
    assertEquals(1, searcher.searchFiles(msSearch, createAnyUser("any")).size());
  }

  @Test
  void failFastIndexerContinues() throws Exception {
    setUpindexFiles(false, pathsToIndexWithCorruptFile);
    // even though an earlier file fails, this still gets indexed
    assertEquals(1, searcher.searchFiles(odtSearch, createAnyUser("any")).size());
  }

  @Test
  void testFileSearcher() throws Exception {
    setUpindexFiles(true, pathsToIndex);

    List<String> files = Arrays.asList(new String[] {pdfPath, msPath, odtPath, odsPath, odpPath});
    List<String> terms =
        Arrays.asList(new String[] {pdfSearch, msSearch, odtSearch, odsSearch, odpSearch});

    IntStream.range(0, files.size())
        .forEach(
            i -> {
              try {
                List<FileSearchResult> results =
                    searcher.searchFiles(terms.get(i), createAnyUser("any"));
                assertTrue(
                    results.stream()
                        .anyMatch(
                            f -> f.getFileName().equals(FilenameUtils.getName(files.get(i)))));
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
  }
}
