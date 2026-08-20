package com.researchspace.model;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;

public class RSpaceModelTestUtils {

  /**
   * Gets a named resource in src/test/resources/TestResources/, as a byte array.
   *
   * @param fileName
   * @return
   * @throws IOException
   */
  public static byte[] getResourceAsByteArray(String fileName) throws IOException {
    return IOUtils.toByteArray(getInputStreamOnFromTestResourcesFolder(fileName));
  }

  /**
   * Given the name of a file in src/test/resources/TestResources/, returns an Input stream to it.
   * CLient should close the input stream.
   *
   * @param fileName
   * @return
   * @throws IOException
   */
  public static InputStream getInputStreamOnFromTestResourcesFolder(String fileName)
      throws IOException {
    return RSpaceModelTestUtils.class
        .getClassLoader()
        .getResourceAsStream("TestResources/" + fileName);
  }

  /**
   * Gets a test file, specified by its name relative to TestResources folder.
   *
   * @param fileName
   * @return
   */
  public static File getResource(String fileName) {
    return new File("src/test/resources/TestResources/" + fileName);
  }

  /**
   * Gets a small PNG image for testing attachment behaviour
   *
   * @return
   */
  public static File getAnyImage() {
    return getResource("tester-core-model.png");
  }
}
