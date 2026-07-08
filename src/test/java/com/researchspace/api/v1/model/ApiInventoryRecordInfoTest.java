package com.researchspace.api.v1.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction;
import com.researchspace.model.FileProperty;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.web.util.UriComponentsBuilder;

public class ApiInventoryRecordInfoTest {
  private ApiInventoryRecordInfo testee;
  private ApiInventoryRecordInfo original;

  @Before
  public void setUp() {
    testee =
        new ApiInventoryRecordInfo() {
          @Override
          protected String getSelfLinkEndpoint() {
            return null;
          }
        };
    original =
        new ApiInventoryRecordInfo() {
          @Override
          protected String getSelfLinkEndpoint() {
            return null;
          }
        };
  }

  @Test
  public void testCreateApiTagInfoList() {
    assertTrue(testee.getTags().isEmpty());
    testee.setApiTagInfo("A local ontology Tag");
    assertEquals(1, testee.getTags().size());
    assertEquals("A local ontology Tag", testee.getTags().get(0).getValue());
    testee.setApiTagInfo("A local ontology Tag, another");
    assertEquals(2, testee.getTags().size());
    assertEquals("A local ontology Tag", testee.getTags().get(0).getValue());
    assertEquals("another", testee.getTags().get(1).getValue());
    testee.setApiTagInfo(
        "A local ontology Tag, another,"
            + " tag1__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1");
    assertEquals(3, testee.getTags().size());
    assertEquals("A local ontology Tag", testee.getTags().get(0).getValue());
    assertEquals("another", testee.getTags().get(1).getValue());
    ApiTagInfo tagWithMeta = testee.getTags().get(2);
    assertEquals("tag1", tagWithMeta.getValue());
    assertEquals("MYONTOLOGY", tagWithMeta.getOntologyName());
    assertEquals("1", tagWithMeta.getOntologyVersion());
    assertEquals("NONE", tagWithMeta.getUri());
  }

  @Test
  public void testGetDBStringFromTags() {
    String forDB = testee.getDBStringFromTags();
    assertEquals("", forDB);
    String tagPlusMeta =
        "A local ontology"
            + " Tag,another,tag1__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1";
    String localTag = "local";
    testee.setApiTagInfo(localTag);
    forDB = testee.getDBStringFromTags();
    assertEquals(localTag, forDB);
    testee.setApiTagInfo(tagPlusMeta);
    forDB = testee.getDBStringFromTags();
    assertEquals(tagPlusMeta, forDB);
  }

  @Test
  public void testTagDifferenceExists() {
    assertFalse(ApiInventoryRecordInfo.tagDifferenceExists(original, testee));
    String tagPlusMeta =
        "A local ontology"
            + " Tag,another,tag1__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1";
    String tagPlusMeta2 =
        "another,tag1__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1,A"
            + " local ontology Tag";
    String localTag = "local";
    testee.setApiTagInfo(localTag);
    original.setApiTagInfo(localTag);
    assertFalse(ApiInventoryRecordInfo.tagDifferenceExists(original, testee));
    testee.setApiTagInfo(null);
    original.setApiTagInfo(localTag);
    assertTrue(ApiInventoryRecordInfo.tagDifferenceExists(original, testee));
    testee.setApiTagInfo(tagPlusMeta);
    original.setApiTagInfo(localTag);
    assertTrue(ApiInventoryRecordInfo.tagDifferenceExists(original, testee));
    testee.setApiTagInfo(null);
    original.setApiTagInfo(null);
    assertFalse(ApiInventoryRecordInfo.tagDifferenceExists(original, testee));
    testee.setApiTagInfo(tagPlusMeta);
    original.setApiTagInfo(tagPlusMeta2);
    assertTrue(ApiInventoryRecordInfo.tagDifferenceExists(original, testee));
  }

  /** A limited-read viewer can't fetch the image (it 403s), so its link is stripped. */
  @Test
  public void limitedReadItemHasImageLinksStripped() {
    ApiInventoryRecordInfo rec = imagedRecord();
    rec.setPermittedActions(List.of(ApiInventoryRecordPermittedAction.LIMITED_READ));

    rec.buildAndAddInventoryRecordLinks(BASE_URL);
    assertTrue("links should be built before stripping", hasImageOrThumbnailLink(rec));

    rec.removeImageLinksForLimitedView();

    assertFalse(hasImageOrThumbnailLink(rec));
  }

  @Test
  public void fullyReadableItemKeepsImageLinks() {
    ApiInventoryRecordInfo rec = imagedRecord();
    rec.setPermittedActions(List.of(ApiInventoryRecordPermittedAction.READ));

    rec.buildAndAddInventoryRecordLinks(BASE_URL);
    rec.removeImageLinksForLimitedView();

    assertTrue(hasImageOrThumbnailLink(rec));
  }

  /**
   * The limited-view copy of a subsample keeps the raw parent sample, whose permitted actions are
   * never evaluated for the viewer, so both the subsample's from-parent image links and the nested
   * sample's own links must be stripped off the limited-read subsample.
   */
  @Test
  public void limitedReadSubSampleHasParentSampleImageLinksStripped() {
    ApiSubSample subSample = limitedReadSubSampleOfImagedSample();

    subSample.buildAndAddInventoryRecordLinks(BASE_URL);
    assertTrue("subsample should get image links from parent", hasImageOrThumbnailLink(subSample));
    assertTrue(hasImageOrThumbnailLink(subSample.getSampleInfo()));

    subSample.removeImageLinksForLimitedView();

    assertFalse(hasImageOrThumbnailLink(subSample));
    assertFalse(hasImageOrThumbnailLink(subSample.getSampleInfo()));
  }

  @Test
  public void fullyReadableSubSampleKeepsParentSampleImageLinks() {
    ApiSubSample subSample = limitedReadSubSampleOfImagedSample();
    subSample.setPermittedActions(List.of(ApiInventoryRecordPermittedAction.READ));

    subSample.buildAndAddInventoryRecordLinks(BASE_URL);
    subSample.removeImageLinksForLimitedView();

    assertTrue(hasImageOrThumbnailLink(subSample));
    assertTrue(hasImageOrThumbnailLink(subSample.getSampleInfo()));
  }

  /** Content of a readable container must still be stripped per-item, not just the container. */
  @Test
  public void limitedReadContainerContentHasImageLinksStripped() {
    ApiSubSample limitedReadChild = limitedReadSubSampleOfImagedSample();
    ApiContainerLocationWithContent location = new ApiContainerLocationWithContent(1, 1);
    location.setContent(limitedReadChild);
    ApiContainer container = new ApiContainer();
    container.setId(3L);
    container.setPermittedActions(List.of(ApiInventoryRecordPermittedAction.READ));
    container.setLocations(List.of(location));

    container.buildAndAddInventoryRecordLinks(BASE_URL);
    assertTrue(hasImageOrThumbnailLink(limitedReadChild));

    container.removeImageLinksForLimitedView();

    assertFalse(hasImageOrThumbnailLink(limitedReadChild));
    assertFalse(hasImageOrThumbnailLink(limitedReadChild.getSampleInfo()));
  }

  private ApiSubSample limitedReadSubSampleOfImagedSample() {
    ApiSampleWithoutSubSamples parentSample = new ApiSampleWithoutSubSamples();
    parentSample.setId(1L);
    parentSample.setCustomImage(true);
    FileProperty fp = mock(FileProperty.class);
    when(fp.getContentsHash()).thenReturn("contentshash");
    parentSample.setImageFileProperty(fp);
    parentSample.setThumbnailFileProperty(fp);

    ApiSubSample subSample = new ApiSubSample();
    subSample.setId(2L);
    subSample.setSampleInfo(parentSample);
    subSample.setPermittedActions(List.of(ApiInventoryRecordPermittedAction.LIMITED_READ));
    return subSample;
  }

  private static final UriComponentsBuilder BASE_URL =
      UriComponentsBuilder.fromHttpUrl("http://localhost:8080/api/inventory/v1");

  private ApiInventoryRecordInfo imagedRecord() {
    ApiInventoryRecordInfo rec =
        new ApiInventoryRecordInfo() {
          @Override
          protected String getSelfLinkEndpoint() {
            return "containers";
          }
        };
    rec.setId(1L);
    rec.setCustomImage(true);
    FileProperty fp = mock(FileProperty.class);
    when(fp.getContentsHash()).thenReturn("contentshash");
    rec.setImageFileProperty(fp);
    rec.setThumbnailFileProperty(fp);
    return rec;
  }

  private boolean hasImageOrThumbnailLink(ApiInventoryRecordInfo rec) {
    return rec.getLinks().stream()
        .anyMatch(
            l ->
                ApiLinkItem.IMAGE_REL.equals(l.getRel())
                    || ApiLinkItem.THUMBNAIL_REL.equals(l.getRel()));
  }
}
