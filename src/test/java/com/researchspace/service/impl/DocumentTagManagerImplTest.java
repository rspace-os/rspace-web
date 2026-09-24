package com.researchspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class DocumentTagManagerImplTest {
  private String ontologyString =
      "tag1__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1";
  private String ontologyTag2String =
      "tag2__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1";

  @Test
  public void testGetTagOntologyUriFromMeta() {
    assertEquals("NONE", DocumentTagManagerImpl.getTagOntologyUriFromMeta(ontologyString));
    assertThat(DocumentTagManagerImpl.getTagOntologyUriFromMeta("local")).isEmpty();
  }

  @Test
  public void testGetTagOntologyNameFromMeta() {
    assertEquals("MYONTOLOGY", DocumentTagManagerImpl.getTagOntologyNameFromMeta(ontologyString));
    assertThat(DocumentTagManagerImpl.getTagOntologyNameFromMeta("local")).isEmpty();
  }

  @Test
  public void testGTagOntologyVersionFromMeta() {
    assertEquals("1", DocumentTagManagerImpl.getTagOntologyVersionFromMeta(ontologyString));
    assertThat(DocumentTagManagerImpl.getTagOntologyVersionFromMeta("local")).isEmpty();
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
