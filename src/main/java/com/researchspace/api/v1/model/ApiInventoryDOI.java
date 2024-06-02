/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiAttributes;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierOtherProperty;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

/** External identifier based on DataCite IGSN. */
@Data
@EqualsAndHashCode
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "doiType",
      "doi",
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
      "customFieldsOnPublicPage",
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

  @JsonProperty("id")
  private Long id;

  @JsonProperty("doiType")
  private String doiType;

  @JsonProperty("doi")
  private String doi;

  @JsonProperty("creatorName")
  private String creatorName;

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

  @JsonProperty("url")
  private String url;

  @JsonProperty("rsPublicId")
  private String rsPublicId;

  @JsonProperty("publicUrl")
  private String publicUrl;

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

  public ApiInventoryDOI(DigitalObjectIdentifier identifier) {
    setId(identifier.getId());
    setDoi(identifier.getIdentifier());
    setDoiType(identifier.getType().toString());
    setState(identifier.getState());
    setCreatorName(identifier.getOtherData(IdentifierOtherProperty.CREATOR_NAME));
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
    setPublicUrl(identifier.getOtherData(IdentifierOtherProperty.PUBLIC_URL));
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

  public ApiInventoryDOI(DataCiteDoi dataCiteDoi) {
    setDoi(dataCiteDoi.getId());
    setDoiType(dataCiteDoi.getType().toString());
    setState(dataCiteDoi.getAttributes().getState());
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

  public boolean applyChangesToDatabaseDOI(DigitalObjectIdentifier dbIdentifier) {
    boolean contentChanged = false;

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
}
