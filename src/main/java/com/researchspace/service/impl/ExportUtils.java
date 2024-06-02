package com.researchspace.service.impl;

import static com.researchspace.core.util.MediaUtils.getContentTypeForFileExtension;
import static org.apache.commons.io.FilenameUtils.getExtension;

import com.researchspace.core.util.IoUtils;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.ConvertibleFile;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.service.FileStoreMetaManager;
import com.researchspace.service.archive.IExportUtils;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class ExportUtils implements IExportUtils {

  protected static final Logger log = LoggerFactory.getLogger(ExportUtils.class);

  @Autowired
  @Qualifier("compositeFileStore")
  private FileStore fileStore;

  @Autowired
  @Qualifier("compositeDocumentConverter")
  private DocumentConversionService docConverter;

  private @Autowired FileStoreMetaManager fileStoreMetaMgr;

  /**
   * @throws FileNotFoundException if couldn't find file in filestore
   */
  @Override
  public void display(String exportFilename, User user, HttpServletResponse res)
      throws IOException, URISyntaxException {
    File f1 = getFileFromFileStore(exportFilename, user);
    if (f1 == null) {
      throw new FileNotFoundException(
          String.format("Could not retrieve file %s from filestore", exportFilename));
    }
    ServletOutputStream outStream = res.getOutputStream();
    try (FileInputStream fis = new FileInputStream(f1);
        BufferedOutputStream bos = new BufferedOutputStream(outStream)) {
      String contentType = getContentTypeForFileExtension(getExtension(exportFilename));
      res.setContentType(contentType);
      IOUtils.copy(fis, bos);
      bos.flush();
    }
  }

  @Override
  public ConversionResult createThumbnailForExport(String exportFileName, User user)
      throws URISyntaxException, IOException {
    File exportedFile = getFileFromFileStore(exportFileName, user);
    if (exportedFile == null) {
      return null;
    }
    ConvertibleFile convertibleFile = new ConvertibleFile(exportedFile);
    File secureTmpDir = IoUtils.createOrGetSecureTempDirectory().toFile();
    File outFile =
        File.createTempFile(FilenameUtils.getBaseName(exportFileName), ".png", secureTmpDir);
    return docConverter.convert(convertibleFile, "png", outFile);
  }

  private File getFileFromFileStore(String exportname, User user) throws IOException {
    Map<String, String> filePropertySearch = new HashMap<>();
    if (user != null) {
      filePropertySearch.put("fileUser", user.getUsername());
    }

    filePropertySearch.put("fileName", exportname);

    List<FileProperty> fps = fileStoreMetaMgr.findProperties(filePropertySearch);
    if (!fps.isEmpty()) {
      return fileStore.findFile(fps.get(0));
    } else {
      return null;
    }
  }

  @Override
  public File createFolder(String path) throws IOException {
    File dir = new File(path);
    createFolder(dir);
    return dir;
  }

  @Override
  public void createFolder(File dir) throws IOException {
    FileUtils.forceMkdir(dir);
  }
}
