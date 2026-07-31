package com.researchspace.documentconversion.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.Convertible;
import com.researchspace.documentconversion.spi.ConvertibleFile;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import org.junit.jupiter.api.Test;

public class AsposeAppInvokerTest {

  class AppInvokerTSS extends AsposeAppInvoker {
    public AppInvokerTSS(ConversionChecker conversionChecker) {
      super(conversionChecker);
    }

    int exitCode;
    String version;

    int doRunCommandLine(Convertible toConvert, String outputExtension, File outfile) {
      return exitCode;
    }

    @Override
    String doGetVersion() {
      return version;
    }
  }

  File testIn = RSpaceTestUtils.getResource("PowerPasteTesting_RSpace.docx");

  @Test
  public void convertHappyCase() {
    AppInvokerTSS invoker = setupAppInvoker();
    ConversionResult cr = invoker.convert(new ConvertibleFile(testIn), "pdf", new File("out.pdf"));
    assertTrue(cr.isSuccessful());
  }

  private AppInvokerTSS setupAppInvoker() {
    AppInvokerTSS invoker = new AppInvokerTSS((to, from) -> false);
    invoker.setAsposeApp("aspose.jar");
    invoker.setAsposeLicensePath("license/path/Aspose-Total-Java.lic");
    invoker.setAsposeLogfile("out.log");
    invoker.setAsposeLogLevel("INFO");
    invoker.setAsposeJvmArgs("-Xms128m -Xmx1024m");
    invoker.exitCode = 0;
    return invoker;
  }

  @Test
  public void convertHandlesNonZeroExitCode() {
    AppInvokerTSS invoker = setupAppInvoker();
    invoker.exitCode = 1;
    ConversionResult cr = invoker.convert(new ConvertibleFile(testIn), "pdf", new File("out.pdf"));
    assertFalse(cr.isSuccessful());
    assertNull(cr.getConverted());
  }

  @Test
  public void convertRequiresAsposeAppSet() {
    AppInvokerTSS invoker = setupAppInvoker();
    invoker.setAsposeApp("");
    assertThrows(IllegalArgumentException.class, () -> runConversion(invoker));
  }

  @Test
  public void convertRequiresAsposeLicenseSet() {
    AppInvokerTSS invoker = setupAppInvoker();
    invoker.setAsposeLicensePath("");
    assertThrows(IllegalArgumentException.class, () -> runConversion(invoker));
  }

  @Test
  public void convertRequiresAsposeLogFileSet() {
    AppInvokerTSS invoker = setupAppInvoker();
    invoker.setAsposeLogfile("");
    assertThrows(IllegalArgumentException.class, () -> runConversion(invoker));
  }

  @Test
  public void convertLoggingLevelOptional() {
    AppInvokerTSS invoker = setupAppInvoker();
    invoker.setAsposeLogLevel("");
    assertTrue(runConversion(invoker).isSuccessful());
  }

  @Test
  public void getVersion() {
    AppInvokerTSS invoker = setupAppInvoker();
    invoker.version = "This is AsposeDocConverter version: 0.0.5-SNAPSHOT";
    SemanticVersion version = invoker.getVersion();
    SemanticVersion expected = new SemanticVersion(0, 0, 5, "SNAPSHOT");
    assertEquals(expected, version);
  }

  @Test
  public void getUnparseableVersionReturnsNullVersion() {
    AppInvokerTSS invoker = setupAppInvoker();
    invoker.version = "This is AsposeDocConverter";
    SemanticVersion version = invoker.getVersion();
    SemanticVersion expected = new SemanticVersion("0");
    assertEquals(expected, version);
  }

  @Test
  public void convertJVMArgsOptional() {
    AppInvokerTSS invoker = setupAppInvoker();
    invoker.setAsposeJvmArgs("");
    assertTrue(runConversion(invoker).isSuccessful());
  }

  private ConversionResult runConversion(AppInvokerTSS invoker) {
    return invoker.convert(new ConvertibleFile(testIn), "pdf", new File("out.pdf"));
  }
}
