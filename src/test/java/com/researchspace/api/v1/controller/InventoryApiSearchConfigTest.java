package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** tag search should replace '/' and ',' in tag search terms using lucene queries */
public class InventoryApiSearchConfigTest {

  @Test
  public void testModifyTagSearchNoTags() {
    assertEquals("some query", InventoryApiSearchConfig.modifyTagSearch("some query"));
  }

  @Test
  public void testModifyTagSearchNoValueToReplace() {
    assertEquals(
        "l: (tags:\"\"My quote\"\")",
        InventoryApiSearchConfig.modifyTagSearch("l: (tags:\"\"My quote\"\")"));
  }

  @Test
  public void testModifyTagSearchOneComma() {
    assertEquals(
        "l: (tags:\"\"My __rspactags_comma__quote\"\")",
        InventoryApiSearchConfig.modifyTagSearch("l: (tags:\"\"My ,quote\"\")"));
  }

  @Test
  public void testModifyTagSearchOneForwardSlash() {
    assertEquals(
        "l: (tags:\"\"My __rspactags_forsl__quote\"\")",
        InventoryApiSearchConfig.modifyTagSearch("l: (tags:\"\"My /quote\"\")"));
  }

  @Test
  public void testModifyTagSearchTwoForwardSlashes() {
    assertEquals(
        "l: (tags:\"\"M__rspactags_forsl__y __rspactags_forsl__quote\"\")",
        InventoryApiSearchConfig.modifyTagSearch("l: (tags:\"\"M/y /quote\"\")"));
  }

  @Test
  public void testModifyTagSearchTwoForwardSlashesTwoCommas() {
    assertEquals(
        "l: (tags:\"\"M__rspactags_forsl__y"
            + " __rspactags_forsl__qu__rspactags_comma__o__rspactags_comma__te\"\")",
        InventoryApiSearchConfig.modifyTagSearch("l: (tags:\"\"M/y /qu,o,te\"\")"));
  }

  @Test
  public void testModifyTagSearchQuotesInTagValue() {
    assertEquals(
        "l: (tags:\"\"My __rspactags_forsl__quote\"\")",
        InventoryApiSearchConfig.modifyTagSearch("l: (tags:\"\"My /quote\"\")"));
  }

  @Test
  public void testModifyTagSearchQuotesInTagValueAndTwoQueryTypesOnlyReplaceSlashInTags() {
    assertEquals(
        "l: (tags:\"\"1-Deoxynojirimycin [Chemical__rspactags_forsl__Ingredient]\"\" AND"
            + " owner.username:\"use/r1a\")",
        InventoryApiSearchConfig.modifyTagSearch(
            "l: (tags:\"\"1-Deoxynojirimycin [Chemical/Ingredient]\"\" AND"
                + " owner.username:\"use/r1a\")"));
  }

  @Test
  public void testModifyTagSearchQuotesInTagValueAndThreeQueryTypesOnlyReplaceSlashInTags() {
    assertEquals(
        "l: (tags:\"\"1-Deoxynojirimycin [Chemical__rspactags_forsl__Ingredient]\"\" AND"
            + " owner.username:\"use/r1a\" OR owner.username:\"use/r2b\")",
        InventoryApiSearchConfig.modifyTagSearch(
            "l: (tags:\"\"1-Deoxynojirimycin [Chemical/Ingredient]\"\" AND"
                + " owner.username:\"use/r1a\" OR owner.username:\"use/r2b\")"));
  }

  @Test
  public void testModifyTagSearchQuotesInTagValueAndThreeQueryTypesOnlyReplaceCommaInTags() {
    assertEquals(
        "l: (tags:\"\"1-Deoxynojirimycin [Chemical__rspactags_comma__Ingredient]\"\" AND"
            + " owner.username:\"use,r1a\" OR owner.username:\"use,r2b\")",
        InventoryApiSearchConfig.modifyTagSearch(
            "l: (tags:\"\"1-Deoxynojirimycin [Chemical,Ingredient]\"\" AND"
                + " owner.username:\"use,r1a\" OR owner.username:\"use,r2b\")"));
  }
}
