package com.researchspace.documentconversion.ext;

import com.researchspace.documentconversion.spi.AbstractDocumentConversionService;
import com.researchspace.documentconversion.spi.ConversionFailedException;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncDocumentConverterService extends AbstractDocumentConversionService
    implements DocumentConversionService {
  private static final int WAIT_DELAY = 30;

  Logger log = LoggerFactory.getLogger(AsyncDocumentConverterService.class);

  private AsyncDocConverterService asposeAppRunner;

  public AsyncDocumentConverterService(AsyncDocConverterService asposeAppService) {
    this.asposeAppRunner = asposeAppService;
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension) {
    throw new UnsupportedOperationException(
        "This operation is not supported, use method variant  with specified outfile.");
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension, File outfile) {
    log.info("Submitting {}", toConvert);
    Future<ConversionResult> future =
        asposeAppRunner.submitAsync(toConvert, outputExtension, outfile);
    try {
      StopWatch sq = new StopWatch();
      sq.start();
      ConversionResult result = future.get(WAIT_DELAY, TimeUnit.SECONDS);
      sq.stop();
      log.info("Waited for {} millis", sq.getTime());
      return result;
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw new ConversionFailedException(e.getMessage(), e);
    }
  }

  @Override
  public boolean supportsConversion(Convertible toConvert, String to) {
    return asposeAppRunner.supportsConversion(toConvert, to);
  }
}
