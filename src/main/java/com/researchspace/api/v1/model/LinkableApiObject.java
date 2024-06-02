package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

/** Base class for API domain objects that can contain HATEOAS links */
@Data
@NoArgsConstructor
public abstract class LinkableApiObject {

  @JsonProperty("_links")
  protected List<ApiLinkItem> links = new ArrayList<>();

  protected LinkableApiObject addLink(ApiLinkItem link) {
    links.add(link);
    return this;
  }

  public LinkableApiObject addLink(String link, String relType) {
    return this.addLink(ApiLinkItem.builder().link(link).rel(relType).build());
  }

  public LinkableApiObject addLink(String link, HttpMethod method, String operation) {
    return this.addLink(
        ApiLinkItem.builder().link(link).method(method).operation(operation).build());
  }

  public LinkableApiObject addSelfLink(String link) {
    this.addLink(ApiLinkItem.builder().link(link).rel(ApiLinkItem.SELF_REL).build());
    return this;
  }

  public LinkableApiObject addOperationSelfLink(String link) {
    this.addLink(
        ApiLinkItem.builder()
            .operation(ApiLinkItem.SELF_REL)
            .link(link)
            .rel(ApiLinkItem.SELF_REL)
            .build());
    return this;
  }

  public LinkableApiObject buildAndAddSelfLink(
      final String endpoint, String pathStartingFromId, UriComponentsBuilder baseUrl) {
    String link = getResourceLink(endpoint, pathStartingFromId, baseUrl);
    return addSelfLink(link);
  }

  public LinkableApiObject buildAndAddSelfLink(
      final String endpoint,
      String pathStartingFromId,
      Map<String, String> reqParameters,
      UriComponentsBuilder baseUrl) {
    String link = getResourceLink(endpoint, pathStartingFromId, reqParameters, baseUrl);
    return addSelfLink(link);
  }

  public String getResourceLink(
      final String endpoint,
      String pathStartingFromId,
      Map<String, String> reqParameters,
      UriComponentsBuilder baseUrl) {
    String path = endpoint;
    if (StringUtils.isNotBlank(pathStartingFromId)) {
      path = path + "/" + pathStartingFromId;
    }
    if (!CollectionUtils.isEmpty(reqParameters)) {
      path = path + '?';
      Iterator<Map.Entry<String, String>> paramIterator = reqParameters.entrySet().iterator();
      while (paramIterator.hasNext()) {
        Map.Entry<String, String> currentParameter = paramIterator.next();
        path = path + currentParameter.getKey() + '=' + currentParameter.getValue();
        if (paramIterator.hasNext()) {
          path = path + '&';
        }
      }
    }
    return baseUrl.cloneBuilder().path(path).build().toUriString();
  }

  public String getResourceLink(
      final String endpoint, String pathStartingFromId, UriComponentsBuilder baseUrl) {
    String path = endpoint;
    if (StringUtils.isNotBlank(pathStartingFromId)) {
      path = path + "/" + pathStartingFromId;
    }
    return baseUrl.cloneBuilder().path(path).build().encode().toUriString();
  }

  public LinkableApiObject addEnclosureLink(String link) {
    this.addLink(ApiLinkItem.builder().link(link).rel(ApiLinkItem.ENCLOSURE_REL).build());
    return this;
  }

  public Optional<ApiLinkItem> getLinkOfType(String type) {
    if (links == null) {
      return Optional.empty();
    }
    return links.stream().filter(l -> l.getRel().equals(type)).findFirst();
  }
}
