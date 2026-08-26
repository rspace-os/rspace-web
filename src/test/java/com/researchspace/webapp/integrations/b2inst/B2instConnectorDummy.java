package com.researchspace.webapp.integrations.b2inst;

import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRecordLinks;
import com.researchspace.b2inst.model.response.B2instRequestResponse;
import java.util.Optional;
import lombok.Getter;

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

  /**
   * Nothing published: {@link #publishDoi(String)} here does not move the record on, so a record
   * this double created is still only a draft.
   */
  @Override
  public Optional<B2instDraftRecord> getPublishedRecord(String rid) {
    return Optional.empty();
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
