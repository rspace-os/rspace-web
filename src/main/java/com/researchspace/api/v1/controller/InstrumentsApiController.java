package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InstrumentsApi;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class InstrumentsApiController extends BaseApiInventoryController implements InstrumentsApi {

  @Autowired private InstrumentApiPostValidator instrumentApiPostValidator;
  @Autowired private InstrumentApiPostFullValidator instrumentApiPostFullValidator;

  @Value("${inventory.instrument.enabled:false}")
  private boolean inventoryInstrumentEnabled;

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
    assertIsInventoryInstrumentEnabled();
    validateCreateInstrumentInput(apiInstrument, errors, user);

    if (apiInstrument.getNewTargetLocation() != null) {
      ApiContainerInfo parentContainer = new ApiContainerInfo();
      parentContainer.setId(apiInstrument.getNewTargetLocation().getContainerId());
      apiInstrument.setParentContainer(parentContainer);
      apiInstrument.setParentLocation(apiInstrument.getNewTargetLocation().getContainerLocation());
    }

    ApiInstrument result = instrumentApiMgr.createNewApiInstrument(apiInstrument, user);
    buildAndAddInventoryRecordLinks(result);

    return result;
  }

  @Override
  public ApiInstrument getInstrumentById(
      @PathVariable Long id, @RequestAttribute(name = "user") User user) {
    assertIsInventoryInstrumentEnabled();

    ApiInstrument instrument = retrieveApiInstrumentIfExists(id, user);
    buildAndAddInventoryRecordLinks(instrument);
    return instrument;
  }

  private void assertIsInventoryInstrumentEnabled() {
    if (!inventoryInstrumentEnabled) {
      throw new UnsupportedOperationException(
          "The inventory Instrument is not enabled in this RSpace instance");
    }
  }

  /* errors might already be populated with simple validation errors using javax.validation annotations
   * by Spring's automatic validation */
  public void validateCreateInstrumentInput(
      ApiInstrument apiInstrument, BindingResult errors, User user) throws BindException {

    // TODO[nik]: implement this on RSDEV-1059, until that, it will be always null
    // reinstate this: InstrumentTemplate instrumentTemplate =
    //                                            verifyTemplate(apiInstrument, errors, user);
    InstrumentTemplate instrumentTemplate = null;

    // we validate the posted object. We can set errors on individual fields in this validator (
    // doesn't need template)
    inputValidator.validate(apiInstrument, instrumentApiPostValidator, errors);

    // here we validate using other information as well as what's posted. errors are 'global' errors
    ApiInstrumentFullPost allData =
        new ApiInstrumentFullPost(apiInstrument, user, instrumentTemplate);

    inputValidator.validate(allData, instrumentApiPostFullValidator, errors);
    // this will collate all errors together.
    throwBindExceptionIfErrors(errors);
  }

  private ApiInstrument retrieveApiInstrumentIfExists(Long id, User user) {
    boolean exists = instrumentApiMgr.instrumentExists(id);
    if (!exists) {
      throw new NotFoundException(createNotFoundMessage("Inventory Instrument ", id));
    }
    return instrumentApiMgr.getApiInstrumentById(id, user);
  }
}
