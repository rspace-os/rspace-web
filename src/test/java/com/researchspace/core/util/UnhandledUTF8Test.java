package com.researchspace.core.util;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class UnhandledUTF8Test {
  String utf8FileName = "widok_z_łazika.png";

  @Before
  public void setUp() throws Exception {}

  @After
  public void tearDown() throws Exception {}

  @Test
  public void corruptedMatchesRealFile() throws IOException {
    String corruptedName = utf8FileName.replace("ł", "?");
    UnhandledUTF8FileFilter ff = new UnhandledUTF8FileFilter(corruptedName);
    assertTrue(ff.accept(null, utf8FileName));
  }

  @Test
  public void ARealFileThatContainsMetaChars() throws IOException {
    String actualName = utf8FileName.replace("w", "?");
    String corruptedName = actualName.replace("ł", "?");
    UnhandledUTF8FileFilter ff = new UnhandledUTF8FileFilter(corruptedName);
    assertTrue(ff.accept(null, utf8FileName));
  }

  @Test
  public void ARealFileThatContainsMetaCharsWithPrefix() throws IOException {
    String actualName = "file:/a/b/c/" + utf8FileName.replace("w", "?");
    String corruptedName = actualName.replace("ł", "?");
    actualName.substring(5, actualName.lastIndexOf(File.separator) + 1);
  }
}
