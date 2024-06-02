package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.LINKEDRECORD_CLASS_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import org.jsoup.nodes.Element;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;

public class LinkedRecordConverterTest extends AbstractParserTest {

  @InjectMocks private LinkedRecordConverter linkedRecordConverter;

  @Before
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
        versionedLink, versionedLink.contains(expectedOidString + ": " + toLinkTo.getName()));
    assertTrue(versionedLink, versionedLink.contains("/globalId/" + expectedOidString));
    assertTrue(
        versionedLink, versionedLink.contains("data-globalid=\"" + expectedOidString + "\""));

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
