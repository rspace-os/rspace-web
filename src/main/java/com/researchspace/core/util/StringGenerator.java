package com.researchspace.core.util;

/**
 * Interface for String-generator methods for generating multiple String
 * representations of an object.
 */
public interface StringGenerator<T> {

	String getString(T object);

}
