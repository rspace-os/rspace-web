package com.researchspace.service.inventory.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiPidinstRecord;
import com.researchspace.b2inst.model.metadata.B2instAlternateIdentifier;
import com.researchspace.b2inst.model.metadata.B2instDate;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.metadata.B2instInstrumentType;
import com.researchspace.b2inst.model.metadata.B2instManufacturer;
import com.researchspace.b2inst.model.metadata.B2instOwner;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiAttributes;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.EditInfo;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

/**
 * The inverse of {@link RspaceToExternalProviderAdapterImpl}: a provider's instrument record into
 * the unified {@link ApiPidinstRecord}, and that record onto the default PIDINST template's fields
 * plus the linked identifier (CONTEXT.md, "Instrument import"; ADR 0009). Pure functions, no
 * Spring.
 *
 * <p>The mapping table is decided (see .claude/RSDEV-1326-plan.md, decision 3). Values are cut to
 * what RSpace can store rather than refused: name to 255, description to 250 with an ellipsis,
 * field content to 1000. Only a missing mandatory Owner or Manufacturer refuses the import, because
 * a placeholder in a mandatory field would be worse data than no record.
 */
final class PidinstRecordMapper {

  static final String JOIN = "; ";
  static final String ELLIPSIS = "…";

  /** The {@code data} column of InventoryEntityField. */
  static final int FIELD_MAX = 1000;

  static final String RESOURCE_TYPE_INSTRUMENT = "Instrument";
  private static final String HOSTING_INSTITUTION = "HostingInstitution";
  private static final String DESCRIPTION_ABSTRACT = "Abstract";
  private static final String DESCRIPTION_TECHNICAL_INFO = "TechnicalInfo";
  private static final String STATE_ACCEPTED = "accepted";
  private static final Pattern ISO_DATE_PREFIX = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");

  private PidinstRecordMapper() {}

  static ApiPidinstRecord fromB2inst(B2instDraftRecord record) {
    ApiPidinstRecord result = new ApiPidinstRecord();
    result.setProvider(IdentifierType.PIDINST_B2INST.name());
    B2instInstrumentMetadata md =
        record.getMetadata() == null ? new B2instInstrumentMetadata() : record.getMetadata();
    String handle =
        md.getIdentifier() == null
            ? null
            : StringUtils.trimToNull(md.getIdentifier().getIdentifierValue());
    result.setPid(handle);
    result.setPublicUrl(handle == null ? null : "https://hdl.handle.net/" + handle);
    result.setProviderRecordUrl(record.getLinks() == null ? null : record.getLinks().getSelfHtml());
    // a published record is what RSpace calls accepted: the review that publishes it is over
    result.setState(
        Boolean.TRUE.equals(record.getIsPublished())
            ? STATE_ACCEPTED
            : StringUtils.defaultIfBlank(record.getStatus(), "draft"));
    result.setName(StringUtils.trimToNull(md.getName()));
    result.setDescription(StringUtils.trimToNull(md.getDescription()));
    result.setOwners(names(md.getOwner(), B2instOwner::getOwnerName));
    result.setManufacturers(names(md.getManufacturer(), B2instManufacturer::getManufacturerName));
    result.setModel(
        md.getModel() == null ? null : StringUtils.trimToNull(md.getModel().getModelName()));
    result.setInstrumentTypes(
        names(md.getInstrumentType(), B2instInstrumentType::getInstrumentTypeName));
    result.setMeasuredVariables(names(md.getMeasuredVariable(), Function.identity()));
    if (md.getDate() != null) {
      for (B2instDate date : md.getDate()) {
        if (date == null || date.getDateType() == null) {
          continue;
        }
        // B2INST writes "DeCommissioned"; compared case-insensitively so either spelling maps
        if ("commissioned".equalsIgnoreCase(date.getDateType())
            && result.getCommissioned() == null) {
          result.setCommissioned(isoDate(date.getDate()));
        } else if ("decommissioned".equalsIgnoreCase(date.getDateType())
            && result.getDecommissioned() == null) {
          result.setDecommissioned(isoDate(date.getDate()));
        }
      }
    }
    result.setLandingPage(resolvableUrl(md.getLandingPage()));
    result.setAlternateIdentifier(
        first(
            names(
                md.getAlternateIdentifier(),
                B2instAlternateIdentifier::getAlternateIdentifierValue)));
    result.setCreated(record.getCreated());
    result.setUpdated(record.getUpdated());
    return result;
  }

  static ApiPidinstRecord fromDataCite(DataCiteDoi doi) {
    ApiPidinstRecord result = new ApiPidinstRecord();
    result.setProvider(IdentifierType.PIDINST_DATACITE.name());
    DataCiteDoiAttributes attr =
        doi.getAttributes() == null ? new DataCiteDoiAttributes() : doi.getAttributes();
    String id = StringUtils.trimToNull(StringUtils.defaultIfBlank(doi.getId(), attr.getDoi()));
    result.setPid(id);
    result.setPublicUrl(id == null ? null : "https://doi.org/" + id);
    result.setProviderRecordUrl(id == null ? null : "https://commons.datacite.org/doi.org/" + id);
    result.setState(attr.getState());
    result.setName(
        attr.getTitles() == null || attr.getTitles().isEmpty()
            ? null
            : StringUtils.trimToNull(attr.getTitles().get(0).getTitle()));
    String description = firstDescription(attr, DESCRIPTION_ABSTRACT);
    result.setDescription(
        description != null ? description : firstDescription(attr, DESCRIPTION_TECHNICAL_INFO));
    List<String> owners = new ArrayList<>();
    if (attr.getContributors() != null) {
      for (DataCiteDoiAttributes.Contributor contributor : attr.getContributors()) {
        if (contributor != null
            && HOSTING_INSTITUTION.equalsIgnoreCase(contributor.getContributorType())
            && isNotBlank(contributor.getName())
            && !owners.contains(contributor.getName().trim())) {
          owners.add(contributor.getName().trim());
        }
      }
    }
    if (owners.isEmpty() && isNotBlank(attr.getPublisher())) {
      // the decided fallback for the instrument DOIs registered without a HostingInstitution
      owners.add(attr.getPublisher().trim());
    }
    result.setOwners(owners);
    result.setManufacturers(names(attr.getCreators(), DataCiteDoiAttributes.Creator::getName));
    String resourceType =
        attr.getTypes() == null ? null : StringUtils.trimToNull(attr.getTypes().getResourceType());
    if (resourceType != null && !RESOURCE_TYPE_INSTRUMENT.equalsIgnoreCase(resourceType)) {
      result.getInstrumentTypes().add(resourceType);
    }
    result.setLandingPage(resolvableUrl(attr.getUrl()));
    result.setAlternateIdentifier(
        first(names(attr.getIdentifiers(), DataCiteDoiAttributes.Identifier::getIdentifier)));
    result.setCreated(attr.getCreated() == null ? null : attr.getCreated().toInstant().toString());
    result.setUpdated(attr.getUpdated() == null ? null : attr.getUpdated().toInstant().toString());
    return result;
  }

  /**
   * The creation payload for {@code InstrumentEntityApiManager.createNewApiInstrument}: one field
   * per active template field, in template order (the create path matches by position), filled from
   * the record by the template field's canonical name and type. Fields the record cannot fill are
   * blank; the link fields say nothing about their link, so the template's defaults survive.
   *
   * @throws ApiRuntimeException {@code errors.inventory.identifier.pidinstMandatoryMissing} when
   *     the record has no value for a mandatory template field
   */
  static ApiInstrument toApiInstrument(ApiPidinstRecord record, InstrumentTemplate template) {
    ApiInstrument instrument = new ApiInstrument();
    instrument.setTemplateId(template.getId());
    instrument.setName(
        truncate(
            StringUtils.defaultIfBlank(record.getName(), record.getPid()),
            BaseRecord.DEFAULT_VARCHAR_LENGTH));
    instrument.setDescription(truncate(record.getDescription(), EditInfo.DESCRIPTION_LENGTH));
    for (InventoryEntityField templateField : template.getActiveFields()) {
      String value =
          templateField.getType() == FieldType.LINK
              ? null
              : truncate(valueFor(templateField, record), FIELD_MAX);
      if (templateField.isMandatory()
          && templateField.getType() != FieldType.LINK
          && isBlank(value)) {
        throw new ApiRuntimeException(
            "errors.inventory.identifier.pidinstMandatoryMissing", templateField.getName());
      }
      ApiInventoryEntityField apiField = new ApiInventoryEntityField();
      apiField.setContent(value == null ? "" : value);
      instrument.getFields().add(apiField);
    }
    return instrument;
  }

  /**
   * The identifier that links the new instrument to the PID: the provider's own value and state,
   * both provider URLs, and the origin flag. Flagged as a register request so the existing attach
   * path persists it; RSpace never calls the provider for it.
   */
  static ApiInventoryDOI toLinkedIdentifier(ApiPidinstRecord record, User user) {
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.generatePublicLinkSuffix();
    doi.setRegisterIdentifierRequest(true);
    doi.setLinked(true);
    doi.setDoiType(record.getProvider());
    doi.setDoi(record.getPid());
    doi.setState(record.getState());
    doi.setTitle(truncate(record.getName(), BaseRecord.DEFAULT_VARCHAR_LENGTH));
    doi.setPublicUrl(record.getPublicUrl());
    doi.setProviderUrl(record.getProviderRecordUrl());
    doi.setResourceType(RESOURCE_TYPE_INSTRUMENT);
    doi.setResourceTypeGeneral(RESOURCE_TYPE_INSTRUMENT);
    doi.setCreatorName(user.getFullName());
    doi.setCreatorType("Personal");
    return doi;
  }

  private static String valueFor(InventoryEntityField field, ApiPidinstRecord record) {
    String name = field.getName() == null ? "" : field.getName().trim();
    FieldType type = field.getType();
    if (type == FieldType.STRING) {
      if (PidinstFields.OWNER.equalsIgnoreCase(name)) {
        return String.join(JOIN, record.getOwners());
      }
      if (PidinstFields.MANUFACTURER.equalsIgnoreCase(name)) {
        return String.join(JOIN, record.getManufacturers());
      }
      if (PidinstFields.MODEL.equalsIgnoreCase(name)) {
        return record.getModel();
      }
      if (PidinstFields.INSTRUMENT_TYPE.equalsIgnoreCase(name)) {
        return String.join(JOIN, record.getInstrumentTypes());
      }
      if (PidinstFields.MEASURED_QUANTITY.equalsIgnoreCase(name)) {
        return String.join(JOIN, record.getMeasuredVariables());
      }
      if (PidinstFields.ALTERNATE_IDENTIFIER.equalsIgnoreCase(name)) {
        return record.getAlternateIdentifier();
      }
    } else if (type == FieldType.DATE) {
      if (PidinstFields.COMMISSIONED.equalsIgnoreCase(name)) {
        return record.getCommissioned();
      }
      if (PidinstFields.DECOMMISSIONED.equalsIgnoreCase(name)) {
        return record.getDecommissioned();
      }
    } else if (type == FieldType.URI && PidinstFields.LANDING_PAGE.equalsIgnoreCase(name)) {
      return record.getLandingPage();
    }
    return null;
  }

  private static <T> List<String> names(Collection<T> items, Function<T, String> name) {
    List<String> result = new ArrayList<>();
    if (items == null) {
      return result;
    }
    for (T item : items) {
      if (item == null) {
        continue;
      }
      String value = StringUtils.trimToNull(name.apply(item));
      if (value != null && !result.contains(value)) {
        result.add(value);
      }
    }
    return result;
  }

  private static String first(List<String> values) {
    return values.isEmpty() ? null : values.get(0);
  }

  private static String firstDescription(DataCiteDoiAttributes attr, String type) {
    if (attr.getDescriptions() == null) {
      return null;
    }
    for (DataCiteDoiAttributes.Description description : attr.getDescriptions()) {
      if (description != null
          && type.equalsIgnoreCase(description.getDescriptionType())
          && isNotBlank(description.getDescription())) {
        return description.getDescription().trim();
      }
    }
    return null;
  }

  /**
   * The leading {@code yyyy-MM-dd} of an ISO timestamp, or null: the date field accepts nothing
   * else.
   */
  static String isoDate(String value) {
    if (value == null) {
      return null;
    }
    Matcher matcher = ISO_DATE_PREFIX.matcher(value.trim());
    return matcher.find() ? matcher.group(1) : null;
  }

  /** An absolute http(s) address the URI field will accept, or null (PidinstFields' own rule). */
  static String resolvableUrl(String value) {
    String trimmed = StringUtils.trimToNull(value);
    if (trimmed == null || !PidinstFields.isResolvableAddress(trimmed)) {
      return null;
    }
    try {
      new URI(trimmed);
      return trimmed;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  static String truncate(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + ELLIPSIS;
  }
}
