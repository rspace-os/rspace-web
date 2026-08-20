package com.researchspace.model.field;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Holds validation errors to return to client/browser in a Spring
 * MVC-independent manner.
 */
public class ErrorList implements Serializable {
	private static final long serialVersionUID = 1658698239631269737L;
	private final List<String> errorMessages = new ArrayList<>();

	/**
	 * Create an ErrorList of error messages
	 */
	public static ErrorList of(String... messages) {
		ErrorList el = new ErrorList();

		for (String message : messages) {
			el.addErrorMsg(message);
		}

		return el;
	}

	/**
	 * Convenience constructor for creating an ErrorList that is to hold only a
	 * single element.
	 *
	 * @return A new {@link ErrorList} object populated with the message.
	 * @deprecated replaced by a more concise version - {@link #of(String...)}}
	 */
	@Deprecated
	public static ErrorList createErrListWithSingleMsg(String msg) {
		ErrorList el = new ErrorList();
		el.addErrorMsg(msg);
		return el;
	}

	public void addErrorMsg(String msg) {
		errorMessages.add(msg);
	}

	/**
	 * Gets a possibly-empty but non-null unmodifiable List of error objects
	 */
	public List<String> getErrorMessages() {
		return errorMessages;
	}

	/**
	 * Gets a possibly-empty but non-null unmodifiable List of error objects
	 */
	public String getAllErrorMessagesAsStringsSeparatedBy(String delimiter) {
		return StringUtils.join(errorMessages, delimiter);
	}

	public String toString() {
		return errorMessages.toString() + " has " + errorMessages.size() + " messages.";
	}

	/**
	 * Boolean test for existence of error messages
	 */
	public boolean hasErrorMessages() {
		return errorMessages.size() > 0;
	}

	/**
	 * Merges the argument's messages with this object. The argument is
	 * unaltered.
	 *
	 * @param el An {@link ErrorList} to merge with this object's error
	 *           messages.
	 */
	public void addErrorList(final ErrorList el) {
		for (String message : el.getErrorMessages()) {
			addErrorMsg(message);
		}
	}
}
