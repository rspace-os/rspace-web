package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.User;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FileStoreImpTest {

  class FileStoreImpTss extends InternalFileStoreImpl {
    Collection<File> rc;

    Collection<File> getUTF8misMatchFiles(String absPath, String fname) {
      return rc;
    }
  }

  InternalFileStoreImpl fs;
  String utf8FileName = "widok_z_łazika.png";
  String utf8FileName2 = "w?dok_z_łazika.png";
  User user;

  @BeforeEach
  public void setUp() throws Exception {
    fs = new InternalFileStoreImpl();
    user = TestFactory.createAnyUser("any");
  }

  @Test
  public void testHandlePossibleUTF8Error1() throws IOException {
    List<String> files = Arrays.asList(new String[] {utf8FileName, utf8FileName2});
    for (String file : files) {
      File utf8File = RSpaceTestUtils.getResource(file);
      FileProperty fp = setupFileStoreRoot(utf8File);
      String corruptedName = utf8File.getName().replace("ł", "?");
      // mimic messed up stream
      fp.setRelPath(corruptedName);

      FileInputStream stream = fs.handlePossibleUTF8Error(fp, fnfe());
      assertNotNull(stream);
    }
  }

  @Test
  public void testHandlePossibleUTF8ErrorCardinality() throws IOException {
    FileStoreImpTss tss = new FileStoreImpTss();
    // simulate inability to find matching file
    tss.rc = Collections.emptyList();
    File utf8File = RSpaceTestUtils.getResource(utf8FileName);
    FileProperty fp = setupFileStoreRoot(utf8File);
    String corruptedName = utf8File.getName().replace("ł", "?");
    // mimic messed up stream
    fp.setRelPath(corruptedName);
    assertThrows(FileNotFoundException.class, () -> tss.handlePossibleUTF8Error(fp, fnfe()));
  }

  @Test
  public void handlePossibleUTF8ErrorThrowsISEIfMultuplieMatches() throws IOException {
    FileStoreImpTss tss = new FileStoreImpTss();
    // simulate > 1 hit
    tss.rc = TransformerUtils.toList(new File("any1"), new File("any2"));
    File utf8File = RSpaceTestUtils.getResource(utf8FileName);
    FileProperty fp = setupFileStoreRoot(utf8File);
    String corruptedName = utf8File.getName().replace("ł", "?");
    // mimic messed up stream
    fp.setRelPath(corruptedName);
    assertThrows(IllegalStateException.class, () -> tss.handlePossibleUTF8Error(fp, fnfe()));
  }

  private FileNotFoundException fnfe() {
    return new FileNotFoundException("from corrupted path");
  }

  private FileProperty setupFileStoreRoot(File utf8File) {
    FileProperty fp = new FileProperty();
    FileStoreRoot root = new FileStoreRoot(FilenameUtils.getFullPath(utf8File.getAbsolutePath()));
    fp.setRoot(root);
    return fp;
  }
}
