package com.researchspace.model.dtos;

import com.researchspace.model.field.ErrorList;
import com.researchspace.service.MessageSourceUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

/**
 * Validator that is autowired into Controller classes.<br>
 * For each object to validate, there are 2 methods:
 *
 * <ul>
 *   <li>A basic validator: public BindingResult validate(Object toValidate). This method returns a
 *       {@link BindingResult} which can be used to populate error messages in forms using the
 *       SPring mvc:form namespace
 *   <li>public ErrorList validateAndGetErrorList(Object toValidate) . This method performs the same
 *       validation, but will resolve error messages and populate a custom ErrorList object. This
 *       object can be returned in the body of a JSON response.
 * </ul>
 */
@Service("dtoValidator")
public class DTOControllerValidatorImpl implements IControllerInputValidator {

  private Logger log = LoggerFactory.getLogger(DTOControllerValidatorImpl.class);

  private @Autowired MessageSourceUtils messages;

  @Override
  public <T, V extends Validator> BindingResult validate(T toValidate, V validator) {
    BindingResult result = new BeanPropertyBindingResult(toValidate, "MyObject");
    validate(toValidate, validator, result);
    return result;
  }

  @Override
  public <T, V extends Validator> void validate(T toValidate, V validator, Errors errors) {
    ValidationUtils.invokeValidator(validator, toValidate, errors);
  }

  @Override
  public <T, V extends Validator> ErrorList validateAndGetErrorList(T toValidate, V validator) {
    BindingResult result = validate(toValidate, validator);
    ErrorList eo = new ErrorList();
    populateErrorList(result, eo);
    return eo.hasErrorMessages() ? eo : null;
  }

  @Override
  public ErrorList populateErrorList(BindingResult result, ErrorList eo) {
    if (result.hasErrors()) {
      for (ObjectError err : result.getAllErrors()) {
        String msg = messages.getMessage(err);
        log.warn(msg);
        eo.addErrorMsg(msg);
      }
    }
    return eo;
  }

  @Override
  public <T, V extends Validator> Map<String, String> validateAndGetErrorMessages(
      T toValidate, V validator) {
    BindingResult result = validate(toValidate, validator);
    Map<String, String> msgs = extractErrorMap(result);
    return msgs;
  }

  private Map<String, String> extractErrorMap(BindingResult result) {
    Map<String, String> msgs = new LinkedHashMap<>();
    if (result.hasErrors()) {
      for (ObjectError err : result.getAllErrors()) {
        String msg = messages.getMessage(err);
        log.warn(msg);

        String fieldName = "";
        if (err instanceof FieldError) {
          fieldName = ((FieldError) err).getField();
        }
        msgs.put(fieldName, msg);
      }
    }
    return msgs;
  }
}
