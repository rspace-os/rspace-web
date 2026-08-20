package com.researchspace.core.util.progress;

/**
 * Basic abstraction over a progress monitor that can be used to pass progress
 * information between server and client.
 */
public interface ProgressMonitor {

	/**
	 * Increment the the progress monitor by a given number of ticks. This will
	 * have no effect if any of the following are true:
	 * <ul>
	 * <li>The monitor is already done - i.e. <code>isDone() == true</code>
	 * </ul>
	 * 
	 * @param workUnits
	 *            A positive integer
	 * @return this for method chaining
	 * @throws IllegalArgumentException
	 *             if <code>ticks</code> &lt; 0
	 */
	ProgressMonitor worked(int workUnits);

	/**
	 * Get the percent completeness of the job. Will never return &gt; 100%
	 * 
	 * @return double value where 0 &lt;= value &lt;=100
	 */
	double getPercentComplete();

	/**
	 * Getter for total work units dedicated to this {@link ProgressMonitor}.
	 * 
	 * @return the total number of work units.
	 */
	int getTotalWorkUnits();

	/**
	 * Request cancel of the operation. Implementations that don't support this
	 * can throw an {@link UnsupportedOperationException}
	 * 
	 * @return <code>this</code> for method chaining.
	 */
	ProgressMonitor requestCancel();

	/**
	 * Progress monitor is complete (100% finished).
	 * 
	 * @return
	 */
	boolean isDone();

	/**
	 * Optional description of the state underlying process.
	 * 
	 * @return
	 */
	String getDescription();

	/**
	 * Setter for description of current progress state.
	 * 
	 * @param description
	 */
	ProgressMonitor setDescription(String description);

	/**
	 * Sets progress monitor state as terminated.
	 * 
	 * @return <code>this</code> for method chaining.
	 */
	ProgressMonitor done();

	/**
	 * Boolean query as to whether the task cancellation has been requested.
	 * 
	 * @return
	 */
	boolean isCancelRequested();

	/**
	 * Noop progress monitor for testing, avoidance of null checks etc.
	 */
	ProgressMonitor NULL_MONITOR = new ProgressMonitor() {

		@Override
		public ProgressMonitor worked(int ticks) {
			return this;
		}

		@Override
		public double getPercentComplete() {
			return 0;
		}

		@Override
		public ProgressMonitor requestCancel() {
			return this;
		}

		@Override
		public boolean isDone() {
			return false;
		}

		@Override
		public String getDescription() {
			return "";
		}

		@Override
		public ProgressMonitor setDescription(String description) {
			return this;
		}

		@Override
		public boolean isCancelRequested() {
			return false;
		}

		@Override
		public int getTotalWorkUnits() {
			return 0;
		}

		@Override
		public ProgressMonitor done() {
			return this;
		}

	};
}
