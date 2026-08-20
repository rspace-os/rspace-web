package com.researchspace.core.util.progress;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang3.Validate;

public class ProgressMonitorImpl implements ProgressMonitor, Serializable {

	static final int DEFAULT_WORK_UNIT_COUNT = 100;
	/**
	 * 
	 */
	private static final long serialVersionUID = -5845953493763812553L;
	private final int totalWorkUnits;
	private AtomicInteger ticksCompleted = new AtomicInteger(0);
	private String description;
	private boolean cancelRequested;
	private boolean done;
	private double percentComplete;

	/**
	 * 
	 * @param totalWorkUnits
	 *            must be &gt; 0
	 * @param description
	 * @throws IllegalArgumentException
	 *             if <code>totalWorkUnits</code> &lt; 0
	 */
	public ProgressMonitorImpl(int totalWorkUnits, String description) {
		super();
		Validate.isTrue(totalWorkUnits > 0);
		this.totalWorkUnits = totalWorkUnits;
		this.description = description;
	}

	/**
	 * Default constructor for frameworks; clients should not use this
	 * constructor
	 */
	public ProgressMonitorImpl() {
		this.totalWorkUnits = DEFAULT_WORK_UNIT_COUNT;
	}

	@Override
	public ProgressMonitor worked(int workUnits) {
		Validate.isTrue(workUnits > 0);
		if (!isDone()) {
			int sum = ticksCompleted.addAndGet(workUnits);
			if (sum > totalWorkUnits) {
				ticksCompleted.set(totalWorkUnits);
			}
		}
		this.percentComplete = calculatePercentComplete();
		return this;
	}

	private double calculatePercentComplete() {
		// this is safe as totalWorkUnits can never be 0
		return ((double) ticksCompleted.get() / (double) totalWorkUnits) * 100;
	}

	@Override
	public double getPercentComplete() {
		return percentComplete;
	}

	@Override
	public boolean isCancelRequested() {
		return cancelRequested;
	}

	@Override
	public boolean isDone() {
		return this.done || getPercentComplete() >= 100;
	}

	@Override
	public ProgressMonitor done() {
		this.done = true;
		return this;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public ProgressMonitor setDescription(String description) {
		this.description = description;
		return this;
	}

	@Override
	public int getTotalWorkUnits() {
		return totalWorkUnits;
	}

	@Override
	public ProgressMonitor requestCancel() {
		this.cancelRequested = true;
		return this;
	}

}
