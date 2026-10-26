package com.researchspace.api.v2.user;

import com.researchspace.api.v2.resource.ApiV2ResourceSpec;
import com.researchspace.api.v2.resource.ResourceOperation;
import com.researchspace.api.v2.resource.ResourceOperations;
import com.researchspace.model.User;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.service.UserManager;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Read-only user adapter for the generic REST API v2 dispatcher.
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
        "errors.api.v2.invalidRequest",
        EnumSet.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
        Map.of());
  }

  @Override
  public ResourcePage<User> find(ResourceRequest request, User actor) {
    return manager.getUsers(request, actor);
  }

  @Override
  public long count(ResourceRequest request, User actor) {
    return manager.countUsers(request, actor);
  }

  /**
   * Returns a user only when {@link UserManager} confirms that the actor is the user or a system
   * administrator. Ordinary collection reads are narrowed to the caller's row in the manager.
   */
  @Override
  public Optional<User> findById(Long id, User actor) {
    return manager.getUserResource(id, actor);
  }
}
