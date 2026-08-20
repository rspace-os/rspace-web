package com.researchspace.core.util.progress;

/**
 * Callback to terminate jobs run under progress monitor.
 */
public interface ProgressTerminator {

	/**
	 * 
	 * @return <code>true</code> if operation was cancelled, <code>false</code>
	 *         otherwise.
	 */
	boolean cancel();

	/**
	 * Convenience class for testing etc that always returns <code>false</code>
	 */
	ProgressTerminator NULL_TERMINATOR = ()->false;
		

	/**
	 * Convenience class for testing etc that always returns <code>true</code>
	 */
	ProgressTerminator TRUE_TERMINATOR = ()->true;

}
