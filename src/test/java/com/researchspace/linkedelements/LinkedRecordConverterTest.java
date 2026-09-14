package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.LINKEDRECORD_CLASS_NAME;
import static org.assertj.core.api.Assertions.assertThat;
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
    assertThat(versionedLink)
        .as(versionedLink)
        .contains(expectedOidString + ": " + toLinkTo.getName());
    assertThat(versionedLink).as(versionedLink).contains("/globalId/" + expectedOidString);
    assertThat(versionedLink)
        .as(versionedLink)
        .contains("data-globalid=\"" + expectedOidString + "\"");

    // verify that converter finds a versioned id in content
    Element toconvert = getElementToConvert(versionedLink, LINKEDRECORD_CLASS_NAME);
    linkedRecordConverter.jsoup2LinkableElement(contents, toconvert);
    assertThat(contents.getLinkedRecordsWithRelativeUrl().getElements()).hasSize(1);
    assertTrue(
        contents.getLinkedRecordsWithRelativeUrl().getElements().get(0).getOid().hasVersionId());
    assertEquals(
        expectedOidString,
        contents.getLinkedRecordsWithRelativeUrl().getElements().get(0).getOid().getIdString());
  }
}
