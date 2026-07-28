package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.LINKEDRECORD_CLASS_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.TestFactory;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

public class LinkedRecordConverterTest extends AbstractParserTest {

  @InjectMocks private LinkedRecordConverter linkedRecordConverter;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void handleVersionedLink() {
    StructuredDocument toLinkTo = TestFactory.createAnySD();
    toLinkTo.setName("doc SD515");
    toLinkTo.setId(515L);
    String versionedLink = rtu.generateURLStringForVersionedInternalLink(toLinkTo);

    String expectedOidString = toLinkTo.getOidWithVersion().toString();
    assertTrue(
        versionedLink.contains(expectedOidString + ": " + toLinkTo.getName()), versionedLink);
    assertTrue(versionedLink.contains("/globalId/" + expectedOidString), versionedLink);
    assertTrue(
        versionedLink.contains("data-globalid=\"" + expectedOidString + "\""), versionedLink);

    // verify that converter finds a versioned id in content
    Element toconvert = getElementToConvert(versionedLink, LINKEDRECORD_CLASS_NAME);
    linkedRecordConverter.jsoup2LinkableElement(contents, toconvert);
    assertEquals(1, contents.getLinkedRecordsWithRelativeUrl().getElements().size());
    assertTrue(
        contents.getLinkedRecordsWithRelativeUrl().getElements().get(0).getOid().hasVersionId());
    assertEquals(
        expectedOidString,
        contents.getLinkedRecordsWithRelativeUrl().getElements().get(0).getOid().getIdString());
  }
}
