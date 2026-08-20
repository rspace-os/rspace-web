package com.researchspace.core.testutil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Given a set of Invokable actions and a sequence in which to run them, will run sequentially
 * across multiple threads, based on
 * http://stackoverflow.com/questions/6574218/how-can-i-get-a-specific
 * -thread-to-be-the-next-one-to-enter-a-synchronized-block.
 * But, this method uses sequential CountDown latches to ensure only 1 thread operates at a time.
 */
public class SequencedRunnableRunner {
	private Map<String, Integer[]> nameToSequence;
	private final Invokable[] actions;
	 boolean completedOK = true;
	 Throwable internalException = null;

	/**
	 * 
	 * @param nameToSequence
	 *            A Map of keys ( thread names) and values ( the sequence of actions to be invoked)
	 * @param actions
	 *            An array of {@link Invokable} actions.
	 */
	public SequencedRunnableRunner(Map<String, Integer[]> nameToSequence, Invokable[] actions) {
		this.nameToSequence = nameToSequence;
		this.actions = actions;
	}

	public boolean isCompletedOK() {
		return completedOK;
	}

	public void setCompletedOK(boolean completedOK) {
		this.completedOK = completedOK;
	}

	public Throwable getInternalException() {
		return internalException;
	}

	public void setInternalException(Throwable internalException) {
		this.internalException = internalException;
	}

	public void runSequence() throws InterruptedException {
		// Lock l = new ReentrantLock(true);
		CountDownLatch[] conditions = new CountDownLatch[actions.length];
		for (int i = 0; i < actions.length; i++) {
			// each latch will be counted down by the action of its predecessor
			conditions[i] = new CountDownLatch(1);
		}

		Thread[] threads = new Thread[nameToSequence.size()];
		List<SequencedRunnable> sequencedRunnables = new ArrayList<>();
		int i = 0;
		for (String name : nameToSequence.keySet()) {
			
			SequencedRunnable seqRun = new SequencedRunnable(name, conditions, actions,
					nameToSequence.get(name));
			sequencedRunnables.add(seqRun);
			threads[i] = new Thread(seqRun);
			i++;
		}

		for (Thread t : threads) {
			t.start();
		}
		// tell the thread waiting for the first latch to wake up.
		conditions[0].countDown();

		// wait for all threads to finish before leaving the test
		for (Thread t : threads) {
			t.join(5000); // max 10s wait in case of error.
		}
		
		// check to see if any runnables failed.
		for (SequencedRunnable runnable: sequencedRunnables) {
			if(!runnable.ok){
				completedOK = false;
			}
			if(runnable.thrown != null) {
				internalException = runnable.thrown;
			}
		}
		
	}
}
