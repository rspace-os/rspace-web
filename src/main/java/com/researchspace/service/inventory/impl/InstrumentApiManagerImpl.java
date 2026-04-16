package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiFieldToModelFieldFactory;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentEntity;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.dao.InstrumentEntityDao;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.service.inventory.InstrumentApiManager;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import java.io.IOException;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InstrumentApiManagerImpl extends InventoryApiManagerImpl<InstrumentEntity>
    implements InstrumentApiManager {

  public static final String INSTRUMENT_DEFAULT_NAME = "Generic Instrument";

  private @Autowired InstrumentEntityDao instrumentEntityDao;
  private @Autowired InventoryAuditApiManager inventoryAuditMgr;
  private @Autowired ApiFieldToModelFieldFactory apiFieldToModelFieldFactory;

  @Override
  public boolean exists(long id) {
    return instrumentEntityDao.exists(id);
  }

  @Override
  public ApiInstrument createNewApiInstrument(ApiInstrument apiInstrument, User user) {

    InstrumentEntity instrumentTemplate = getInstrumentTemplateIfExists(apiInstrument);

    String instrumentName = getNameForIncomingApiInstrument(apiInstrument);
    return createInstrument(instrumentName, apiInstrument, instrumentTemplate, user);
  }

  private InstrumentEntity getInstrumentTemplateIfExists(ApiInstrument apiInstrument) {
    InstrumentEntity template = null;
    Long templateId = apiInstrument.getTemplateId();
    // if templateId is null(we're creating a new instrument), that's ok, but if not null, we expect
    // it
    // to exist
    if (templateId != null) {
      template = instrumentEntityDao.getInstrumentTemplate(templateId);
    }
    return template;
  }

  private String getNameForIncomingApiInstrument(ApiInstrumentEntity instrumentRequest) {
    return StringUtils.isNotBlank(instrumentRequest.getName())
        ? instrumentRequest.getName()
        : INSTRUMENT_DEFAULT_NAME;
  }

  private ApiInstrument createInstrument(
      String instrumentName,
      ApiInstrument apiInstrument,
      InstrumentEntity instrTemplate,
      User user) {
    Instrument instrument = recordFactory.createInstrument(instrumentName, user, instrTemplate);

    setBasicFieldsFromNewIncomingApiInventoryRecord(instrument, apiInstrument, user);
    if (instrTemplate != null) {
      // might be null from incoming API request, but here we want to reference template icon id
      instrument.setIconId(instrTemplate.getIconId());
    }
    if (!apiInstrument.getFields().isEmpty()) {
      saveNewApiFieldsIntoInstrumentFields(
          apiInstrument.getFields(), instrument.getActiveFields(), user);
    } else {
      assertDefaultFieldsValid(instrument.getActiveFields());
    }

    Instrument savedInstrument = instrumentEntityDao.persistNewInstrument(instrument);
    saveIncomingInstrumentImage(savedInstrument, apiInstrument, user);

    publisher.publishEvent(new InventoryCreationEvent(savedInstrument, user));

    ApiInstrument apiResultInstrument = new ApiInstrument(savedInstrument);
    if (instrTemplate != null) {
      apiResultInstrument.setTemplateId(instrTemplate.getId());
    }
    populateOutgoingApiInstrument(apiResultInstrument, savedInstrument, user);

    return apiResultInstrument;
  }

  private void assertDefaultFieldsValid(List<InventoryEntityField> activeFields) {
    for (InventoryEntityField field : activeFields) {
      field.assertFieldDataValid(field.getFieldData());
    }
  }

  private void saveNewApiFieldsIntoInstrumentFields(
      List<ApiInventoryEntityField> apiFieldList,
      List<InventoryEntityField> inventoryEntityFieldList,
      User user) {

    if (apiFieldList.size() != inventoryEntityFieldList.size()) {
      throw new IllegalArgumentException(
          String.format(
              "Number of incoming instrument fields [%d]"
                  + " doesn't match number of template fields [%d]",
              apiFieldList.size(), inventoryEntityFieldList.size()));
    }

    for (int i = 0; i < apiFieldList.size(); i++) {
      ApiInventoryEntityField apiField = apiFieldList.get(i);
      String newFieldContent = apiField.getContent();
      InventoryEntityField inventoryEntityField = inventoryEntityFieldList.get(i);

      if (inventoryEntityField.isOptionsStoringField()) {
        inventoryEntityField.setSelectedOptions(apiField.getSelectedOptions());
      } else {
        inventoryEntityField.setFieldData(newFieldContent);
      }
    }
  }

  @Override
  public ApiInstrument getApiInstrumentById(Long id, User user) {
    return doGetInstrument(id, user, false);
  }

  @Override
  public ApiInstrument getApiInstrumentTemplateById(Long id, User user) {
    return doGetInstrument(id, user, true);
  }

  @Override
  public Instrument assertUserCanEditInstrument(Long dbId, User user) {
    // TODO[nik]: implement this on RSDEV-1059
    return null;
  }

  @Override
  public Instrument assertUserCanReadInstrument(Long dbId, User user) {
    // TODO[nik]: implement this on RSDEV-1059
    return null;
  }

  private ApiInstrument doGetInstrument(Long id, User user, boolean asTemplate) {
    Instrument instrument = (Instrument) getIfExists(id);
    if (asTemplate != instrument.isTemplate()) {
      throw new IllegalArgumentException(
          String.format("Instrument template flag doesn't match the request (id %d)", id));
    }
    publisher.publishEvent(new InventoryAccessEvent(instrument, user));
    return getOutgoingApiInstrument(instrument, user);
  }

  private ApiInstrument getOutgoingApiInstrument(Instrument instrument, User user) {
    ApiInstrument result = new ApiInstrument(instrument);
    populateOutgoingApiInstrument(result, instrument, user);
    return result;
  }

  private void populateOutgoingApiInstrument(
      ApiInstrument apiInstrument, InstrumentEntity instrument, User user) {
    if (apiInstrument != null) { // populate only if it is already created
      setOtherFieldsForOutgoingApiInventoryRecord(apiInstrument, instrument, user);
      populateSharingPermissions(apiInstrument.getSharedWith(), instrument);
    }
  }

  /**
   * Save incoming instrument image.
   *
   * @throws IOException
   * @returns true if any images were saved
   */
  private boolean saveIncomingInstrumentImage(
      InstrumentEntity dbInstrument, ApiInstrumentEntity apiInstrument, User user) {
    return saveIncomingImage(
        dbInstrument,
        apiInstrument,
        user,
        InstrumentEntity.class,
        instrument -> instrumentEntityDao.save(instrument));
  }

  @Override
  public InstrumentEntity getIfExists(Long id) {
    return getIfExists(id, false);
  }

  private InstrumentEntity getIfExists(Long id, boolean onlyIfTemplate) {
    boolean exists = instrumentEntityDao.exists(id);
    if (!exists) {
      throw new NotFoundException(
          "No instrument " + (onlyIfTemplate ? "template " : "") + "with id: " + id);
    }
    InstrumentEntity instrumentEntity = instrumentEntityDao.get(id);
    if (onlyIfTemplate && !instrumentEntity.isTemplate()) {
      throw new NotFoundException("No instrument template with id: " + id);
    }
    return instrumentEntity;
  }
}
