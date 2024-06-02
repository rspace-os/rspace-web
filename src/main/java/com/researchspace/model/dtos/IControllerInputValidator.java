package com.researchspace.model.dtos;

import com.researchspace.model.field.ErrorList;
import java.util.Map;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Encapsulates validation of objects passed in from the client in a generic manner */
public interface IControllerInputValidator {

  /**
   * Validates an object and returns a {@link BindingResult}.
   *
   * @param toValidate
   * @return A {@link BindingResult}. If the object under test is valid, the {@link BindingResult}
   *     will contain no errors.
   */
  <T, V extends Validator> BindingResult validate(T toValidate, V validator);

  /**
   * @param toValidate
   * @return An {@link ErrorList} or <code>null</code> if the object was valid.
   */
  <T, V extends Validator> ErrorList validateAndGetErrorList(T toValidate, V validator);

  /**
   * @return a Map of problematic field names and their error messages, or empty Map if the object
   *     under test is valid.
   */
  <T, V extends Validator> Map<String, String> validateAndGetErrorMessages(
      T toValidate, V validator);

  /**
   * Validate using an existing Errors object
   *
   * @param toValidate
   * @param validator
   * @param errors
   */
  <T, V extends Validator> void validate(T toValidate, V validator, Errors errors);

  /**
   * Given an already populated {@link BindingResult}, will add to an {@link ErrorList}
   *
   * @param result
   * @param eo
   * @return the set error list with added errors.
   */
  ErrorList populateErrorList(BindingResult result, ErrorList eo);
}
