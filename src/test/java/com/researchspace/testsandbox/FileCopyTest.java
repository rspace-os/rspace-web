package com.researchspace.testsandbox;

import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestRunnerController;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class FileCopyTest {

  @Test
  public void test() throws IOException {
    System.err.println(System.getProperty("os.name"));
    File original = RSpaceTestUtils.getResource("smartscotland3.pdf");
    FileInputStream fis = new FileInputStream(original);
    File out = new File("out.pdf");
    FileOutputStream fos = new FileOutputStream(out);
    IOUtils.copy(fis, fos);
    TestRunnerController.isJDK11();
  }
}
