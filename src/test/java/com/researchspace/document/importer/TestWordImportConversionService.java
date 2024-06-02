package com.researchspace.document.importer;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;

/**
 * Sets up a folder containing the original file, the outfile of HTML, and any image files that
 * linked to by the HTML.
 */
public class TestWordImportConversionService implements DocumentConversionService {

  /**
   * @param expectedResultsFolder folder of source and converted files
   * @param convertedFile The HTML version of the Word file
   */
  public TestWordImportConversionService(File expectedResultsFolder, File convertedFile) {
    super();
    this.expectedResultsFolder = expectedResultsFolder;
    this.convertedFile = convertedFile;
  }

  private File expectedResultsFolder;
  private boolean supportsConversion = true;
  private File convertedFile;

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension) {
    return null;
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension, File outfile) {
    Collection<File> filesToCopy =
        FileUtils.listFiles(expectedResultsFolder, FileFilterUtils.trueFileFilter(), null);
    for (File file : filesToCopy) {
      try {
        FileUtils.copyFileToDirectory(file, outfile.getParentFile());
        IOUtils.copy(new FileInputStream(convertedFile), new FileOutputStream(outfile));
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return new ConversionResult(outfile, "text/html");
  }

  @Override
  public boolean supportsConversion(Convertible toConvert, String to) {
    return supportsConversion;
  }

  public File getExpectedResultsFolder() {
    return expectedResultsFolder;
  }

  public void setExpectedResultsFolder(File expectedResultsFolder) {
    this.expectedResultsFolder = expectedResultsFolder;
  }

  public boolean isSupportsConversion() {
    return supportsConversion;
  }

  public void setSupportsConversion(boolean supportsConversion) {
    this.supportsConversion = supportsConversion;
  }
}
