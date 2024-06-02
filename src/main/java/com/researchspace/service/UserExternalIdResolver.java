package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.repository.spi.ExternalId;
import com.researchspace.repository.spi.IdentifierScheme;
import java.util.Optional;

/** Retrieves external user identifiers related to global user identity for e.g. repositories. */
public interface UserExternalIdResolver {

  /**
   * Retrieves an optional external id for the given user, which may not exist
   *
   * @param user
   * @return An optional externalId
   */
  Optional<ExternalId> getExternalIdForUser(User user, IdentifierScheme scheme);

  /**
   * Boolean query as to whether supplied scheme is available for the given user.
   *
   * @param scheme
   * @param user
   * @return <code>true</code> if it is, <code>false</code> otherwise.
   */
  boolean isIdentifierSchemeAvailable(User user, IdentifierScheme scheme);
}
