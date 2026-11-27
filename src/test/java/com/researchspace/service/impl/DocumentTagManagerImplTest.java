package com.researchspace.service.impl;

import static com.researchspace.service.DocumentTagManager.FINAL_DATA;
import static com.researchspace.service.DocumentTagManager.MAX_ONTOLOGY_RESULTS_SIZE;
import static com.researchspace.service.DocumentTagManager.ONTOLOGY_RESULTS_PAGE_SIZE;
import static com.researchspace.service.DocumentTagManager.SMALL_DATASET_IN_SINGLE_BLOCK;
import static com.researchspace.service.DocumentTagManager.TOO_MANY_ONTOLOGY_RESULTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.dao.RecordDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryTagApiManager;
import com.researchspace.testutils.TestFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class DocumentTagManagerImplTest {
  @Mock private RecordDao recordDao;
  @Mock private RecordGroupSharingDao sharingDao;
  @Mock private InventoryTagApiManager inventoryTagApiManager;
  @InjectMocks private DocumentTagManagerImpl tagManager;
  private final User user = TestFactory.createAnyUser("tagUser");

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

  private static String term(String label, String vocabulary) {
    return label
        + "__RSP_EXTONT_URL_DELIM__https://example.org/"
        + vocabulary
        + "__RSP_EXTONT_NAME_DELIM__"
        + vocabulary
        + "__RSP_EXTONT_VERSION_DELIM__1";
  }

  private static List<String> numberedTerms(int count) {
    return IntStream.range(0, count)
        .mapToObj(i -> term(String.format("term%06d", i), "V"))
        .collect(Collectors.toList());
  }

  /** Suggestions for an empty filter when the user's records carry {@code recordTags}. */
  private List<String> suggestions(List<String> recordTags, int page) {
    when(recordDao.getTagsMetaDataForRecordsVisibleByUserOrPi(user, ""))
        .thenReturn(List.of(String.join(",", recordTags)));
    return List.copyOf(tagManager.getTagsPlusOntologiesForViewableDocuments(user, "", page));
  }

  @Test
  public void plainSuggestionIsDroppedWhenAnOntologyTermHasTheSameLabel() {
    String fromFile =
        "X__RSP_EXTONT_URL_DELIM__https://example.org/file"
            + "__RSP_EXTONT_NAME_DELIM__File__RSP_EXTONT_VERSION_DELIM__2";
    when(recordDao.getTextDataFromOntologiesOwnedByUser(user))
        .thenReturn(
            List.of(
                "__RSP_EXTONT_NAME__File__RSP_EXTONT_TAG_DELIM____RSP_EXTONT_VERSION__2"
                    + "__RSP_EXTONT_TAG_DELIM__X__RSP_EXTONT_URL_DELIM__https://example.org/file"));

    assertThat(suggestions(List.of("X", term("X", "A"), "Y", "x"), 0))
        .containsExactlyInAnyOrder(
            term("X", "A"), fromFile, "Y", "x", SMALL_DATASET_IN_SINGLE_BLOCK);
  }

  @Test
  public void escapedPlainLabelsMatchOntologyLabels() {
    when(recordDao.getTextDataFromOntologiesOwnedByUser(user))
        .thenReturn(
            List.of(
                "__RSP_EXTONT_NAME__File__RSP_EXTONT_TAG_DELIM____RSP_EXTONT_VERSION__2"
                    + "__RSP_EXTONT_TAG_DELIM__a/b__RSP_EXTONT_URL_DELIM__https://example.org/ab"
                    + "__RSP_EXTONT_TAG_DELIM__c,d__RSP_EXTONT_URL_DELIM__https://example.org/cd"));

    assertThat(suggestions(List.of("a__rspactags_forsl__b", "c__rspactags_comma__d"), 0))
        .containsExactlyInAnyOrder(
            "a/b__RSP_EXTONT_URL_DELIM__https://example.org/ab"
                + "__RSP_EXTONT_NAME_DELIM__File__RSP_EXTONT_VERSION_DELIM__2",
            "c,d__RSP_EXTONT_URL_DELIM__https://example.org/cd"
                + "__RSP_EXTONT_NAME_DELIM__File__RSP_EXTONT_VERSION_DELIM__2",
            SMALL_DATASET_IN_SINGLE_BLOCK);
  }

  @Test
  public void pageBoundaryIsComputedWithoutDuplicates() {
    List<String> tags = new ArrayList<>(numberedTerms(ONTOLOGY_RESULTS_PAGE_SIZE - 1));
    tags.add("term000000");

    assertThat(suggestions(tags, 0))
        .hasSize(ONTOLOGY_RESULTS_PAGE_SIZE)
        .contains(SMALL_DATASET_IN_SINGLE_BLOCK)
        .doesNotContain("term000000");
  }

  @Test
  public void lastPageIsMarkedFinalAfterDeduplication() {
    List<String> tags = new ArrayList<>(numberedTerms(ONTOLOGY_RESULTS_PAGE_SIZE + 1));
    tags.add("term000000");

    assertThat(suggestions(tags, 1))
        .containsExactlyInAnyOrder(
            term(String.format("term%06d", ONTOLOGY_RESULTS_PAGE_SIZE), "V"), FINAL_DATA);
  }

  @Test
  public void tooManySignalIsComputedWithoutDuplicates() {
    List<String> tags = new ArrayList<>(numberedTerms(MAX_ONTOLOGY_RESULTS_SIZE));
    tags.add("term000000");

    assertThat(suggestions(tags, 0))
        .hasSize(ONTOLOGY_RESULTS_PAGE_SIZE)
        .doesNotContain(TOO_MANY_ONTOLOGY_RESULTS);
  }

  @Test
  public void tooManySignalStaysAboveTheLimit() {
    List<String> tags = new ArrayList<>(numberedTerms(MAX_ONTOLOGY_RESULTS_SIZE));
    tags.add("plain only");

    assertThat(suggestions(tags, 0)).containsExactly(TOO_MANY_ONTOLOGY_RESULTS);
  }
}
