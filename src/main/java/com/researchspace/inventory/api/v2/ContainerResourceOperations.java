package com.researchspace.inventory.api.v2;

import com.researchspace.api.v2.resource.*;
import com.researchspace.inventory.model.ApiV2ContainerResource;
import com.researchspace.model.User;
import com.researchspace.model.collection.*;
import com.researchspace.model.inventory.Container;
import com.researchspace.service.inventory.ContainerReadAccess;
import com.researchspace.service.inventory.ContainerReadManager;
import java.util.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Read-only containers, including contents-only Inventory visibility. */
@Configuration(proxyBeanMethods = false)
public class ContainerResourceOperations implements ResourceOperations<Container, Long> {
  private final ContainerReadManager manager;
  private final ContainerReadAccess access;

  public ContainerResourceOperations(ContainerReadManager manager, ContainerReadAccess access) {
    this.manager = manager;
    this.access = access;
  }

  @Bean
  ApiV2ResourceSpec<Container, Long> containerApiV2Resource() {
    return new ApiV2ResourceSpec<>(
        ApiV2ContainerResource.description(access),
        this,
        Long::valueOf,
        "errors.api.v2.container.create",
        "errors.api.v2.container.patch",
        Set.of(ResourceOperation.LIST, ResourceOperation.COUNT, ResourceOperation.READ),
        Map.of(),
        Map.of(),
        CollectionMutationLimits.DEFAULT,
        List.of());
  }

  @Override
  public ResourcePage<Container> find(ResourceRequest request, User actor) {
    return manager.find(request, actor);
  }

  @Override
  public long count(ResourceRequest request, User actor) {
    return manager.count(request, actor);
  }

  @Override
  public Optional<Container> findById(Long id, User actor) {
    return find(
            ResourceRequest.unpaged(
                new FilterExpression.Comparison("id", Operator.EQUAL, List.of(id), false)),
            actor)
        .resources()
        .stream()
        .findFirst();
  }

  @Override
  public Optional<Map<Object, Map<String, Object>>> relationshipReadDocuments(
      Set<Long> ids, User actor) {
    Map<Object, Map<String, Object>> documents = new LinkedHashMap<>();
    List<Long> ordered = new ArrayList<>(ids);
    for (int start = 0; start < ordered.size(); start += CollectionQueryLimits.MAX_PAGE_SIZE) {
      var batch =
          ordered.subList(
              start, Math.min(start + CollectionQueryLimits.MAX_PAGE_SIZE, ordered.size()));
      var request =
          new ResourceRequest(
              new FilterExpression.Comparison("id", Operator.IN, new ArrayList<>(batch), false),
              List.of(),
              new ResourceRequest.Page(1, batch.size()),
              FieldSelection.all(),
              IncludeTree.empty());
      for (Container container : find(request, actor).resources()) {
        if (ids.contains(container.getId()))
          documents.put(
              container.getId(),
              Map.of(
                  "id",
                  container.getId(),
                  "globalId",
                  container.getGlobalIdentifier(),
                  "name",
                  container.getName(),
                  "containerType",
                  container.getContainerType().name(),
                  "deleted",
                  container.isDeleted()));
      }
    }
    return Optional.of(documents);
  }
}
