package com.researchspace.core.util;

/**
 * Mixin interface for anything that can be described in a human-readable format
 */
public interface IDescribable {
	/**
	 * A human-readable description of something.
	 * 
	 * @return a non-null {@link String}
	 */
	String getDescription();

}
