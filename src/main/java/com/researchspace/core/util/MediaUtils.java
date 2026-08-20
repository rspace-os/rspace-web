package com.researchspace.core.util;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.Instant;

/** Class for utility methods and constants for handling media file names and extension types */
public class MediaUtils {

  public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

  public static final String SVG_XML = "image/svg+xml";

  public static final String IMAGE_X_PNG = "image/x-png";

  public static final String APPLICATION_PDF = "application/pdf";

  public static final String APPLICATION_MSWORD = "application/msword";

  public static final String APPLICATION_ZIP = "application/zip";

  public static final String APP_ICON_FOLDER = "/images/icons/";

  public static final String APP_IMAGES_FOLDER = "/images/";

  public static final String VIDEO_MEDIA_FLDER_NAME = "Videos";

  public static final String AUDIO_MEDIA_FLDER_NAME = "Audios";

  public static final String IMAGES_MEDIA_FLDER_NAME = "Images";

  public static final String IMAGES_EXAMPLES_MEDIA_FLDER_NAME = "Examples";

  public static final String DOCUMENT_MEDIA_FLDER_NAME = "Documents";

  public static final String CHEMISTRY_MEDIA_FLDER_NAME = "Chemistry";

  public static final String MISC_MEDIA_FLDER_NAME = "Miscellaneous";

  public static final String DMP_MEDIA_FLDER_NAME = "DMPs";

  public static final String[] GALLERY_MEDIA_FOLDERS =
      new String[] {
        IMAGES_MEDIA_FLDER_NAME,
        DOCUMENT_MEDIA_FLDER_NAME,
        MISC_MEDIA_FLDER_NAME,
        CHEMISTRY_MEDIA_FLDER_NAME,
        AUDIO_MEDIA_FLDER_NAME,
        VIDEO_MEDIA_FLDER_NAME,
        DMP_MEDIA_FLDER_NAME
      };
  private static String[] chemFiles =
      new String[] {
        "skc",
        "mrv",
        "cxsmiles",
        "cxsmarts",
        "cdx",
        "cdxml",
        "csrdf",
        "cml",
        "csmol",
        "cssdf",
        "csrxn",
        "mol",
        "mol2",
        "pdb",
        "rxn",
        "rdf",
        "smiles",
        "smarts",
        "sdf",
        "inchi"
      };
  private static String[] dnaFiles =
      new String[] {"fa", "gb", "fasta", "dna", "seq", "sbd", "embl", "gbk", "ab1"};

  private static final String[] imageFiles =
      new String[] {"tif", "tiff", "jpeg", "jpg", "gif", "png", "bmp"};
  private static final String[] documentFiles =
      new String[] {
        "pdf", "odt", "ods", "odp", "doc", "rtf", "txt", "docx", "ppt", "pptx", "xls", "xlsx",
        "csv", "pps", "md"
      };
  private static final String[] audioFiles = new String[] {"mp3", "wma", "aac", "ogg", "wav"};
  private static final String[] videoFiles =
      new String[] {
        "mp4", "avi", "flv", "mpg", "mpeg", "3gp", "wmv", "mov", "hdmov", "m4v",
      };

  private static final Map<String, String[]> mediaFolderToType = new HashMap<>();

  static {
    mediaFolderToType.put(IMAGES_MEDIA_FLDER_NAME, imageFiles);
    mediaFolderToType.put(DOCUMENT_MEDIA_FLDER_NAME, documentFiles);
    mediaFolderToType.put(CHEMISTRY_MEDIA_FLDER_NAME, chemFiles);
    mediaFolderToType.put(AUDIO_MEDIA_FLDER_NAME, audioFiles);
    mediaFolderToType.put(VIDEO_MEDIA_FLDER_NAME, videoFiles);
    mediaFolderToType.put(MISC_MEDIA_FLDER_NAME, new String[] {""});
  }

  /**
   * Gets an array of supported file types for a particular Gallery section.
   *
   * @param galleryMediaFolderName A Gallery section name; one of the values returned by
   *     #getInsertableGalleryMediaFolders()
   * @return A String [] of supported extensions. For 'Miscellaneous' will return 'all'
   * @throws IllegalArgumentException if {@code}galleryMediaFolderName{@code} is not a valid value.
   */
  public static String[] getSupportedFileTypesForGallerySection(String galleryMediaFolderName) {
    if (!ArrayUtils.contains(getInsertableGalleryMediaFolders(), galleryMediaFolderName)) {
      throw new IllegalArgumentException("Unknown gallery folder: " + galleryMediaFolderName);
    }
    String[] types = mediaFolderToType.get(galleryMediaFolderName);
    return Arrays.copyOf(types, types.length);
  }

  /**
   * Gets array of gallery media folders that accept files to be uploaded to them
   *
   * @return a String []
   */
  public static String[] getInsertableGalleryMediaFolders() {
    return new String[] {
      IMAGES_MEDIA_FLDER_NAME,
      DOCUMENT_MEDIA_FLDER_NAME,
      MISC_MEDIA_FLDER_NAME,
      CHEMISTRY_MEDIA_FLDER_NAME,
      AUDIO_MEDIA_FLDER_NAME,
      VIDEO_MEDIA_FLDER_NAME
    };
  }

  /**
   * null-safe case-insensitive Boolean test as to whether the supplied extension is for an image
   * file
   *
   * @param extension
   * @return
   */
  public static boolean isImageFile(String extension) {
    return checkext(extension, imageFiles);
  }

  /**
   * null-safe case-insensitive Boolean test as to whether the supplied extension is for a document
   * file
   *
   * @param extension
   * @return
   */
  public static boolean isDocumentFile(String extension) {
    return checkext(extension, documentFiles);
  }

  private static boolean checkext(String extension, String[] toSearch) {
    return !StringUtils.isBlank(extension)
        && Arrays.stream(toSearch).anyMatch(extension.toLowerCase()::equals);
  }

  /**
   * null-safe case-insensitive Boolean test as to whether the supplied extension is for a Audio
   * file
   *
   * @param extension
   * @return
   */
  public static boolean isAudioFile(String extension) {
    return checkext(extension, audioFiles);
  }

  /**
   * Boolean test for whether the supplied file is an AV file, based on its extension
   *
   * @param file A non-null file with a name
   * @return
   */
  public static boolean isAVFile(File file) {
    return isVideoFile(FilenameUtils.getExtension(file.getName()))
        || isAudioFile(FilenameUtils.getExtension(file.getName()));
  }

  /**
   * null-safe case-insensitive Boolean test as to whether the supplied extension is for a Video
   * file
   *
   * @param extension
   * @return
   */
  public static boolean isVideoFile(String extension) {
    return checkext(extension, videoFiles);
  }

  /**
   * Gets a read-only list of supported DNA file extensions
   *
   * @return
   */
  public static List<String> supportedDNATypes() {
    return Collections.unmodifiableList(Arrays.asList(dnaFiles));
  }

  /**
   * Boolean test if <code>extension</code> is that of a chemistry file format supported by RSpace
   *
   * @param extension
   * @return
   */
  public static boolean isChemistryFile(String extension) {
    return checkext(extension, chemFiles);
  }

  /**
   * Boolean test if <code>extension</code> is that of a DNA file format supported by Snapgene
   *
   * @param extension
   * @return
   */
  public static boolean isDNAFile(String extension) {
    return checkext(extension, dnaFiles);
  }

  /**
   * Given a file extension, returns the Gallery folder name that the media file should belong to.
   *
   * @param extension
   * @return A String of the Gallery media folder name.
   */
  public static String extractFileType(String extension) {

    if (isAudioFile(extension)) {
      return AUDIO_MEDIA_FLDER_NAME;
    } else if (isVideoFile(extension)) {
      return VIDEO_MEDIA_FLDER_NAME;
    } else if (isImageFile(extension)) {
      return IMAGES_MEDIA_FLDER_NAME;
    } else if (isDocumentFile(extension)) {
      return DOCUMENT_MEDIA_FLDER_NAME;
    } else if (isChemistryFile(extension)) {
      return CHEMISTRY_MEDIA_FLDER_NAME;
    } else {
      return MISC_MEDIA_FLDER_NAME;
    }
  }

  /**
   * Given a full file path, returns the Gallery folder name that the media file should belong to.
   *
   * @param filePath
   * @return A String of the Gallery media folder name.
   */
  public static String extractFileTypeFromPath(String filePath) {
    String ext = FilenameUtils.getExtension(filePath);
    return extractFileType(ext.toLowerCase());
  }

  /**
   * @param originalFilename A String filename
   * @return the extension - the substring following the last '.' character in the name- or the full
   *     name if no '.' character is in the name, or <code>null</code> if the argument is <code>null
   *     </code> or empty
   */
  public static String getExtension(String originalFilename) {
    if (StringUtils.isBlank(originalFilename)) {
      return null;
    }
    if (originalFilename.indexOf('.') == -1) {
      return originalFilename;
    }

    String[] tempName = originalFilename.split(Pattern.quote("."));
    String extensionType = tempName[tempName.length - 1];
    return extensionType;
  }

  /**
   * Resolves the content type depends on the extension parameter
   *
   * @param
   * @return A String
   */
  public static String getContentTypeForFileExtension(String extension) {
    String result;
    if (extension.equalsIgnoreCase("txt")) {
      result = "text/plain";
    } else if (extension.equalsIgnoreCase("md")) {
      result = "text/markdown";
    } else if (extension.equalsIgnoreCase("rtf")) {
      result = "application/rtf";
    } else if (extension.equalsIgnoreCase("html")) {
      result = "text/html";
    } else if (extension.equalsIgnoreCase("csv")) {
      result = "text/csv";
    } else if (extension.equalsIgnoreCase("doc") || extension.equalsIgnoreCase("docx")) {
      result = APPLICATION_MSWORD;
    } else if (extension.equalsIgnoreCase("pps")
        || extension.equalsIgnoreCase("ppt")
        || extension.equalsIgnoreCase("pptx")) {
      result = "application/vnd.ms-powerpoint";
    } else if (extension.equalsIgnoreCase("xls") || extension.equalsIgnoreCase("xlsx")) {
      result = "application/vnd.ms-excel";
    } else if (extension.equalsIgnoreCase("pdf")) {
      result = APPLICATION_PDF;
    } else if (extension.equalsIgnoreCase("zip")) {
      result = APPLICATION_ZIP;
    } else if (extension.equalsIgnoreCase("mp3")) {
      result = "audio/mpeg";
    } else if (extension.equalsIgnoreCase("wav")) {
      result = "audio/x-wav";
    } else if (extension.equalsIgnoreCase("aac")) {
      result = "audio/aac";
    } else if (extension.equalsIgnoreCase("mid")) {
      result = "audio/mid";
    } else if (extension.equalsIgnoreCase("wma")) {
      result = "audio/x-ms-wma";
    } else if (extension.equalsIgnoreCase("ogg")) {
      result = "audio/ogg";
    } else if (extension.equalsIgnoreCase("mov")) {
      result = "video/quicktime";
    } else if (extension.equalsIgnoreCase("hdmov")) {
      result = "video/quicktime";
    } else if (extension.equalsIgnoreCase("m4v")) {
      result = "video/m4v";
    } else if (extension.equalsIgnoreCase("avi")) {
      result = "video/x-msvideo";
    } else if (extension.equalsIgnoreCase("wmv")) {
      result = "video/x-ms-wmv";
    } else if (extension.equalsIgnoreCase("mp4")) {
      result = "video/mp4";
    } else if (extension.equalsIgnoreCase("3gp")) {
      result = "video/3gp";
    } else if (extension.equalsIgnoreCase("flv")) {
      result = "video/x-flv";
    } else if (extension.equalsIgnoreCase("odt")) {
      result = "application/vnd.oasis.opendocument.text";
    } else if (extension.equalsIgnoreCase("ods")) {
      result = "application/vnd.oasis.opendocument.spreadsheet";
    } else if (extension.equalsIgnoreCase("odp")) {
      result = "application/vnd.oasis.opendocument.presentation";
    } else if (extension.equalsIgnoreCase("mpg")) {
      result = "video/mpeg";
    } else if (extension.equalsIgnoreCase("jpg") || extension.equalsIgnoreCase("jpeg")) {
      result = "image/jpeg";
    } else if (extension.equalsIgnoreCase("gif")) {
      result = "image/gif";
    } else if (extension.equalsIgnoreCase("png")) {
      result = IMAGE_X_PNG;
    } else {
      result = APPLICATION_OCTET_STREAM;
    }
    return result;
  }

  /**
   * Gets the resource path for an icon file name. Does not verify that such as resource exists;
   * this is solely to centralise the path construction.
   *
   * @param fileName of file icon E.g., 'txt.png'
   * @return the resource path for loading an icon into the browser from the web application
   */
  public static String getIconPathForSuffix(String fileName) {
    if (fileName.equalsIgnoreCase("getInfo12.png")) {
      return APP_IMAGES_FOLDER + fileName;
    }
    return APP_ICON_FOLDER + fileName;
  }

  /**
   * Given a file name, such as xxx.pdf, will insert a numeric timestamp to the name, e.g.
   * xxx_12345.pdf to make the possibility of duplicate files very unlikely.
   *
   * @param originalFileName
   * @return
   */
  public static String makeFileNameUnique(String originalFileName) {
    return FilenameUtils.getBaseName(originalFileName)
        + "_"
        + Instant.now().getMillis()
        + "."
        + FilenameUtils.getExtension(originalFileName);
  }
}
