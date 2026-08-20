/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
      "_links"
    })
public class ApiInventoryDOI extends LinkableApiObject {

  /**
   * Path of the anonymous public identifier page. Private because what callers share is {@link
   * #publicLandingPageUrl}, not this segment; going through the builder is what keeps the
   * registered and the stored address normalised identically.
   */
  private static final String PUBLIC_PAGE_PATH = "/public/inventory/";

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

  @JsonProperty("id")
  private Long id;

  @JsonProperty("doiType")
  private String doiType;

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

  @JsonProperty("state")
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
  // entity can never be handed an already-persisted publicLink. core-model's counterpart
  // DigitalObjectIdentifier.publicLink is locked down the same way.
  @JsonIgnore
  @Setter(AccessLevel.NONE)
  private String publicLinkSuffix;

  /** Generates the public link suffix for a brand-new identifier registration. */
  public void generatePublicLinkSuffix() {
    this.publicLinkSuffix = SecureStringUtils.getURLSafeSecureRandomString(16);
  }

  /**
   * The public landing page address for this DTO's suffix, or empty when no server URL is
   * configured or no suffix has been generated. Empty rather than site-relative for the same reason
   * as {@code GlobalIdUrls.globalIdUrl}: a wrong absolute URL registered with a provider cannot be
   * repaired once a curator accepts the record.
   */
  public Optional<String> getPublicLandingPageUrl(String serverUrl) {
    return publicLandingPageUrl(serverUrl, publicLinkSuffix);
  }

  /**
   * The public landing page address for any suffix, or empty when either part is missing. Shared
   * with the persistence side (ApiIdentifiersHelper builds the identifier's LOCAL_URL from the
   * entity's own publicLink) so the address RSpace registers and the address it stores are built
   * the same way: same trailing-slash handling, and the same refusal to produce a wrong absolute
   * URL from a blank server setting rather than emitting "null/public/inventory/...".
   */
  public static Optional<String> publicLandingPageUrl(String serverUrl, String suffix) {
    String trimmed = StringUtils.trimToEmpty(serverUrl);
    if (trimmed.isEmpty() || StringUtils.isBlank(suffix)) {
      return Optional.empty();
    }
    return Optional.of(StringUtils.removeEnd(trimmed, "/") + PUBLIC_PAGE_PATH + suffix);
  }

  /**
   * Whether an address names the public landing page of the identifier with this suffix, i.e.
   * whether RSpace itself wrote it. Lets a caller undo that write — clearing an instrument's
   * Landing page when its identifier is deleted — without touching an address a user chose (ADR
   * 0006).
   *
   * <p>Matched on the {@code /public/inventory/<suffix>} tail rather than by equality with the
   * address {@link #publicLandingPageUrl} would build today, for the same reason the globalId check
   * matches a tail: the deployment's server URL may have changed since, and the question is what
   * RSpace wrote, not what it would write now. A blank suffix matches nothing rather than
   * everything.
   */
  public static boolean namesPublicLandingPage(String address, String suffix) {
    return StringUtils.isNotBlank(suffix)
        && StringUtils.endsWithIgnoreCase(
            StringUtils.stripEnd(StringUtils.trimToEmpty(address), "/"), PUBLIC_PAGE_PATH + suffix);
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
    if (getDoi() != null) {
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
