package com.researchspace.api.v2.user;

import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.UserManager;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * User manager adapter for the generic REST v2 CRUD dispatcher.
 *
 * <p>The resource access policy refuses every mutation before dispatch gets here. The explicit
 * write methods are non-persistent placeholders until user mutation semantics are defined.
 */
@Configuration(proxyBeanMethods = false)
public final class UserResourceOperations implements ResourceOperations<User, Long> {

  private final UserManager manager;

  public UserResourceOperations(UserManager manager) {
    this.manager = manager;
  }

  @Bean
  ApiV2ResourceSpec<User, Long> userApiV2Resource() {
    return new ApiV2ResourceSpec<>(
        ApiV2UserResource.DESCRIPTION,
        this,
        Long::valueOf,
        "errors.api.v2.invalidRequest",
        "errors.api.v2.invalidRequest");
  }

  @Override
  public ResourcePage<User> find(ResourceRequest request, User actor) {
    return manager.getUsers(request);
  }

  @Override
  public long count(ResourceRequest request, User actor) {
    return manager.countUsers(request);
  }

  /**
   * Unreachable for an ordinary caller: their policy returns a row constraint, so {@code get} goes
   * through {@link #find} instead. Only a system administrator, whose access carries no constraint,
   * reaches this path.
   */
  @Override
  public Optional<User> findById(Long id, User actor) {
    return manager.getOptional(id);
  }

  @Override
  public User create(ParsedDocument document, User actor) {
    return actor;
  }

  @Override
  public List<User> createMany(List<ParsedDocument> documents, User actor) {
    return List.of();
  }

  @Override
  public Optional<User> update(Long id, ParsedDocument document, User actor) {
    return Optional.empty();
  }

  @Override
  public List<User> updateMany(ResourceRequest request, ParsedDocument document, User actor) {
    return List.of();
  }

  @Override
  public Optional<User> delete(Long id, User actor) {
    return Optional.empty();
  }

  @Override
  public List<User> deleteMany(ResourceRequest request, User actor) {
    return List.of();
  }
}
