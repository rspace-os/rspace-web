package com.researchspace.model.views;

import lombok.Getter;
import org.apache.commons.lang3.Validate;

import java.util.Optional;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * For service operations that return a {@link Boolean} indicating
 * success/failure, it can be useful to return an entity back to the controller
 * layer as well.
 * 
 * In some historical cases the entity is String message; since 1.63 there is an
 * additional field for messages that should be used preferentially for
 * consistency. Here is a table of the invariants.
 * 
 * In summary, an object of this class must have a message set if succeeded == false.
 * 
 * <table>
 * <tr><th>Condition</th> <th>1</th><th>2</th><th>3</th><th>4</th><th>5</th><th>6</th><th>7</th><th>8</th> </tr>
 * <tr><td>Succeeded</td> <td>Y</td><td>Y</td><td>Y</td><td>Y</td><td>N</td><td>N</td><td>N</td><td>N</td> </tr>
 * <tr><td>Entity value</td> <td>Y</td><td>Y</td><td>N</td><td>N</td><td>Y</td><td>Y</td><td>N</td><td>N</td> </tr>
 * <tr><td>message set?</td> <td>Y</td><td>N</td><td>Y</td><td>N</td><td>Y</td><td>N</td><td>Y</td><td>N</td> </tr>

 * <tr><th>Actions</th> <th>1</th><th>2</th><th>3</th><th>4</th><th>5</th><th>6</th><th>7</th><th>8</th> </tr>
 * <tr><th>Valid?</th> <th>Y</th><th>Y</th><th>Y</th><th>Y</th><th>Y</th><th>N</th><th>Y</th><th>N</th> </tr>
 * <tr> 
 * </table>
 * 
 * Given that both entity and message are nullable, we should consider redesigning this class or creating
 *  a version2 to avoid breaking the 150+ uses of it
 *
 * @param <T>
 */
@Getter
public class ServiceOperationResult<T> {

	/**
	 * Creates a new ServiceOperationResult with an empty message, unless
	 *  the entity is a java.lang.String, in which case the message will be set to that String.
	 * 
	 * @param entity    the object affected by the service method.
	 * @param succeeded
	 */
	public ServiceOperationResult(T entity, boolean succeeded) {
		super();
		this.entity = entity;
		this.succeeded = succeeded;
		this.message = "";
		if(entity instanceof String) {
			message = (String)entity;
		}
	}
	
	public ServiceOperationResult(T entity, boolean succeeded, String message) {
		super();
		if(!succeeded) {
			Validate.isTrue(!isBlank(message), "Message must be set if  succeeded == false");
		}
		this.entity = entity;
		this.succeeded = succeeded;
		this.message = message;
	}

	/**
	 * the entity the object affected by the service method
	 */
	private T entity;

	/**
	 * whether succeeded or not
	 */
	private boolean succeeded;

	/**
	 * Optional message
	 */
	private String message;

	/**
	 * Static factory method for when a ServiceOperationResult's entity is a string
	 * error message in event of failure.
	 * 
	 * @param optionalErrorMessage
	 * @return
	 */
	public static ServiceOperationResult<String> fromOptionalError(Optional<String> optionalErrorMessage) {
		return optionalErrorMessage.map(errorMsg -> new ServiceOperationResult<>(errorMsg, false))
				.orElse(new ServiceOperationResult<>("", true));
	}
	
	/**
	 * Converts a ServiceOperationResult<?> to ServiceOperationResult<String>. This is kept for backwards
	 *  compatibility where UI is expecting a ServiceOperationResult<String> returned to it.
	 *  <br/>Note:
	 *  <ul>
	 *  <li>The returned object's entity is <code>replacementEntity</code> String
	 *  <li>The returned object's message is kept the same as the original if it  was not empty; else is set to <code>replacementEntity</code>
	 *  </ul>
	 * @param toConvert
	 * @param replacementEntity
	 * @return
	 */
	public static ServiceOperationResult<String> convertToStringEntity(ServiceOperationResult<?> toConvert, String replacementEntity) {
		return new ServiceOperationResult<> (replacementEntity, toConvert.isSucceeded(),
				 isEmpty(toConvert.getMessage())?replacementEntity:toConvert.getMessage());
	}

}
