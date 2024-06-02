package com.researchspace.documentconversion.ext;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.ConvertibleFile;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

@ContextConfiguration(classes = AsposeWebAppClientTestIT.class)
// docker container running aspose-web 0.27.0, this is from point of view of kudu
@TestPropertySource(properties = "aspose.web.url=http://howler.researchspace.com:8083")
@Configuration
@RunWith(ConditionalTestRunner.class)
public class AsposeWebAppClientTestIT extends AbstractJUnit4SpringContextTests {

  private @Autowired Environment env;

  AsposeWebAppClient client;
  File wordFile = RSpaceTestUtils.getResource("PowerPasteTesting_RSpace.docx");
  final int EXPECTED_WORDFILE_TO_PDF_LENGTH = 620482;
  final int EXPECTED_WORDFILE_TO_HTML_LENGTH = 25378;
  final int EXPECTED_HTML_TO_DOC = 166400;

  @Before
  public void before() throws URISyntaxException {
    URI uri = new URI(env.getProperty("aspose.web.url"));
    client = new AsposeWebAppClient(uri, null, () -> "AsposeWebAppClientTestIT");
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void wordToPdfClient() {
    ConversionResult result = client.convert(new ConvertibleFile(wordFile), "pdf");
    assertTrue(result.isSuccessful());
    File converted = result.getConverted();
    assertNotNull(converted);
    assertEquals(EXPECTED_WORDFILE_TO_PDF_LENGTH, converted.length());
  }

  // import from Word use-case
  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void wordToHtmlClient() throws IOException {
    File tempFolder =
        Files.createTempDirectory(getBaseName(wordFile.getName()), new FileAttribute[] {}).toFile();
    File outFile = File.createTempFile(getBaseName(wordFile.getName()), ".html", tempFolder);
    ConversionResult result = client.convert(new ConvertibleFile(wordFile), "html", outFile);
    assertTrue(result.isSuccessful());
    File converted = result.getConverted();
    assertNotNull(converted);
    assertEquals(EXPECTED_WORDFILE_TO_HTML_LENGTH, converted.length());
    // html + 3 image files
    assertEquals(4, tempFolder.listFiles().length);
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void htmlToWord() throws IOException {
    File inputFolder = new File("src/test/resources/TestResources/word2rspace/powerpasteHtml");
    File inputHtml = new File(inputFolder, "PowerPasteTesting_RSpace.html");
    ConversionResult result = client.convert(new ConvertibleFile(inputHtml), "doc");
    assertTrue(result.isSuccessful());
    File converted = result.getConverted();
    assertNotNull(converted);
    assertEquals(EXPECTED_HTML_TO_DOC, converted.length());
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void version() {
    SemanticVersion result = client.getVersion();
    assertNotNull(result);
    assertThat(SemanticVersion.UNKNOWN_VERSION, Matchers.not(Matchers.equalTo(result)));
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void versionFailsGracefully() throws URISyntaxException {
    URI unknownUri = new URI("http://unknownURL.com");
    client = new AsposeWebAppClient(unknownUri, null, () -> "customerID");
    SemanticVersion result = client.getVersion();
    assertNotNull(result);
    assertThat(SemanticVersion.UNKNOWN_VERSION, Matchers.equalTo(result));
  }
}
