package com.researchspace.core.testutil;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Collection of useful methods for asserting content of file-system folders */
public class FileTestUtils {
  static Logger log = LoggerFactory.getLogger(FileTestUtils.class);

  /**
   * Returns true if the specified folder, or any of its subfolders, has a file starting with the
   * given name
   *
   * @param name
   * @param folder
   * @return
   */
  public static boolean hasFileWithNAmePrefix(String name, File folder) {
    return !FileUtils.listFiles(
            folder, FileFilterUtils.prefixFileFilter(name), TrueFileFilter.INSTANCE)
        .isEmpty();
  }

  /**
   * Boolean test for the content containing the given regular expression
   *
   * @param htmlFile
   * @param regex
   * @return <code>true</code> if the file contains the regexp, <code>false</code> otherwise
   * @throws IOException
   */
  public static boolean fileContainsContent(File htmlFile, String regex) throws IOException {
    String content = FileUtils.readFileToString(htmlFile);
    Pattern p = Pattern.compile(regex);
    Matcher m = p.matcher(content);
    boolean found = false;
    while (m.find()) {
      found = true;
      log.info("Found match for [{}] at {}", regex, m.group());
    }
    return found;
  }

  /**
   * Counts the number of subfolders in the argument folder
   *
   * @param file a Folder
   * @return
   */
  public static int getFolderCount(File file) {
    return FileUtils.listFilesAndDirs(file, FalseFileFilter.FALSE, TrueFileFilter.TRUE).size() - 1;
  }

  /**
   * Asserts that <code>folder</code> contains a file with name <code>testFileName</code>
   *
   * @param folder A {@link File} that is a directory
   * @param testFileName
   * @return <code>true</code> if the file is present, <code>false</code> otherwise.
   * @throws IllegalArgumentException if folder is not a directory, or <code>testFileName</code> is
   *     empty.
   */
  public static boolean assertFolderHasFile(File folder, String testFileName) {
    Validate.notEmpty(testFileName, "testFileName cannot be an empty string");
    Validate.isTrue(
        folder != null && folder.exists() && folder.isDirectory(), "Folder must be a directory");
    return !FileUtils.listFiles(folder, FileFilterUtils.nameFileFilter(testFileName), null)
        .isEmpty();
  }
}
