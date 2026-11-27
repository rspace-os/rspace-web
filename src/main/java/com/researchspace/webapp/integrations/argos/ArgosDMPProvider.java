package com.researchspace.webapp.integrations.argos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.researchspace.argos.model.ArgosDMP;
import com.researchspace.argos.model.ArgosDMPListing;
import com.researchspace.argos.model.DataTableData;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.service.DMPManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.UserManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/** Lists and imports public plans through Argos's (OpenCDMP) public plan API. */
@Slf4j
public class ArgosDMPProvider {

  // ArgosDMP#getDoi() returns Optional<String>; Jdk8Module is required to serialize it.
  static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new Jdk8Module());

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);
  private static final String TENANT_HEADER = "x-tenant";
  private static final String TENANT = "default";
  private static final String GRANTS_REFERENCE_TYPE = "grants";
  private static final List<String> LISTING_FIELDS =
      List.of(
          "id",
          "label",
          "createdAt",
          "updatedAt",
          "planReferences.isActive",
          "planReferences.reference.label",
          "planReferences.reference.type.code");
  private static final List<String> PLAN_FIELDS =
      List.of(
          "id",
          "label",
          "entityDois.doi",
          "entityDois.repositoryId",
          "planReferences.isActive",
          "planReferences.reference.id",
          "planReferences.reference.label",
          "planReferences.reference.type.code");

  private RestTemplate restTemplate;
  private URL baseUrl;

  public ArgosDMPProvider(URL baseUrl) {
    this.baseUrl = baseUrl;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(READ_TIMEOUT);
    this.restTemplate = new RestTemplate(factory);
  }

  public void setRestTemplate(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Autowired MediaManager mediaManager;
  @Autowired UserManager userManager;
  @Autowired DMPManager dmpManager;

  /**
   * @param id an Argos plan id (a UUID)
   * @throws IllegalArgumentException if {@code id} is not a UUID; nothing is requested
   */
  public ArgosDMP getPlanById(String id) {
    UUID planId = UUID.fromString(id);
    UriComponentsBuilder builder = apiUri().pathSegment("plan", "public", planId.toString());
    PLAN_FIELDS.forEach(field -> builder.queryParam("f", field));
    URI uri = builder.encode().build().toUri();
    JsonNode plan =
        restTemplate
            .exchange(uri, HttpMethod.GET, new HttpEntity<>(headers()), JsonNode.class)
            .getBody();
    // An empty body (204 or {}) would otherwise be saved as "null.json".
    if (plan == null
        || StringUtils.isBlank(plan.path("id").asText(null))
        || StringUtils.isBlank(plan.path("label").asText(null))) {
      throw new RestClientException("Argos returned no plan id or label");
    }
    return toPlan(plan);
  }

  /**
   * @param like optional text matched against a plan's title or description
   */
  public DataTableData<ArgosDMPListing> listPlans(Integer pageSize, Integer page, String like) {
    ObjectNode query = OBJECT_MAPPER.createObjectNode();
    query.putObject("page").put("offset", page * pageSize).put("size", pageSize);
    query.putObject("order").putArray("items").add("-updatedAt");
    query.putObject("metadata").put("countAll", true);
    query.putArray("isActive").add(1);
    LISTING_FIELDS.forEach(query.putObject("project").putArray("fields")::add);
    if (StringUtils.isNotBlank(like)) {
      query.put("like", like.trim());
    }
    URI uri = apiUri().pathSegment("plan", "public", "query").encode().build().toUri();
    JsonNode response =
        restTemplate
            .exchange(uri, HttpMethod.POST, new HttpEntity<>(query, headers()), JsonNode.class)
            .getBody();

    List<ArgosDMPListing> listings = new ArrayList<>();
    for (JsonNode item : elements(response, "items")) {
      ArgosDMPListing listing = new ArgosDMPListing();
      listing.id = item.path("id").asText(null);
      listing.label = item.path("label").asText(null);
      listing.grant = grantReference(item).map(ref -> ref.path("label").asText(null)).orElse(null);
      listing.createdAt = epochMillis(item.path("createdAt"));
      listing.modifiedAt = epochMillis(item.path("updatedAt"));
      listings.add(listing);
    }
    DataTableData<ArgosDMPListing> result = new DataTableData<>();
    result.setData(listings);
    result.setTotalCount(response == null ? 0 : response.path("count").asInt(listings.size()));
    return result;
  }

  public Boolean importDmp(String id) throws JsonProcessingException, IOException {
    User user = userManager.getAuthenticatedUserInSession();
    ArgosDMP dmpDetails = getPlanById(id);
    log.info("Importing DMP: " + id + ", " + dmpDetails.getLabel());

    String json = OBJECT_MAPPER.writeValueAsString(dmpDetails);
    InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    EcatDocumentFile file =
        mediaManager.saveNewDMP(dmpDetails.getLabel() + ".json", is, user, null);

    DmpDto dmpDto = new DmpDto(id, dmpDetails.getLabel(), DMPSource.ARGOS, null, null);
    DMPUser dmpUser =
        dmpManager.findByDmpId(dmpDto.getDmpId(), user).orElse(new DMPUser(user, dmpDto));
    if (file != null) {
      dmpUser.setDmpDownloadFile(file);
    } else {
      log.warn("Unexpected null DMP File - did download work?");
    }
    dmpManager.save(dmpUser);

    return Boolean.TRUE;
  }

  private UriComponentsBuilder apiUri() {
    return UriComponentsBuilder.fromUriString(StringUtils.removeEnd(baseUrl.toString(), "/"));
  }

  private static HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set(TENANT_HEADER, TENANT);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    return headers;
  }

  private static ArgosDMP toPlan(JsonNode plan) {
    ArgosDMP dmp = new ArgosDMP();
    dmp.id = plan.path("id").asText(null);
    dmp.label = plan.path("label").asText(null);
    grantReference(plan)
        .ifPresent(
            ref -> {
              ArgosDMP.ApiDMPGrant grant = new ArgosDMP.ApiDMPGrant();
              grant.id = ref.path("id").asText(null);
              grant.label = ref.path("label").asText(null);
              dmp.grant = grant;
            });
    List<ArgosDMP.ApiDMPDoi> dois = new ArrayList<>();
    for (JsonNode entityDoi : elements(plan, "entityDois")) {
      ArgosDMP.ApiDMPDoi doi = new ArgosDMP.ApiDMPDoi();
      doi.doi = entityDoi.path("doi").asText(null);
      doi.repositoryId = entityDoi.path("repositoryId").asText(null);
      dois.add(doi);
    }
    dmp.dois = dois;
    return dmp;
  }

  /** The plan's first active reference of type "grants", if any. */
  private static Optional<JsonNode> grantReference(JsonNode plan) {
    return StreamSupport.stream(elements(plan, "planReferences").spliterator(), false)
        .filter(ArgosDMPProvider::isActive)
        .map(planReference -> planReference.path("reference"))
        .filter(ref -> GRANTS_REFERENCE_TYPE.equals(ref.path("type").path("code").asText(null)))
        .findFirst();
  }

  // Argos sends isActive as the number 1; accept "1" too and treat a missing value as active.
  private static boolean isActive(JsonNode planReference) {
    JsonNode isActive = planReference.path("isActive");
    return isActive.isMissingNode() || isActive.isNull() || "1".equals(isActive.asText());
  }

  private static Iterable<JsonNode> elements(JsonNode node, String field) {
    if (node == null || !node.path(field).isArray()) {
      return List.of();
    }
    return node.path(field);
  }

  private static long epochMillis(JsonNode date) {
    if (!date.isTextual()) {
      return 0;
    }
    try {
      return Instant.parse(date.asText()).toEpochMilli();
    } catch (DateTimeParseException e) {
      log.warn("Unexpected Argos date '{}'", date.asText());
      return 0;
    }
  }
}
