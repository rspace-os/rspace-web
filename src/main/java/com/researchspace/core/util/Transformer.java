package com.researchspace.core.util;

/**
 * Interface for TransformerUtils to map one object to another
 */
public interface Transformer<U, T> {

	U transform(T toTransform);

}
