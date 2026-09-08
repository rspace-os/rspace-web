package com.researchspace.webapp.integrations.b2inst;

import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRecordLinks;
import com.researchspace.b2inst.model.response.B2instRequestResponse;
import com.researchspace.b2inst.model.response.B2instSearchResult;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;

/**
 * Test double capturing the payload sent to B2INST; always configured and enabled. Mirrors {@code
 * DataCiteConnectorDummy} so tests can assert on the mapped metadata without a live B2INST
 * instance.
 */
public class B2instConnectorDummy implements B2instConnector {

  public static final String DUMMY_RID = "abcde-12345";
  public static final String DUMMY_SELF_HTML =
      "https://b2inst-test.example.org/uploads/" + DUMMY_RID;

  @Getter private B2instDoi doiSentToB2inst;

  @Override
  public B2instDraftRecord registerDoi(B2instDoi doi) {
    this.doiSentToB2inst = doi;
    B2instDraftRecord draft = new B2instDraftRecord();
    draft.setId(DUMMY_RID);
    // Mirrors the real create-draft response, whose links.self_html is the record's B2INST page.
    B2instRecordLinks links = new B2instRecordLinks();
    links.setSelfHtml(DUMMY_SELF_HTML);
    draft.setLinks(links);
    return draft;
  }

  /** Captures the rebuilt payload, so a test can assert on what an on-save update would send. */
  @Getter private B2instDoi doiUpdateSentToB2inst;

  @Override
  public B2instDraftRecord updateDraftDoi(String rid, B2instDoi doi) {
    this.doiUpdateSentToB2inst = doi;
    B2instDraftRecord draft = new B2instDraftRecord();
    draft.setId(rid);
    return draft;
  }

  @Override
  public boolean deleteDoi(String rid) {
    return true;
  }

  @Override
  public B2instRequestResponse publishDoi(String rid) {
    return new B2instRequestResponse();
  }

  @Override
  public B2instRequestResponse retractDoi(String rid) {
    throw new UnsupportedOperationException("B2INST has no retract operation");
  }

  @Override
  public Optional<B2instRequestResponse> getReviewOf(String rid) {
    return Optional.empty();
  }

  /** The one published record this double knows, answered by search and by id; null means none. */
  @Setter private B2instDraftRecord publishedRecord;

  @Override
  public B2instSearchResult searchRecords(String query, int size) {
    B2instSearchResult result = new B2instSearchResult();
    if (publishedRecord != null) {
      result.getHits().getHits().add(publishedRecord);
    }
    result.getHits().setTotal(result.getHits().getHits().size());
    return result;
  }

  /** The same one record: this double's account owns whatever it has published. */
  @Override
  public B2instSearchResult searchUserRecords(String query, int size) {
    return searchRecords(query, size);
  }

  /**
   * Nothing published unless a test set {@code publishedRecord}: {@link #publishDoi(String)} here
   * does not move the record on, so a record this double created is still only a draft.
   */
  @Override
  public Optional<B2instDraftRecord> getPublishedRecord(String rid) {
    return publishedRecord != null && rid.equals(publishedRecord.getId())
        ? Optional.of(publishedRecord)
        : Optional.empty();
  }

  /**
   * The draft {@link #registerDoi(B2instDoi)} created, so the double agrees with its own state: a
   * test that registers and then refreshes gets the truthful "still a draft" answer instead of
   * landing on the record-gone error path. Any other id is unknown to this double.
   */
  @Override
  public Optional<B2instDraftRecord> getDraftRecord(String rid) {
    if (!DUMMY_RID.equals(rid)) {
      return Optional.empty();
    }
    B2instDraftRecord draft = new B2instDraftRecord();
    draft.setId(DUMMY_RID);
    B2instRecordLinks links = new B2instRecordLinks();
    links.setSelfHtml(DUMMY_SELF_HTML);
    draft.setLinks(links);
    return Optional.of(draft);
  }

  @Override
  public void reloadClient() {}

  @Override
  public boolean isConfiguredAndEnabled() {
    return true;
  }

  @Override
  public boolean testConnection() {
    return true;
  }
}
