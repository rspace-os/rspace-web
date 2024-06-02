package com.researchspace.documentconversion.ext;

import static org.apache.commons.lang.StringUtils.isEmpty;

import com.researchspace.core.util.CommandLineRunner;
import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.documentconversion.spi.AbstractDocumentConversionService;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import java.io.File;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.helper.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.AsyncResult;

/**
 * Invokes Aspose converter as a separate application in a separate process via the command line. It
 * requires an Aspose Converter application installed. <br>
 * See aspose-documentconversion project for more details.
 *
 * <p>This implementation runs either in the calling thread, or asyncronously using Spring's task
 * executor service (via <code>submitAsync</code>).
 */
public class AsposeAppInvoker extends AbstractDocumentConversionService
    implements AsyncDocConverterService {

  private static final int TIMEOUT_MILLIS = 20000;

  Logger log = LoggerFactory.getLogger(AsposeAppInvoker.class);

  private ConversionChecker conversionChecker;

  public AsposeAppInvoker(ConversionChecker conversionChecker) {
    super();
    this.conversionChecker = conversionChecker;
  }

  @Value("${aspose.license}")
  private String asposeLicensePath;

  void setAsposeLicensePath(String asposeLicensePath) {
    this.asposeLicensePath = asposeLicensePath;
  }

  @Value("${aspose.app}")
  private String asposeApp;

  void setAsposeApp(String asposeApp) {
    this.asposeApp = asposeApp;
  }

  @Value("${aspose.logfile}")
  private String asposeLogfile;

  void setAsposeLogfile(String asposeLogfile) {
    this.asposeLogfile = asposeLogfile;
  }

  @Value("${aspose.jvmArgs}")
  private String asposeJvmArgs;

  void setAsposeJvmArgs(String jvmArgs) {
    this.asposeJvmArgs = jvmArgs;
  }

  // uses default log level if nothing set
  private static final String DEFAULT_LOG_LEVEL = "WARN";

  @Value("${aspose.loglevel}")
  private String asposeLogLevel = DEFAULT_LOG_LEVEL;

  void setAsposeLogLevel(String asposeLogLevel) {
    this.asposeLogLevel = asposeLogLevel;
    if (isEmpty(asposeLogLevel)) {
      this.asposeLogLevel = DEFAULT_LOG_LEVEL;
    }
  }

  void setConversionChecker(ConversionChecker conversionChecker) {
    this.conversionChecker = conversionChecker;
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension) {
    throw new UnsupportedOperationException("This must be invoked with a known outfile");
  }

  @Override
  public ConversionResult convert(Convertible toConvert, String outputExtension, File outfile) {

    Validate.notEmpty(asposeLogfile, "Aspose Log file not set");
    Validate.notEmpty(asposeApp, "Aspose application jar not set");
    Validate.notEmpty(asposeLicensePath, "Aspose license location not set");
    Validate.notEmpty(outputExtension, "Output extension not set");
    Validate.notEmpty(asposeLogLevel, "Log level not set");

    // remove any '.'
    outputExtension = outputExtension.replace(".", "");
    int rc = doRunCommandLine(toConvert, outputExtension, outfile);
    if (rc == 0) {
      return new ConversionResult(outfile, getContentTypeForExtension(outputExtension));
    } else {
      return new ConversionResult("Document conversion failed with error code " + rc);
    }
  }

  int doRunCommandLine(Convertible toConvert, String outputExtension, File outfile) {
    CommandLineRunner runner = new CommandLineRunner();
    String commandline = constructCommandLine(toConvert, outputExtension, outfile);
    log.info("Launching document convertor with command line {}", commandline);
    return runner.runCommandReturningExitStatus(commandline, TIMEOUT_MILLIS);
  }

  String constructCommandLine(Convertible toConvert, String outputExtension, File outfile) {
    return "java "
        + asposeJvmArgs
        + " -Dlogfile="
        + asposeLogfile
        + " -Dlog.level="
        + asposeLogLevel
        + " -jar "
        + asposeApp
        + " -l "
        + asposeLicensePath
        + " -i "
        + getFilePathFromURI(toConvert)
        + " -o "
        + outfile.getAbsolutePath()
        + " -f "
        + outputExtension;
  }

  @Override
  public boolean supportsConversion(Convertible toConvert, String toFormat) {
    String fromFormat = FilenameUtils.getExtension(toConvert.getName());
    return conversionChecker.supportsConversion(fromFormat, toFormat);
  }

  @Override
  public Future<ConversionResult> submitAsync(
      Convertible toConvert, String outputExt, File outFile) {
    ConversionResult result = convert(toConvert, outputExt, outFile);
    return new AsyncResult<>(result);
  }

  @Override
  public SemanticVersion getVersion() {
    String output = doGetVersion();
    Pattern p = Pattern.compile("(\\d+\\.\\d+\\.\\d+[\\S]*)");
    Matcher m = p.matcher(output);
    if (m.find()) {
      return new SemanticVersion(m.group());
    } else {
      log.warn("Couldn't get Aspose converter version -   was [{}]", output);
      return SemanticVersion.UNKNOWN_VERSION;
    }
  }

  @Override
  public String getVersionMessage() {
    return doGetVersion();
  }

  String doGetVersion() {
    CommandLineRunner runner = new CommandLineRunner();
    String line = " java  -Dlogfile=" + asposeLogfile + " -jar " + asposeApp + " -v";
    log.info("Running command line {}", line);
    return runner.runCommandReturningOutput(line);
  }

  @Override
  public String getDescription() {
    return "Aspose App Document converter";
  }
}
