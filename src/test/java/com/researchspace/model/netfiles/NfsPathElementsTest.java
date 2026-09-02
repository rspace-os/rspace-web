package com.researchspace.model.netfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NfsPathElementsTest {
  NfsPathElements pathElements;
  // these are from real examples on pangolin
  String[] exampleFileSystemUrls =
      new String[] {
        "pangolin.researchspace.com:22",
        "smb://ecdfnas.ecdf.ed.ac.uk",
        "sg.datastore.ed.ac.uk:22222",
        "smb://demo.researchspace.com",
        "smb://localhost/samba-folder/",
        "staff.ssh.inf.ed.ac.uk"
      };

  String[] exampleFileStorePaths =
      new String[] {
        "file_store",
        "mkdir",
        "rspace/scripts",
        "LuceneFTsearchIndices",
        "cse/inf/rspacestore",
        "cse/inf/rspacestore",
        "cse/inf/rspacestore",
        "csce/datastore/inf/users/v1mkowa3",
        "bin",
        "rspace",
        "cse/inf",
        "rspace/filestores/cloud_filestore",
        "cmvm",
        "rspace/aspose",
        "cse/inf",
        "samba-folder",
        "samba-folder",
        "rspace/LicenseServerLogs",
        "cmvm/datastore",
        "csce/datastore/inf",
        "samba-folder",
        "/",
        "file_store/Images",
        "/",
        "/afs/inf.ed.ac.uk/group/ideal/mingjun/software/ml",
        "Bio Multi Meßsystem"
      };

  String[] relPaths = new String[] {"/a.txt", "b.txt", "a/b/c.txt"};

  @BeforeEach
  public void setUp() throws Exception {}

  @AfterEach
  public void tearDown() throws Exception {}

  @Test
  public void toFullPathUsingRealExamples() {
    for (String sysPath : exampleFileSystemUrls) {
      for (String storePath : exampleFileStorePaths) {
        for (String relPath : relPaths) {
          pathElements = new NfsPathElements(sysPath, storePath, relPath);
          assertNotNull(pathElements.toFullPath());
        }
      }
    }
  }

  @Test
  public void toFullPathPathologicalCases() {
    new NfsPathElements("/", "/", "/").toFullPath();
    assertEquals("", new NfsPathElements("", "", "").toFullPath());
  }
}
