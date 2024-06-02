package com.researchspace.document.importer;

import static com.researchspace.core.util.MediaUtils.IMAGES_MEDIA_FLDER_NAME;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.io.filefilter.FileFilterUtils.suffixFileFilter;

import com.researchspace.core.util.IoUtils;
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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

/** Generates an RSpace document from an incoming Word file */
public class MSWordImporter implements ExternalFileImporter {

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

  static class TempConverterData {
    File tempFolder;
    String origDocName;
    ConversionResult result;
  }

  @Override
  public BaseRecord create(
      InputStream wordFile, User user, Folder targetFolder, Folder imageFolder, String originalName)
      throws IOException {
    TempConverterData converted = doConversion(wordFile, originalName);
    if (converted.result.isSuccessful()) {
      File html =
          FileUtils.listFiles(
                  converted.tempFolder,
                  FileFilterUtils.and(suffixFileFilter("html"), FileFilterUtils.fileFileFilter()),
                  null)
              .iterator()
              .next();
      HTMLContentProvider htmlProvider = new HTMLContentProvider(converted.tempFolder, html);
      // create default image folder if one not already specified
      if (htmlProvider.hasImages() && imageFolder == null) {
        imageFolder =
            folderMgr.createGallerySubfolder(converted.origDocName, IMAGES_MEDIA_FLDER_NAME, user);
      }
      return creator.create(htmlProvider, targetFolder, imageFolder, converted.origDocName, user);
    } else {
      throw new IllegalStateException(converted.result.getErrorMsg());
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
    if (converted.result.isSuccessful()) {
      File html =
          FileUtils.listFiles(
                  converted.tempFolder,
                  FileFilterUtils.and(suffixFileFilter("html"), FileFilterUtils.fileFileFilter()),
                  null)
              .iterator()
              .next();
      return creator.replace(
          toReplaceID,
          new HTMLContentProvider(converted.tempFolder, html),
          converted.origDocName,
          user);
    } else {
      throw new IllegalStateException(converted.result.getErrorMsg());
    }
  }

  private TempConverterData doConversion(InputStream wordFile, String originalName)
      throws IOException {
    final String origDocName = FilenameUtils.getBaseName(originalName);
    // this is needed so that folders don't have spaces in names,  this breaks the aspose command
    // line
    String origDocNameNoSpaces = StringUtils.replaceChars(origDocName, " ", "");
    Path secureTempDir = IoUtils.createOrGetSecureTempDirectory();
    File tempFolder = Files.createTempDirectory(secureTempDir, origDocNameNoSpaces).toFile();
    File tempFile =
        File.createTempFile(
            "origDocName", "." + getExtension(originalName), secureTempDir.toFile());
    ConversionResult result = convert(wordFile, tempFile, tempFolder);
    TempConverterData data = new TempConverterData();
    data.origDocName = origDocName;
    data.tempFolder = tempFolder;
    data.result = result;
    return data;
  }
}
