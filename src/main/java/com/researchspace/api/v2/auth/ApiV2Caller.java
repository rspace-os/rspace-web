package com.researchspace.api.v2.auth;

import com.researchspace.model.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;

/** The effective subject and originating actor for one REST API v2 request. */
public record ApiV2Caller(User subject, User actor) {

  public static final String REQUEST_ATTRIBUTE = "apiV2Caller";

  public ApiV2Caller {
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(actor, "actor");
  }

  public static ApiV2Caller direct(User user) {
    return new ApiV2Caller(user, user);
  }

  public boolean isDelegated() {
    return !Objects.equals(subject.getId(), actor.getId());
  }

  public static ApiV2Caller from(HttpServletRequest request) {
    Object caller = request.getAttribute(REQUEST_ATTRIBUTE);
    return caller instanceof ApiV2Caller typedCaller ? typedCaller : null;
  }
}
