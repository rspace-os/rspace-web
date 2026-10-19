package com.researchspace.model.sort;

/**
 * Sort keys for messages, requests and notifications. {@code SENDER} sorts by the originator's
 * username.
 */
public enum CommunicationSort implements SortKey {
  CREATION_TIME("creationTime"),
  REQUESTED_COMPLETION_DATE("requestedCompletionDate"),
  SENDER("sender"),
  LAST_STATUS_UPDATE("lastStatusUpdate");

  private final String key;

  CommunicationSort(String key) {
    this.key = key;
  }

  @Override
  public String key() {
    return key;
  }

  public static CommunicationSort fromRequest(String raw) {
    return SortKey.fromRequest(CommunicationSort.class, raw, CREATION_TIME);
  }
}
