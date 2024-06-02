package com.researchspace.archive;

import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.model.core.IRSpaceDoc;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

/** Utility class for handling name conventions and manipulations for archives. */
public class ArchiveUtils {

  private ArchiveUtils() {
    // private constructor to prevent default constructor
    // for utility class which shouldn't be instantiated
  }

  /**
   * Given folder, returns a filename for an HTML export index file in the form:
   *
   * <p><code>
   *   name-id.html
   * </code> where the name has non-alphanumeric characters removed.
   */
  public static String getFolderIndexName(IRSpaceDoc folder) {
    return filterArchiveNameString(folder.getName()) + "-" + folder.getId() + ".html";
  }

  /**
   * Replaces spaces with hyphens and removes any other non-alphanumeric characters.
   *
   * @return a filtered string
   */
  public static String filterArchiveNameString(String name) {
    String alphanumericNameWithSpacesUnderscoresHyphens = name.replaceAll("[^A-Za-z0-9\\s_-]", "");
    return alphanumericNameWithSpacesUnderscoresHyphens.replaceAll("\\s+|_+", "-");
  }

  /** Given a prefix, appends with secure random string to generate unguessable URL RSPAC-570 */
  public static String getUniqueName(String prefix) {
    String newPrefix = "";
    if (!StringUtils.isBlank(prefix)) {
      newPrefix = prefix.replaceAll("[^A-Za-z0-9\\-]+", "");
    }
    return newPrefix + SecureStringUtils.getURLSafeSecureRandomString(10);
  }

  /** Creates a global id link */
  public static String getAbsoluteGlobalLink(String globalId, String serverUrl) {
    String url = (serverUrl.endsWith("/")) ? "globalId/" : "/globalId/";
    return serverUrl + url + globalId;
  }

  /**
   * Constructs download link given an archive result and a prefix. <br>
   * Does not check file exists, this is just String manipulation
   */
  public static String getExportDownloadLink(String serverURLPrefix, ArchiveResult result) {
    return getExportDownloadLink(serverURLPrefix, result.getExportFile().getName());
  }

  /**
   * Constructs download link given an archive result and the name of a file <br>
   * Does not check file exists, this is just String manipulation
   */
  public static String getExportDownloadLink(String serverURLPrefix, String filename) {
    return String.format("%s/export/ajax/downloadArchive/%s", serverURLPrefix, filename);
  }

  public static String getApiExportDownloadLink(String serverURLPrefix, String filename) {
    return String.format("%s/api/v1/export/%s", serverURLPrefix, filename);
  }

  public static String getExportReportLink(String serverURLPrefix, Long exportNotificationId) {
    return String.format("%s/export/report/%d", serverURLPrefix, exportNotificationId);
  }

  public static long calculateChecksum(File zipFile) throws IOException {
    return FileUtils.checksumCRC32(zipFile);
  }

  /**
   * Calculate checksum based on MD5 which takes into account all files' / folders' content and
   * their relative filepath.
   *
   * @param folder folder to calculate checksum of
   */
  public static String calculateFolderContentsChecksum(File folder) throws IOException {
    try (Stream<Path> paths = Files.walk(folder.toPath())) {
      MessageDigest contentsCheckSum = MessageDigest.getInstance("MD5");
      paths
          .filter((Path path) -> !path.toFile().isHidden())
          .sorted()
          .forEachOrdered(
              (Path path) -> {
                String contentsHash;

                if (path.toFile().isFile()) {
                  try (FileInputStream input = new FileInputStream(path.toFile())) {
                    contentsHash = DigestUtils.md5Hex(input);
                  } catch (IOException e) {
                    throw new IllegalStateException(e);
                  }
                } else {
                  contentsHash = "folder";
                }

                contentsCheckSum.update(folder.toPath().relativize(path).toString().getBytes());
                contentsCheckSum.update(contentsHash.getBytes());
              });
      return Hex.encodeHexString(contentsCheckSum.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
