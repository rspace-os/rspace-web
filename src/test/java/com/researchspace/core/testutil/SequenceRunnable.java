package com.researchspace.core.testutil;

import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SequencedRunnable implements Runnable {
	Logger log = LoggerFactory.getLogger(SequencedRunnable.class);
	private final String name;

	private final CountDownLatch[] toWaitFor;
	private final Invokable[] actions;
	final Integer[] sequence;
	 boolean ok = false;
	 Throwable thrown = null;
	

	public SequencedRunnable(String name, CountDownLatch[] conds, Invokable[] actions,
			Integer[] sequence) {
		validateArgs(name, conds, actions, sequence);
		this.toWaitFor = conds;
		this.actions = actions;
		this.name = name;
		this.sequence = sequence;
	}

	private void validateArgs(String name, CountDownLatch[] latches, Invokable[] actions,
			Integer[] sequence) {
		// must be an action for each latch
		if (latches.length != actions.length) {
			throw new IllegalArgumentException("In thread " + name
					+ " there must be the same number of conditions as actions, but was "
					+ latches.length + " and " + actions.length + " respectively");
		}
		for (int index = 0; index < sequence.length; index++) {
			// array indices must be valid
			if (sequence[index] < 0 || sequence[index] >= actions.length) {
				throw new IllegalArgumentException(
						"In thread "
								+ name
								+ ", values  in  sequence [] must be array indices for Conditions []. But ["
								+ index + "] lies outside the range [0," + latches.length + "]");
			}
			if (index >= 1) {
				// can't be consecutive
				if (sequence[index] - sequence[index - 1] == 1) {
					throw new IllegalArgumentException("Thread " + name
							+ " can't have consecutive indices [" + sequence[index - 1] + ","
							+ sequence[index] + "]. Combine into a single action.");
				}
				// sequence of invokables must be ordered.
				if (sequence[index] - sequence[index - 1] < 1) {
					throw new IllegalArgumentException("Thread " + name
							+ " invocation sequence must be an increasing sequence but contains ["
							+ sequence[index - 1] + "," + sequence[index] + "]");
				}
			}
		}
	}

	public void run() {
		try {
      for (int integer : sequence) {
        int toWaitForIndx = integer;
        try {

          log.debug("{}: waiting for event {}", name, toWaitForIndx);
          toWaitFor[toWaitForIndx].await();
        } catch (InterruptedException e) {
          thrown = e;
          Thread.currentThread().interrupt();
        }
        log.debug("{}: invoking action {}", name, toWaitForIndx);
        actions[toWaitForIndx].invoke();
        if (toWaitForIndx < toWaitFor.length - 1) {
          log.debug("{}counting down for next latch {}", name, toWaitForIndx + 1);
          toWaitFor[++toWaitForIndx].countDown();
        } else
          log.debug("{} executed last invokable!", name);

      }
			ok = true;
		} catch (Throwable e) {
			e.printStackTrace();
			thrown = e;
			Thread.currentThread().interrupt();
		
		} 

	}
}
