package com.researchspace.document.importer;

import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.io.filefilter.FileFilterUtils.suffixFileFilter;

import com.researchspace.core.util.IoUtils;
import com.researchspace.documentconversion.ext.DocumentConversionError;
import com.researchspace.documentconversion.ext.DocumentConversionException;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.ConvertibleFile;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.FolderManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/** Generates an RSpace document from an incoming Word file */
public class MSWordImporter implements ExternalFileImporter {

  private static final Logger LOG = LoggerFactory.getLogger(MSWordImporter.class);
  // Temporary names come from these constants, never from the uploaded file name. The suffix
  // tells the converter the input format; anything else is reported as unsupported.
  private static final String TEMP_PREFIX = "word-import-";
  private static final Map<String, String> TEMP_SUFFIXES =
      Map.of(
          "doc", ".doc", "docx", ".docx", "odt", ".odt", "ott", ".ott", "rtf", ".rtf", "txt",
          ".txt");
  private static final String UNSUPPORTED_SUFFIX = ".unsupported";

  public void setFolderMgr(FolderManager folderMgr) {
    this.folderMgr = folderMgr;
  }

  private final DocumentConversionService converter;

  public MSWordImporter(DocumentConversionService dcs) {
    this.converter = dcs;
  }

  @Autowired private RSpaceDocumentCreator creator;
  @Autowired private FolderManager folderMgr;

  public void setCreator(RSpaceDocumentCreator creator) {
    this.creator = creator;
  }

  record TempConverterData(
      File tempFolder, File tempInput, String origDocName, ConversionResult result) {}

  @Override
  public BaseRecord create(
      InputStream wordFile, User user, Folder targetFolder, Folder imageFolder, String originalName)
      throws IOException {
    TempConverterData converted = doConversion(wordFile, originalName);
    try {
      if (converted.result().isSuccessful()) {
        File html =
            FileUtils.listFiles(
                    converted.tempFolder(),
                    FileFilterUtils.and(suffixFileFilter("html"), FileFilterUtils.fileFileFilter()),
                    null)
                .iterator()
                .next();
        extractImages(html, converted.tempFolder());
        HTMLContentProvider htmlProvider = new HTMLContentProvider(converted.tempFolder(), html);
        // create default image folder if one not already specified
        if (htmlProvider.hasImages() && imageFolder == null) {
          imageFolder =
              folderMgr.createGallerySubfolder(
                  converted.origDocName(), IMAGES_MEDIA_FLDER_NAME, user);
        }
        return creator.create(
            htmlProvider, targetFolder, imageFolder, converted.origDocName(), user);
      } else {
        throw new DocumentConversionException(
            DocumentConversionError.fromCode(converted.result().getErrorMsg())
                .orElse(DocumentConversionError.FAILED));
      }
    } finally {
      FileUtils.deleteQuietly(converted.tempInput());
      FileUtils.deleteQuietly(converted.tempFolder());
    }
  }

  private ConversionResult convert(InputStream wordFile, File tempInputFile, File tempOutputFolder)
      throws IOException {
    try (FileOutputStream tempFos = new FileOutputStream(tempInputFile)) {
      IOUtils.copy(wordFile, tempFos);
      File tempOutfile = File.createTempFile("word", ".html", tempOutputFolder);
      return converter.convert(new ConvertibleFile(tempInputFile), "html", tempOutfile);
    }
  }

  @Override
  public BaseRecord replace(InputStream wordFile, User user, Long toReplaceID, String originalName)
      throws IOException, DocumentAlreadyEditedException {
    TempConverterData converted = doConversion(wordFile, originalName);
    try {
      if (converted.result().isSuccessful()) {
        File html =
            FileUtils.listFiles(
                    converted.tempFolder(),
                    FileFilterUtils.and(suffixFileFilter("html"), FileFilterUtils.fileFileFilter()),
                    null)
                .iterator()
                .next();
        extractImages(html, converted.tempFolder());
        return creator.replace(
            toReplaceID,
            new HTMLContentProvider(converted.tempFolder(), html),
            converted.origDocName(),
            user);
      } else {
        throw new DocumentConversionException(
            DocumentConversionError.fromCode(converted.result().getErrorMsg())
                .orElse(DocumentConversionError.FAILED));
      }
    } finally {
      FileUtils.deleteQuietly(converted.tempInput());
      FileUtils.deleteQuietly(converted.tempFolder());
    }
  }

  private void extractImages(File html, File tempFolder) throws IOException {
    try {
      new DataUriExtractor().extract(html.toPath(), tempFolder.toPath());
    } catch (IOException e) {
      LOG.warn("Imported Word HTML contained invalid embedded image data", e);
      throw new DocumentConversionException(
          DocumentConversionError.fromCode(e.getMessage())
              .orElse(DocumentConversionError.INPUT_INVALID),
          e);
    }
  }

  private TempConverterData doConversion(InputStream wordFile, String originalName)
      throws IOException {
    final String origDocName = FilenameUtils.getBaseName(originalName);
    Path secureTempDir = IoUtils.createOrGetSecureTempDirectory();
    File tempFolder = Files.createTempDirectory(secureTempDir, TEMP_PREFIX).toFile();
    File tempFile = null;
    try {
      String suffix =
          TEMP_SUFFIXES.getOrDefault(
              getExtension(originalName).toLowerCase(Locale.ROOT), UNSUPPORTED_SUFFIX);
      tempFile = File.createTempFile(TEMP_PREFIX, suffix, secureTempDir.toFile());
      ConversionResult result = convert(wordFile, tempFile, tempFolder);
      return new TempConverterData(tempFolder, tempFile, origDocName, result);
    } catch (IOException | RuntimeException e) {
      LOG.warn("Word import conversion failed", e);
      FileUtils.deleteQuietly(tempFile);
      FileUtils.deleteQuietly(tempFolder);
      throw e;
    }
  }
}
