package com.researchspace.webapp.controller;

import com.researchspace.model.field.ErrorList;
import com.researchspace.model.views.ServiceOperationResult;
import java.util.function.Function;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;

/**
 * A return object for Ajax calls. This object contains the data to be serialized and an optional
 * error code. Either can be <code>null</code>, but not both.
 *
 * @param <T> The payload of the response.
 */
@NoArgsConstructor
public class AjaxReturnObject<T> {

  private T data;

  private ErrorList error;

  /**
   * @param data - the payload of the response. Can be <code>null</code> if the return object could
   *     not be generated or there is no data to return.
   * @param errorMsg - An optional error message if the data could not be returned. Can be <code>
   *     null</code> if data is not null.
   */
  public AjaxReturnObject(T data, ErrorList errorMsg) {
    this.data = data;
    this.error = errorMsg;
  }

  public AjaxReturnObject(T data) {
    this(data, null);
  }

  public AjaxReturnObject(ErrorList errors) {
    this(null, errors);
  }

  public T getData() {
    return data;
  }

  public ErrorList getErrorMsg() {
    return error;
  }

  public ErrorList getError() {
    return error;
  }

  /**
   * Returns true if the AjaxReturnObject has data
   *
   * @return
   */
  public boolean isSuccess() {
    return data != null;
  }

  /**
   * Applies a function to the data contained in the AjaxReturnObject if it isn't null
   *
   * @param func function to apply to the data
   * @param <K> type of the new data
   * @return a new AjaxReturnObject that will have the original error message.
   */
  public <K> AjaxReturnObject<K> mapData(Function<T, K> func) {
    if (isSuccess()) return new AjaxReturnObject<>(func.apply(data), error);
    else return new AjaxReturnObject<>(null, error);
  }

  /**
   * Replaces original data but keeps the error if present
   *
   * @param newData new data for the AjaxReturnObject
   * @param <K> type of the new data
   * @return a new AjaxReturnObject that will have the original error message and new data
   */
  public <K> AjaxReturnObject<K> setData(K newData) {
    return mapData(oldData -> newData);
  }

  public static <K> AjaxReturnObject<K> fromSOR(ServiceOperationResult<K> result) {
    return new AjaxReturnObject<>(
        result.getEntity(), ErrorList.createErrListWithSingleMsg(result.getMessage()));
  }

  /**
   * Maps a ServiceOperationResult to a new type to be returned to the browser. <br>
   * Adds error message to the return value only if one is set. <br>
   * The returned object data value will be set if all these conditions are true:
   *
   * <ul>
   *   <li>The ServiceOperationResult was successful
   *   <li>The ServiceOperationResult entity is not null
   * </ul>
   *
   * The errorMessage will be non-null if the ServiceOperationResult contained a message.
   *
   * @param result A ServiceOperationResult
   * @param mapper A function to convert types
   * @return
   */
  public static <T, R> AjaxReturnObject<R> fromSOR(
      ServiceOperationResult<T> result, Function<T, R> mapper) {
    R aroResult = null;
    ErrorList errors = null;
    if (result.isSucceeded() && result.getEntity() != null) {
      aroResult = mapper.apply(result.getEntity());
    }
    if (!StringUtils.isBlank(result.getMessage())) {
      errors = ErrorList.createErrListWithSingleMsg(result.getMessage());
    }
    return new AjaxReturnObject<>(aroResult, errors);
  }
}
