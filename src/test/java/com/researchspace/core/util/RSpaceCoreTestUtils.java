package com.researchspace.core.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.IOUtils;

public class RSpaceCoreTestUtils {

  /**
   * Gets a named resource in src/test/resources/, as a byte array.
   *
   * @param fileName
   * @return
   * @throws IOException
   */
  public static byte[] getResourceAsByteArray(String fileName) throws IOException {
    return IOUtils.toByteArray(getInputStreamOnFromTestResourcesFolder(fileName));
  }

  /**
   * Given the name of a file in src/test/resources/, returns an Input stream to it. CLient should
   * close the input stream.
   *
   * @param fileName
   * @return
   * @throws IOException
   */
  public static InputStream getInputStreamOnFromTestResourcesFolder(String fileName)
      throws IOException {
    InputStream is = RSpaceCoreTestUtils.class.getClassLoader().getResourceAsStream(fileName);
    return is;
  }

  /**
   * Gets a test file, specified by its name relative to TestResources folder.
   *
   * @param fileName
   * @return
   */
  public static File getResource(String fileName) {
    File resource = new File("src/test/resources/" + fileName);
    return resource;
  }
}
