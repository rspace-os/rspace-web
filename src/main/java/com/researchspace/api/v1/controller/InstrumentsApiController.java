package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InstrumentsApi;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class InstrumentsApiController extends BaseApiInventoryController implements InstrumentsApi {

  @Autowired private InstrumentApiPostValidator sampleApiPostValidator;
  @Autowired private InstrumentApiPutValidator sampleApiPutValidator;
  @Autowired private InstrumentApiPostFullValidator sampleApiPostFullValidator;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiInstrumentFullPost {
    ApiInstrument apiInstrument;
    User user;
    // may be null
    InstrumentTemplate template;
  }

  @Override
  public ApiInstrument createNewInstrument(
      @RequestBody @Valid ApiInstrument apiInstrument,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    assertNotInstrumentTemplate(apiInstrument.isTemplate());
    validateCreateInstrumentInput(apiInstrument, errors, user);

    ApiInstrument result = instrumentApiMgr.createNewApiInstrument(apiInstrument, user);
    buildAndAddInventoryRecordLinks(result);

    return result;
  }

  private void assertNotInstrumentTemplate(boolean sampleTemplateFlag) {
    if (sampleTemplateFlag) {
      throw new IllegalArgumentException(
          "Please use /sampleTemplates endpoint for template actions");
    }
  }

  /* errors might already be populated with simple validation errors using javax.validation annotations
   * by Spring's automatic validation */
  public void validateCreateInstrumentInput(
      ApiInstrument apiInstrument, BindingResult errors, User user) throws BindException {

    // TODO[nik]: implement this on RSDEV-1059, until that, it will be always null
    //    InstrumentTemplate instrumentTemplate = verifyTemplate(apiInstrument, errors, user);
    InstrumentTemplate instrumentTemplate = null;

    // we validate the posted object. We can set errors on individual fields in this validator (
    // doesn't need template)
    inputValidator.validate(apiInstrument, sampleApiPostValidator, errors);

    // here we validate using other information as well as what's posted. errors are 'global' errors
    ApiInstrumentFullPost allData =
        new ApiInstrumentFullPost(apiInstrument, user, instrumentTemplate);

    inputValidator.validate(allData, sampleApiPostFullValidator, errors);
    // this will collate all errors together.
    throwBindExceptionIfErrors(errors);
  }

  @Override
  public ApiInstrument getInstrumentById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {

    ApiInstrument sample = retrieveApiInstrumentIfExists(id, user);
    buildAndAddInventoryRecordLinks(sample);
    return sample;
  }

  private ApiInstrument retrieveApiInstrumentIfExists(Long id, User user) {
    boolean exists = instrumentApiMgr.exists(id);
    if (!exists) {
      throw new NotFoundException(createNotFoundMessage("Inventory record", id));
    }
    return instrumentApiMgr.getApiInstrumentById(id, user);
  }
}
