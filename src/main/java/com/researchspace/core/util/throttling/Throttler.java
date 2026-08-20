package com.researchspace.core.util.throttling;

/**
 * Top level interface for throttling.
 */
public interface Throttler    {
	
	/**
	 * Determines whether a request should be allowed to proceed.
	 * <h5>Implementation</h5>
	 *  Default implementation, if this method is not implemented in subclasses,  returns <code>true</code>.
	 * @param identifier
	 *            An identifier for the client making the request
	 * @param requestedResourceUnits
	 * @return <code>true</code> if request should proceed, i.e. is in within
	 *         usage limits
	 * @throws TooManyRequestsException
	 *             if cannot proceed.
	 */
	default boolean proceed(String identifier, Double requestedResourceUnits) {
		return true;
	}
	
	/**
	 * Determines whether a request should be allowed to proceed, using default of 1 requested resource units. <br/>
	 * Equivalent to <code>proceed(String identifier, 1.0)</code>
	 * <h5>Implementation</h5>
	 *  Default implementation, if this method is not implemented in subclasses,  returns <code>true</code>.
	 * @param identifier
	 *            An identifier for the client making the request
	 * @return <code>true</code> if request should proceed, i.e. is in within
	 *         usage limits
	 * @throws TooManyRequestsException
	 *             if cannot proceed.
	 */
	default boolean proceed(String identifier) {
		return true;
	}
	
	/**
	 * The name of the throttler.
	 * @return
	 */
	String getName();
	
	

}
