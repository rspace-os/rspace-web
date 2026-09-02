package com.researchspace.service.archive.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.properties.IPropertyHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ExportObjectGeneratorTest {
  @Mock IPropertyHolder properties;
  @InjectMocks private ExportObjectGenerator exportObjectGenerator;
  TestFieldExporter testExporter = new TestFieldExporter();

  @Test
  public void testGetFileIdentity() {
    String filename = testExporter.getUniqueName("abc_ 'hello'");
    assertTrue(filename.contains("abchello"));
  }

  @Test
  public void testLinkReplacement() {

    String orgFieldContent1 =
        "<img id=\"156-102\" class=\"imageDropped\""
            + " src=\"/image/getImage/156-102/1416823077356?width=200&height=200\" alt=\"image\""
            + " width=\"250\" height=\"250\">";
    String orgFieldContent2 =
        "<img id=\"156-102\" class=\"imageDropped\""
            + " src=\"/image/getImage/156-102/1416823077356?width=200&amp;height=200\""
            + " alt=\"image\" width=\"250\" height=\"250\">";

    String linkToReplace = "/image/getImage/156-102/1416823077356?width=200&height=200";
    String testReplacement = "test";

    String expectedResult =
        "<img id=\"156-102\" class=\"imageDropped\" src=\"test\" alt=\"image\" width=\"250\""
            + " height=\"250\">";

    String result1 = testExporter.updateLink(orgFieldContent1, linkToReplace, testReplacement);
    String result2 = testExporter.updateLink(orgFieldContent2, linkToReplace, testReplacement);

    assertEquals(result1, expectedResult);
    assertEquals(result2, expectedResult);
  }
}
