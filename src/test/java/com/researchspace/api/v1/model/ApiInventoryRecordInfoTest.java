package com.researchspace.api.v1.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

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
}
