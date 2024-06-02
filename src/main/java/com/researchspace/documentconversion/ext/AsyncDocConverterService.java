package com.researchspace.documentconversion.ext;

import com.researchspace.core.util.version.Versionable;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import java.io.File;
import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.Async;

/**
 * EXtends {@link DocumentConversionService} in order to provide an method using Spring's {@link
 * Async} framework that returns a {@link Future}
 */
public interface AsyncDocConverterService extends DocumentConversionService, Versionable {
  /**
   * Converts a document asynchronously
   *
   * @param toConvert
   * @param outputExt
   * @param outFile
   * @return a {@link Future}
   */
  @Async(value = "docConverter")
  public Future<ConversionResult> submitAsync(
      Convertible toConvert, String outputExt, File outFile);
}
