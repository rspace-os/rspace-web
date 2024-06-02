package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class DocumentTagManagerImplTest {
  private String ontologyString =
      "tag1__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1";
  private String ontologyTag2String =
      "tag2__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1";

  @Test
  public void testGetTagOntologyUriFromMeta() {
    assertEquals("NONE", DocumentTagManagerImpl.getTagOntologyUriFromMeta(ontologyString));
    assertEquals("", DocumentTagManagerImpl.getTagOntologyUriFromMeta("local"));
  }

  @Test
  public void testGetTagOntologyNameFromMeta() {
    assertEquals("MYONTOLOGY", DocumentTagManagerImpl.getTagOntologyNameFromMeta(ontologyString));
    assertEquals("", DocumentTagManagerImpl.getTagOntologyNameFromMeta("local"));
  }

  @Test
  public void testGTagOntologyVersionFromMeta() {
    assertEquals("1", DocumentTagManagerImpl.getTagOntologyVersionFromMeta(ontologyString));
    assertEquals("", DocumentTagManagerImpl.getTagOntologyVersionFromMeta("local"));
  }

  @Test
  public void testGetTagValueFromMeta() {
    assertEquals("tag1", DocumentTagManagerImpl.getTagValueFromMeta(ontologyString));
  }

  @Test
  public void testGetTagValueFromMetaLocalOntology() {
    assertEquals("local", DocumentTagManagerImpl.getTagValueFromMeta("local"));
  }

  @Test
  public void testGetAllTagValuesFromAllTagsPlusMeta() {
    assertEquals(
        "tag1",
        DocumentTagManagerImpl.getAllTagValuesFromAllTagsPlusMeta(
                ontologyString + "," + ontologyTag2String)
            .get(0));
    assertEquals(
        "tag2",
        DocumentTagManagerImpl.getAllTagValuesFromAllTagsPlusMeta(
                ontologyString + "," + ontologyTag2String)
            .get(1));
    assertEquals(
        "tag1", DocumentTagManagerImpl.getAllTagValuesFromAllTagsPlusMeta("tag1,tag2").get(0));
    assertEquals(
        "tag2", DocumentTagManagerImpl.getAllTagValuesFromAllTagsPlusMeta("tag1,tag2").get(1));
  }
}
