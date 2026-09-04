/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiAttributes;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierOtherProperty;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;

/** External identifier based on DataCite IGSN. */
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "doiType",
      "doi",
      "associatedGlobalId",
      "creatorName",
      "creatorType",
      "creatorAffiliation",
      "creatorAffiliationIdentifier",
      "title",
      "publisher",
      "publicationYear",
      "state",
      "resourceType",
      "resourceTypeGeneral",
      "url",
      "subjects",
      "descriptions",
      "geoLocations",
      "alternateIdentifiers",
      "dates",
      "rsPublicId",
      "publicUrl",
      "providerUrl",
      "customFieldsOnPublicPage",
      "externalMetadataUpdate",
      "_links"
    })
public class ApiInventoryDOI extends LinkableApiObject {

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryDOISubject {
    private String value;
    private String subjectScheme;
    private String schemeURI;
    private String valueURI;
    private String classificationCode;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryDOIDescription {
    public enum DoiDescriptionType {
      ABSTRACT,
      METHODS,
      SERIESINFORMATION,
      TABLEOFCONTENTS,
      TECHNICALINFO,
      OTHER;
    }

    private String value;
    private DoiDescriptionType type;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryDOIAlternateIdentifier {
    private String value;
    private String freeType;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiInventoryDOIDate {

    public enum DoiDateType {
      ACCEPTED,
      AVAILABLE,
      COPYRIGHTED,
      COLLECTED,
      CREATED,
      ISSUED,
      SUBMITTED,
      UPDATED,
      VALID,
      WITHDRAWN,
      OTHER;
    }

    private String value;
    private DoiDateType type;
  }

  /**
   * Outcome of the external metadata update attempted while saving the record this identifier
   * belongs to (RSDEV-1251, ADR 0008). Response-only and never persisted: it describes one push,
   * not a state of the identifier, so it is absent from the next read of the same identifier.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ApiExternalMetadataUpdate {

    /**
     * The same result in machine-readable form, so a client can tell a provider failure from a
     * record its own state has frozen without matching the wording of {@link #reason} (RSDEV-1356).
     * Additive alongside {@link #succeeded}, which stays for existing clients.
     */
    public enum Outcome {
      /** The provider accepted the rebuilt metadata. */
      UPDATED,
      /**
       * The provider could not be reached or rejected the update, or the payload could not be
       * built. The instrument is saved; saving it again retries.
       */
      FAILED,
      /** The record's own state no longer allows an in-place update. Expected, not an error. */
      NOT_UPDATABLE
    }

    /** Whether the provider accepted the rebuilt metadata. */
    @JsonProperty("succeeded")
    private boolean succeeded;

    @JsonProperty("outcome")
    private Outcome outcome;

    /**
     * Localized sentence for the user: what was updated, or why it was not. Carries the provider's
     * own words for a rejection, which cannot be translated but say more than a generic failure.
     */
    @JsonProperty("reason")
    private String reason;
  }

  @JsonProperty("id")
  private Long id;

  @JsonProperty("doiType")
  private String doiType;

  /**
   * The provider's own record id: a B2INST draft RID, or a DataCite DOI.
   *
   * <p>Writable on the way in, but only ever applied to a <em>new</em> identifier - see the
   * immutability guard in {@link #applyChangesToDatabaseDOI(DigitalObjectIdentifier)}, which is
   * what stops a client retargeting an existing identifier at someone else's provider record.
   * Deliberately not {@code Access.READ_ONLY} like {@link #state} and the URL properties: this
   * value is part of every identifier response, and READ_ONLY would stop a Java client (our own
   * MVCITs included) reading it back out of one.
   */
  @JsonProperty("doi")
  private String doi;

  @JsonProperty("associatedGlobalId")
  private String associatedGlobalId;

  @JsonProperty("creatorName")
  private String creatorName;

  @JsonIgnore private User owner;

  @JsonProperty("creatorType")
  private String creatorType;

  @JsonProperty("creatorAffiliation")
  private String creatorAffiliation;

  @JsonProperty("creatorAffiliationIdentifier")
  private String creatorAffiliationIdentifier;

  @JsonProperty("title")
  private String title;

  @JsonProperty("publisher")
  private String publisher;

  @JsonProperty("publicationYear")
  private Integer publicationYear;

  /**
   * The identifier's publication state, owned by the server.
   *
   * <p>{@link JsonProperty.Access#READ_ONLY} for the same reason as {@link #url}, {@link
   * #publicUrl} and {@link #providerUrl}, and more pressingly: this is the gate on the
   * unauthenticated public landing page. {@code DigitalObjectIdentifier.isPublishedState} opens
   * that page for {@code findable} and {@code accepted}, and {@link
   * #applyChangesToDatabaseDOI(DigitalObjectIdentifier)} copies this field straight onto the
   * entity, so without this a record update carrying {@code "state": "accepted"} would publish the
   * page with no provider registration and no B2INST curator review behind it. State only ever
   * changes through the register, publish, retract and refresh operations, which set it in Java and
   * are unaffected by this annotation.
   */
  @JsonProperty(value = "state", access = JsonProperty.Access.READ_ONLY)
  private String state;

  @JsonProperty("resourceType")
  private String resourceType;

  @JsonProperty("resourceTypeGeneral")
  private String resourceTypeGeneral;

  /**
   * The identifier's target address: what a resolved DOI points at, and the {@code url} RSpace
   * sends to DataCite.
   *
   * <p>The third server-owned URL on this class, for the same reason as {@link #publicUrl} and
   * {@link #providerUrl}. It is rendered into an {@code <externalLink>} in the identifier state
   * messages, so a client-supplied value would put an attacker-chosen href behind benign link text,
   * and it is also the outbound DataCite target. Every server-side write is a Java setter — from
   * the stored property, from a DataCite response, and at publish — so {@link
   * JsonProperty.Access#READ_ONLY} costs nothing.
   */
  @JsonProperty(value = "url", access = JsonProperty.Access.READ_ONLY)
  private String url;

  @JsonProperty("rsPublicId")
  private String rsPublicId;

  /**
   * Suffix of this identifier's public landing page address ({@code
   * <serverUrl>/public/inventory/<suffix>}). Generated via {@link #generatePublicLinkSuffix()}
   * before a new identifier is registered with an external provider, so the page's address can be
   * part of the registration payload, and threaded into {@link DigitalObjectIdentifier}'s
   * constructor so the entity's {@code publicLink} names the same page (RSDEV-1254, ADR 0006).
   * Server-internal: never serialized and never readable from a client payload; {@link #rsPublicId}
   * is the client-facing copy of the entity value. Deliberately NOT generated by the no-args
   * constructor, which backs Jackson and the sparse update DTOs.
   */
  // No setter: generatePublicLinkSuffix() is the only way to populate this, so a brand-new
  // entity can never be handed an already-persisted publicLink. The counterpart entity field
  // DigitalObjectIdentifier.publicLink is locked down the same way.
  // ToString.Exclude so the "kept out of logs" rule is enforced by the generated toString
  // rather than left to every future caller to remember.
  @JsonIgnore
  @Setter(AccessLevel.NONE)
  @ToString.Exclude
  private String publicLinkSuffix;

  /** Generates the public link suffix for a brand-new identifier registration. */
  public void generatePublicLinkSuffix() {
    this.publicLinkSuffix = SecureStringUtils.getURLSafeSecureRandomString(16);
  }

  /**
   * The citable, publicly resolvable address of the identifier, set when it is published.
   *
   * <p>Server-owned for the same reason as {@link #providerUrl}, and more importantly so: this one
   * is rendered on the unauthenticated public identifier page, so a client-supplied value would
   * reach readers who never signed in. It is only ever written from the DOI at publish time, so
   * {@link JsonProperty.Access#READ_ONLY} costs nothing; a client that still sends it is ignored
   * rather than rejected, and the stored value stands.
   */
  @JsonProperty(value = "publicUrl", access = JsonProperty.Access.READ_ONLY)
  private String publicUrl;

  /**
   * The record's page on the issuing provider, for example the B2INST deposit page. Unlike {@link
   * #publicUrl} this exists from registration onwards, and viewing it may require signing in to
   * that provider, so it is not a citable public URL.
   *
   * <p>Server-owned: it only ever legitimately comes from the B2INST draft response, so it is
   * {@link JsonProperty.Access#READ_ONLY} to stop a client supplying its own. Without that, any
   * user who can edit the instrument could PUT an arbitrary URL here and every viewer of the record
   * would see a link whose visible text is the harmless identifier value. The Java setter is
   * unaffected, so the registration path still writes it.
   */
  @JsonProperty(value = "providerUrl", access = JsonProperty.Access.READ_ONLY)
  private String providerUrl;

  @JsonProperty("customFieldsOnPublicPage")
  private Boolean customFieldsOnPublicPage;

  @JsonProperty("subjects")
  private List<ApiInventoryDOISubject> subjects;

  @JsonProperty("descriptions")
  private List<ApiInventoryDOIDescription> descriptions;

  @JsonProperty("geoLocations")
  private List<ApiInventoryDOIGeoLocation> geoLocations;

  @JsonProperty("alternateIdentifiers")
  private List<ApiInventoryDOIAlternateIdentifier> alternateIdentifiers;

  @JsonProperty("dates")
  private List<ApiInventoryDOIDate> dates;

  /**
   * Set on the way out by the instrument save that pushed to the provider, and by nothing else.
   *
   * <p>{@link JsonProperty.Access#READ_ONLY} and excluded from equality: it is transient decoration
   * of one response, so a client sending it is ignored and two identifiers carrying the same
   * metadata still compare equal. {@code NON_NULL} keeps the property out of the payload entirely
   * when no push was attempted, which is how a client tells "not eligible" from "attempted".
   * Deliberately absent from {@link #applyChangesToDatabaseDOI(DigitalObjectIdentifier)}: there is
   * no column for it, by decision (ADR 0008 item 4).
   */
  @JsonProperty(value = "externalMetadataUpdate", access = JsonProperty.Access.READ_ONLY)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @EqualsAndHashCode.Exclude
  @ToString.Exclude
  private ApiExternalMetadataUpdate externalMetadataUpdate;

  @JsonIgnore
  public User getOwner() {
    return owner;
  }

  public ApiInventoryDOI(DigitalObjectIdentifier identifier) {
    setId(identifier.getId());
    setDoi(identifier.getIdentifier());
    setDoiType(identifier.getType().toString());
    setAssociatedGlobalId(identifier.getConnectedRecordGlobalIdentifier());
    setState(identifier.getState());
    setCreatorName(identifier.getOtherData(IdentifierOtherProperty.CREATOR_NAME));
    setOwner(identifier.getOwner());
    setCreatorType(identifier.getOtherData(IdentifierOtherProperty.CREATOR_TYPE));
    setCreatorAffiliation(identifier.getOtherData(IdentifierOtherProperty.CREATOR_AFFILIATION));
    setCreatorAffiliationIdentifier(
        identifier.getOtherData(IdentifierOtherProperty.CREATOR_AFFILIATION_IDENTIFIER));
    setTitle(identifier.getTitle());
    setPublisher(identifier.getOtherData(IdentifierOtherProperty.PUBLISHER));
    String publicationYear = identifier.getOtherData(IdentifierOtherProperty.PUBLICATION_YEAR);
    setPublicationYear(publicationYear == null ? null : Integer.valueOf(publicationYear));
    setResourceType(identifier.getOtherData(IdentifierOtherProperty.RESOURCE_TYPE));
    setResourceTypeGeneral(identifier.getOtherData(IdentifierOtherProperty.RESOURCE_TYPE_GENERAL));
    setUrl(identifier.getOtherData(IdentifierOtherProperty.LOCAL_URL));
    setRsPublicId(identifier.getPublicLink());
    // deliberately NOT copied into publicLinkSuffix: that field means "the suffix to give a
    // brand-new entity", and both its readers hand it to a new DigitalObjectIdentifier. Copying an
    // already-persisted publicLink here would let a second row claim an existing identifier's
    // public page, which UNIQUE KEY isPublicLink rejects at flush - after the provider call in the
    // same transaction has already created a draft. rsPublicId above carries the value for reads.
    setPublicUrl(identifier.getOtherData(IdentifierOtherProperty.PUBLIC_URL));
    setProviderUrl(identifier.getOtherData(IdentifierOtherProperty.PROVIDER_URL));
    setCustomFieldsOnPublicPage(identifier.isCustomFieldsOnPublicPage());

    setSubjects(
        convertSubjectsFromOtherData(
            identifier.getOtherListData(
                DigitalObjectIdentifier.IdentifierOtherListProperty.SUBJECTS)));
    setDescriptions(
        convertDescriptionsFromOtherData(
            identifier.getOtherListData(
                DigitalObjectIdentifier.IdentifierOtherListProperty.DESCRIPTIONS)));
    setAlternateIdentifiers(
        convertRelatedIdentifiersFromOtherData(
            identifier.getOtherListData(
                DigitalObjectIdentifier.IdentifierOtherListProperty.RELATED_IDENTIFIERS)));
    setDates(
        convertDatesFromOtherData(
            identifier.getOtherListData(
                DigitalObjectIdentifier.IdentifierOtherListProperty.DATES)));
    setGeoLocations(
        convertGeolocationsFromOtherData(
            identifier.getOtherListData(
                DigitalObjectIdentifier.IdentifierOtherListProperty.GEOLOCATIONS)));
  }

  public ApiInventoryDOI(User createdBy, DataCiteDoi dataCiteDoi) {
    setDoi(dataCiteDoi.getId());
    setDoiType(dataCiteDoi.getType().toString());
    setState(dataCiteDoi.getAttributes().getState());
    setOwner(createdBy);
    if (CollectionUtils.isNotEmpty(dataCiteDoi.getAttributes().getCreators())) {
      DataCiteDoiAttributes.Creator creator = dataCiteDoi.getAttributes().getCreators().get(0);
      setCreatorName(creator.getName());
      setCreatorType(creator.getNameType());
      setCreatorAffiliation(creator.getAffiliation()[0].getName());
      setCreatorAffiliationIdentifier(creator.getAffiliation()[0].getAffiliationIdentifier());
    }
    if (CollectionUtils.isNotEmpty(dataCiteDoi.getAttributes().getTitles())) {
      setTitle(dataCiteDoi.getAttributes().getTitles().get(0).getTitle());
    }
    setPublisher(dataCiteDoi.getAttributes().getPublisher());
    setPublicationYear(dataCiteDoi.getAttributes().getPublicationYear());
    if (dataCiteDoi.getAttributes().getTypes() != null) {
      setResourceType(dataCiteDoi.getAttributes().getTypes().getResourceType());
      setResourceTypeGeneral(dataCiteDoi.getAttributes().getTypes().getResourceTypeGeneral());
    }
    setUrl(dataCiteDoi.getAttributes().getUrl());
  }

  /* not to be set by API client, but rather internally by identifier minting code  */
  private boolean registerIdentifierRequest;

  @JsonIgnore
  public boolean isRegisterIdentifierRequest() {
    return registerIdentifierRequest;
  }

  private boolean deleteIdentifierRequest;

  @JsonIgnore
  public boolean isDeleteIdentifierRequest() {
    return deleteIdentifierRequest;
  }

  private boolean assignIdentifierRequest;

  @JsonIgnore
  public boolean isAssignIdentifierRequest() {
    return assignIdentifierRequest;
  }

  public boolean applyChangesToDatabaseDOI(DigitalObjectIdentifier dbIdentifier) {
    boolean contentChanged = false;

    // doiType may hold values that are not IdentifierType names (e.g. the "dois" JSON:API
    // literal copied from DataCite responses), which must not override the entity default.
    // Only apply the incoming type when creating a new identifier (transient, no id yet):
    // an existing identifier's type is immutable so it keeps its provider routing (IGSN vs
    // PIDINST).
    IdentifierType incomingType = EnumUtils.getEnum(IdentifierType.class, getDoiType());
    if (dbIdentifier.getId() == null
        && incomingType != null
        && !incomingType.equals(dbIdentifier.getType())) {
      dbIdentifier.setType(incomingType);
      contentChanged = true;
    }
    /*
     * Only while creating, exactly as with the type above. This value is the ADDRESS of the record
     * at the provider, and the on-save external metadata update (RSDEV-1251, ADR 0008) sends the
     * instrument's metadata to whatever record it names, using the deployment's own credentials.
     * The id check in ApiInventoryRecordInfo.applyChangesToDatabaseIdentifiers stops a client
     * naming ANOTHER record's identifier row, but not a client retargeting its OWN row: without
     * this guard, one instrument PUT carrying a foreign RID or DOI would overwrite that external
     * record. Registration is unaffected - it applies to a transient entity, having taken the id
     * from the provider's own response.
     */
    if (dbIdentifier.getId() == null && getDoi() != null) {
      if (!getDoi().equals(dbIdentifier.getIdentifier())) {
        dbIdentifier.setIdentifier(getDoi());
        contentChanged = true;
      }
    }
    if (getState() != null) {
      if (!getState().equals(dbIdentifier.getState())) {
        dbIdentifier.setState(getState());
        contentChanged = true;
      }
    }
    if (getCreatorName() != null) {
      if (!getCreatorName()
          .equals(dbIdentifier.getOtherData(IdentifierOtherProperty.CREATOR_NAME))) {
        dbIdentifier.addOtherData(IdentifierOtherProperty.CREATOR_NAME, getCreatorName());
        contentChanged = true;
      }
    }
    if (getOwner() != null) {
      if (!getOwner().equals(dbIdentifier.getOwner())) {
        dbIdentifier.setOwner(getOwner());
        contentChanged = true;
      }
    }
    if (getCreatorType() != null) {
      if (!getCreatorType()
          .equals(dbIdentifier.getOtherData(IdentifierOtherProperty.CREATOR_TYPE))) {
        dbIdentifier.addOtherData(IdentifierOtherProperty.CREATOR_TYPE, getCreatorType());
        contentChanged = true;
      }
    }
    if (getCreatorAffiliation() != null) {
      if (!getCreatorAffiliation()
          .equals(dbIdentifier.getOtherData(IdentifierOtherProperty.CREATOR_AFFILIATION))) {
        dbIdentifier.addOtherData(
            IdentifierOtherProperty.CREATOR_AFFILIATION, getCreatorAffiliation());
        contentChanged = true;
      }
    }
    if (getCreatorAffiliationIdentifier() != null) {
      if (!getCreatorAffiliationIdentifier()
          .equals(
              dbIdentifier.getOtherData(IdentifierOtherProperty.CREATOR_AFFILIATION_IDENTIFIER))) {
        dbIdentifier.addOtherData(
            IdentifierOtherProperty.CREATOR_AFFILIATION_IDENTIFIER,
            getCreatorAffiliationIdentifier());
        contentChanged = true;
      }
    }
    if (getTitle() != null) {
      if (!getTitle().equals(dbIdentifier.getTitle())) {
        dbIdentifier.setTitle(getTitle());
        contentChanged = true;
      }
    }
    if (getPublisher() != null) {
      if (!getPublisher().equals(dbIdentifier.getOtherData(IdentifierOtherProperty.PUBLISHER))) {
        dbIdentifier.addOtherData(IdentifierOtherProperty.PUBLISHER, getPublisher());
        contentChanged = true;
      }
    }
    if (getPublicationYear() != null) {
      if (!getPublicationYear()
          .toString()
          .equals(dbIdentifier.getOtherData(IdentifierOtherProperty.PUBLICATION_YEAR))) {
        dbIdentifier.addOtherData(
            IdentifierOtherProperty.PUBLICATION_YEAR, getPublicationYear().toString());
        contentChanged = true;
      }
    }
    if (getResourceType() != null) {
      if (!getResourceType()
          .equals(dbIdentifier.getOtherData(IdentifierOtherProperty.RESOURCE_TYPE))) {
        dbIdentifier.addOtherData(IdentifierOtherProperty.RESOURCE_TYPE, getResourceType());
        contentChanged = true;
      }
    }
    if (getResourceTypeGeneral() != null) {
      if (!getResourceTypeGeneral()
          .equals(dbIdentifier.getOtherData(IdentifierOtherProperty.RESOURCE_TYPE_GENERAL))) {
        dbIdentifier.addOtherData(
            IdentifierOtherProperty.RESOURCE_TYPE_GENERAL, getResourceTypeGeneral());
        contentChanged = true;
      }
    }
    if (getUrl() != null) {
      if (!getUrl().equals(dbIdentifier.getOtherData(IdentifierOtherProperty.LOCAL_URL))) {
        dbIdentifier.addOtherData(IdentifierOtherProperty.LOCAL_URL, getUrl());
        contentChanged = true;
      }
    }
    if (getPublicUrl() != null) {
      if (!getPublicUrl().equals(dbIdentifier.getOtherData(IdentifierOtherProperty.PUBLIC_URL))) {
        dbIdentifier.addOtherData(IdentifierOtherProperty.PUBLIC_URL, getPublicUrl());
        contentChanged = true;
      }
    }
    if (getProviderUrl() != null) {
      if (!getProviderUrl()
          .equals(dbIdentifier.getOtherData(IdentifierOtherProperty.PROVIDER_URL))) {
        dbIdentifier.addOtherData(IdentifierOtherProperty.PROVIDER_URL, getProviderUrl());
        contentChanged = true;
      }
    }
    if (getCustomFieldsOnPublicPage() != null) {
      if (!getCustomFieldsOnPublicPage().equals(dbIdentifier.isCustomFieldsOnPublicPage())) {
        dbIdentifier.setCustomFieldsOnPublicPage(getCustomFieldsOnPublicPage());
        contentChanged = true;
      }
    }

    if (getSubjects() != null) {
      List<String> subjectsAsStringList =
          getSubjects().stream().map(s -> JacksonUtil.toJson(s)).collect(Collectors.toList());
      dbIdentifier.addOtherListData(
          DigitalObjectIdentifier.IdentifierOtherListProperty.SUBJECTS, subjectsAsStringList);
    }
    if (getDescriptions() != null) {
      List<String> descriptions =
          getDescriptions().stream().map(s -> JacksonUtil.toJson(s)).collect(Collectors.toList());
      dbIdentifier.addOtherListData(
          DigitalObjectIdentifier.IdentifierOtherListProperty.DESCRIPTIONS, descriptions);
    }
    if (getAlternateIdentifiers() != null) {
      List<String> relIdentifiers =
          getAlternateIdentifiers().stream()
              .map(s -> JacksonUtil.toJson(s))
              .collect(Collectors.toList());
      dbIdentifier.addOtherListData(
          DigitalObjectIdentifier.IdentifierOtherListProperty.RELATED_IDENTIFIERS, relIdentifiers);
    }
    if (getDates() != null) {
      List<String> dates =
          getDates().stream().map(s -> JacksonUtil.toJson(s)).collect(Collectors.toList());
      dbIdentifier.addOtherListData(
          DigitalObjectIdentifier.IdentifierOtherListProperty.DATES, dates);
    }
    if (getGeoLocations() != null) {
      List<String> geolocations =
          getGeoLocations().stream().map(s -> JacksonUtil.toJson(s)).collect(Collectors.toList());
      dbIdentifier.addOtherListData(
          DigitalObjectIdentifier.IdentifierOtherListProperty.GEOLOCATIONS, geolocations);
    }

    return contentChanged;
  }

  private List<ApiInventoryDOISubject> convertSubjectsFromOtherData(
      List<String> subjectStringList) {
    return convertOtherListData(subjectStringList, ApiInventoryDOISubject.class);
  }

  private List<ApiInventoryDOIDescription> convertDescriptionsFromOtherData(
      List<String> descStringList) {
    return convertOtherListData(descStringList, ApiInventoryDOIDescription.class);
  }

  private List<ApiInventoryDOIAlternateIdentifier> convertRelatedIdentifiersFromOtherData(
      List<String> descStringList) {
    return convertOtherListData(descStringList, ApiInventoryDOIAlternateIdentifier.class);
  }

  private List<ApiInventoryDOIDate> convertDatesFromOtherData(List<String> descStringList) {
    return convertOtherListData(descStringList, ApiInventoryDOIDate.class);
  }

  private List<ApiInventoryDOIGeoLocation> convertGeolocationsFromOtherData(
      List<String> geolocsStringList) {
    return convertOtherListData(geolocsStringList, ApiInventoryDOIGeoLocation.class);
  }

  private <T> List<T> convertOtherListData(List<String> stringList, Class<T> elemType) {
    if (stringList == null) {
      return null;
    }
    return stringList.stream()
        .map(s -> JacksonUtil.fromJson(s, elemType))
        .collect(Collectors.toList());
  }

  public DataCiteDoi convertToDataCiteDoi() {
    DataCiteDoi dataCiteDoi = new DataCiteDoi();
    dataCiteDoi.setId(getDoi());
    dataCiteDoi.getAttributes().setTitles(List.of(new DataCiteDoiAttributes.Title(getTitle())));
    DataCiteDoiAttributes.Affiliation affiliation = null;
    if (!StringUtils.isEmpty(getCreatorAffiliation())) {
      affiliation =
          new DataCiteDoiAttributes.Affiliation(
              getCreatorAffiliation(), getCreatorAffiliationIdentifier());
    }
    if (affiliation != null) {
      dataCiteDoi
          .getAttributes()
          .setCreators(
              List.of(
                  new DataCiteDoiAttributes.Creator(
                      getCreatorName(),
                      getCreatorType(),
                      new DataCiteDoiAttributes.Affiliation[] {affiliation})));
    } else {
      dataCiteDoi
          .getAttributes()
          .setCreators(
              List.of(new DataCiteDoiAttributes.Creator(getCreatorName(), getCreatorType())));
    }
    dataCiteDoi.getAttributes().setPublisher(getPublisher());
    if (getPublicationYear() != null) {
      dataCiteDoi.getAttributes().setPublicationYear(getPublicationYear());
    }
    dataCiteDoi
        .getAttributes()
        .setTypes(new DataCiteDoiAttributes.Types(getResourceType(), getResourceTypeGeneral()));
    dataCiteDoi.getAttributes().setUrl(getUrl());

    if (getSubjects() != null) {
      List<DataCiteDoiAttributes.Subject> subjects =
          getSubjects().stream()
              .map(
                  apiSubject -> {
                    DataCiteDoiAttributes.Subject dcSubject = new DataCiteDoiAttributes.Subject();
                    dcSubject.setSubject(apiSubject.getValue());
                    dcSubject.setSubjectScheme(apiSubject.getSubjectScheme());
                    dcSubject.setSchemeUri(apiSubject.getSchemeURI());
                    dcSubject.setValueUri(apiSubject.getValueURI());
                    dcSubject.setClassificationCode(apiSubject.getClassificationCode());
                    return dcSubject;
                  })
              .collect(Collectors.toList());
      dataCiteDoi.getAttributes().setSubjects(subjects);
    }
    if (getDescriptions() != null) {
      List<DataCiteDoiAttributes.Description> descriptions =
          getDescriptions().stream()
              .map(
                  apiDesc -> {
                    DataCiteDoiAttributes.Description dcDesc =
                        new DataCiteDoiAttributes.Description();
                    dcDesc.setDescription(apiDesc.getValue());
                    dcDesc.setDescriptionType(
                        StringUtils.capitalize(apiDesc.getType().name().toLowerCase()));
                    return dcDesc;
                  })
              .collect(Collectors.toList());
      dataCiteDoi.getAttributes().setDescriptions(descriptions);
    }
    if (getAlternateIdentifiers() != null) {
      List<DataCiteDoiAttributes.AlternateIdentifier> alternateIdentifiers =
          getAlternateIdentifiers().stream()
              .map(
                  apiAltId -> {
                    DataCiteDoiAttributes.AlternateIdentifier dcAltId =
                        new DataCiteDoiAttributes.AlternateIdentifier();
                    dcAltId.setAlternateIdentifier(apiAltId.getValue());
                    dcAltId.setAlternateIdentifierType(apiAltId.getFreeType());
                    return dcAltId;
                  })
              .collect(Collectors.toList());
      dataCiteDoi.getAttributes().setAlternateIdentifiers(alternateIdentifiers);
    }
    if (getDates() != null) {
      List<DataCiteDoiAttributes.DoiDate> dates =
          getDates().stream()
              .map(
                  apiDate -> {
                    DataCiteDoiAttributes.DoiDate dcDate = new DataCiteDoiAttributes.DoiDate();
                    dcDate.setDate(apiDate.getValue());
                    dcDate.setDateType(
                        StringUtils.capitalize(apiDate.getType().name().toLowerCase()));
                    return dcDate;
                  })
              .collect(Collectors.toList());
      dataCiteDoi.getAttributes().setDates(dates);
    }
    if (getGeoLocations() != null) {
      List<DataCiteDoiAttributes.GeoLocation> geolocations =
          getGeoLocations().stream()
              .map(
                  apiGeoLocation -> {
                    DataCiteDoiAttributes.GeoLocation dcGeolocation =
                        new DataCiteDoiAttributes.GeoLocation();
                    if (StringUtils.isNotBlank(apiGeoLocation.getGeoLocationPlace())) {
                      dcGeolocation.setGeoLocationPlace(apiGeoLocation.getGeoLocationPlace());
                    }
                    if (apiGeoLocation.getGeoLocationPoint() != null) {
                      if (StringUtils.isNotBlank(
                              apiGeoLocation.getGeoLocationPoint().getPointLatitude())
                          && StringUtils.isNotBlank(
                              apiGeoLocation.getGeoLocationPoint().getPointLongitude())) {
                        dcGeolocation.setGeoLocationPoint(
                            new DataCiteDoiAttributes.GeoLocationPoint(
                                apiGeoLocation.getGeoLocationPoint().getPointLatitude(),
                                apiGeoLocation.getGeoLocationPoint().getPointLongitude()));
                      }
                    }
                    if (apiGeoLocation.getGeoLocationBox() != null) {
                      if (StringUtils.isNotBlank(
                              apiGeoLocation.getGeoLocationBox().getWestBoundLongitude())
                          && StringUtils.isNotBlank(
                              apiGeoLocation.getGeoLocationBox().getEastBoundLongitude())
                          && StringUtils.isNotBlank(
                              apiGeoLocation.getGeoLocationBox().getSouthBoundLatitude())
                          && StringUtils.isNotBlank(
                              apiGeoLocation.getGeoLocationBox().getNorthBoundLatitude())) {
                        dcGeolocation.setGeoLocationBox(
                            new DataCiteDoiAttributes.GeoLocationBox(
                                apiGeoLocation.getGeoLocationBox().getWestBoundLongitude(),
                                apiGeoLocation.getGeoLocationBox().getEastBoundLongitude(),
                                apiGeoLocation.getGeoLocationBox().getSouthBoundLatitude(),
                                apiGeoLocation.getGeoLocationBox().getNorthBoundLatitude()));
                      }
                    }
                    if (CollectionUtils.isNotEmpty(apiGeoLocation.getGeoLocationPolygon())) {
                      List<DataCiteDoiAttributes.GeoLocationPolygonPoint> polygonPoints =
                          apiGeoLocation.getGeoLocationPolygon().stream()
                              .filter(
                                  apiPolygonPoint ->
                                      apiPolygonPoint.getPolygonPoint() != null
                                          && StringUtils.isNotBlank(
                                              apiPolygonPoint.getPolygonPoint().getPointLatitude())
                                          && StringUtils.isNotBlank(
                                              apiPolygonPoint
                                                  .getPolygonPoint()
                                                  .getPointLongitude()))
                              .map(
                                  apiPolygonPoint -> {
                                    DataCiteDoiAttributes.GeoLocationPolygonPoint dcPolygonPoint =
                                        new DataCiteDoiAttributes.GeoLocationPolygonPoint();
                                    dcPolygonPoint.setPolygonPoint(
                                        new DataCiteDoiAttributes.GeoLocationPoint(
                                            apiPolygonPoint.getPolygonPoint().getPointLatitude(),
                                            apiPolygonPoint.getPolygonPoint().getPointLongitude()));
                                    return dcPolygonPoint;
                                  })
                              .collect(Collectors.toList());
                      if (CollectionUtils.isNotEmpty(polygonPoints)) {
                        dcGeolocation.setGeoLocationPolygon(polygonPoints);
                      }
                    }
                    return dcGeolocation;
                  })
              .collect(Collectors.toList());
      if (CollectionUtils.isNotEmpty(geolocations)) {
        dataCiteDoi.getAttributes().setGeoLocations(geolocations);
      }
    }
    return dataCiteDoi;
  }

  @JsonIgnore
  public boolean isAssociated() {
    return associatedGlobalId != null;
  }
}
