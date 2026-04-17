package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentEntity;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.dao.InstrumentEntityDao;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.service.inventory.InstrumentApiManager;
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

  private @Autowired InstrumentEntityDao<Instrument> instrumentDao;
  private @Autowired InstrumentEntityDao<InstrumentTemplate> instrumentTemplateDao;

  @Override
  public boolean instrumentExists(long id) {
    return instrumentDao.exists(id);
  }

  @Override
  public boolean instrumentTemplateExists(long id) {
    return instrumentTemplateDao.exists(id);
  }

  @Override
  public ApiInstrument createNewApiInstrument(ApiInstrument apiInstrument, User user) {

    InstrumentEntity instrumentTemplate = getInstrumentTemplateIfPresentOnRequest(apiInstrument);

    String instrumentName = getNameForIncomingApiInstrument(apiInstrument);
    return createInstrument(instrumentName, apiInstrument, instrumentTemplate, user);
  }

  private InstrumentEntity getInstrumentTemplateIfPresentOnRequest(ApiInstrument apiInstrument) {
    InstrumentEntity template = null;
    Long templateId = apiInstrument.getTemplateId();
    // if templateId is null (we're creating a new instrument),
    // but if not null, we expect it to exist
    if (templateId != null) {
      template = instrumentTemplateDao.get(templateId);
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
    Instrument instrumentToSave =
        recordFactory.createInstrument(instrumentName, user, instrTemplate);

    setBasicFieldsFromNewIncomingApiInventoryRecord(instrumentToSave, apiInstrument, user);
    if (instrTemplate != null) {
      // might be null from incoming API request, but here we want to reference template icon id
      instrumentToSave.setIconId(instrTemplate.getIconId());
    }
    if (!apiInstrument.getFields().isEmpty()) {
      saveNewApiFieldsIntoInstrumentFields(
          apiInstrument.getFields(), instrumentToSave.getActiveFields(), user);
    } else {
      assertDefaultFieldsValid(instrumentToSave.getActiveFields());
    }

    Instrument savedInstrument = instrumentDao.save(instrumentToSave);
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
    return getInstrumentById(id, user);
  }

  @Override
  public ApiInstrumentEntity getApiInstrumentTemplateById(Long id, User user) {
    return getInstrumentTemplateById(id, user);
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

  private ApiInstrument getInstrumentById(Long id, User user) {
    InstrumentEntity instrument = instrumentDao.get(id);
    publisher.publishEvent(new InventoryAccessEvent(instrument, user));
    return getOutgoingApiInstrument(instrument, user);
  }

  private ApiInstrumentEntity getInstrumentTemplateById(Long id, User user) {
    InstrumentEntity instrumentTemplate = instrumentTemplateDao.get(id);
    publisher.publishEvent(new InventoryAccessEvent(instrumentTemplate, user));
    return getOutgoingApiInstrument(instrumentTemplate, user);
  }

  private ApiInstrument getOutgoingApiInstrument(InstrumentEntity instrumentEntity, User user) {
    ApiInstrument result = new ApiInstrument(instrumentEntity);
    populateOutgoingApiInstrument(result, instrumentEntity, user);
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
   * @returns true if any i mages were saved
   */
  private boolean saveIncomingInstrumentImage(
      InstrumentEntity dbInstrument, ApiInstrumentEntity apiInstrument, User user) {
    return saveIncomingImage(
        dbInstrument,
        apiInstrument,
        user,
        Instrument.class,
        instrument -> instrumentDao.save(instrument));
  }

  @Override
  public InstrumentEntity getIfExists(Long id) {
    if (instrumentExists(id)) {
      return instrumentDao.get(id);
    } else if (instrumentTemplateExists(id)) {
      return instrumentTemplateDao.get(id);
    }
    throw new NotFoundException("No Instrument or InstrumentTemplate found with id: " + id);
  }
}
