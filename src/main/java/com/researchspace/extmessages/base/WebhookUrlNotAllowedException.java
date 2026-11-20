package com.researchspace.extmessages.base;

import com.researchspace.model.field.LocalizedIllegalArgumentException;

/**
 * A webhook URL that {@link WebhookDestinationPolicy} does not allow. The user-facing text never
 * contains the URL, which is a credential; {@link #getReason()} is safe to log.
 */
public class WebhookUrlNotAllowedException extends LocalizedIllegalArgumentException {

  static final String MESSAGE_KEY = "apps.webhook.errors.urlNotAllowed";

  private final String reason;

  public WebhookUrlNotAllowedException(String reason) {
    super(MESSAGE_KEY);
    this.reason = reason;
  }

  /**
   * @return why the URL was rejected, without the URL itself
   */
  public String getReason() {
    return reason;
  }
}
