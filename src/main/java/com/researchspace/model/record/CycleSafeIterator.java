package com.researchspace.model.record;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.Stack;

/**
 * Iterator for navigating upwards through parent folders. If hasNext() returns
 * <code>false</code>, this could be due to 2 reasons:
 * <ul>
 * <li>The iteration genuinely has no more elements, and there were no cycles
 * <li>Or, a cycle was detected in the parent graph.
 * </ul>
 * To distinguish between the two, call the isCycleDetected() method.<br/>
 * 
 * This iterator cannot be reused; create a new iterator to iterate over a new
 * BaseRecord.
 */
class CycleSafeIterator implements Iterator<Folder> {

	// the record we iterating from
	private BaseRecord start;

	// contains previously returned records
	private Set<BaseRecord> seen = new HashSet<>();

	// next items in dfs
	private Stack<Folder> dfsToSearch = new Stack<>();

	// used to detect cycles - keeps track of current path in DFS
	private Map<BaseRecord, Set<BaseRecord>> predecessors = new HashMap<>();

	private boolean isStarted = false;

	CycleSafeIterator(BaseRecord start) {
		super();
		this.start = start;

	}

	private boolean init() {
		// only do init once
		if (!isStarted) {
			addParentsToStack(this.start);
			if (isCycleDetected()) {
				return false;
			} else {
				isStarted = true;
				return true;
			}
		}
		return true;
	}

	private boolean cycleDetected = false;

	public boolean isCycleDetected() {
		return cycleDetected;
	}

	@Override
	public boolean hasNext() {

		if (!init()) {
			return false;
		}
		// return false if no elements
		if (dfsToSearch.isEmpty()) {
			return false;
		}
		boolean foundNext = false;
		// this searches for a next node that has not been found before
		while (!foundNext && !dfsToSearch.isEmpty()) {
			this.next = _getNext();
			if (this.next == null) {
				return false;
			}
			if (!seen.contains(next)) {
				foundNext = true;
				seen.add(next);
			}
		}
		return foundNext;

	}

	void addParentsToStack(BaseRecord rec) {

		for (Folder parent : rec.getParentFolders()) {
			if (parentInPredecessors(rec, parent)) {
				this.cycleDetected = true;
				return;

			}
			if (predecessors.get(parent) != null) {
				predecessors.get(parent).add(rec);
			} else {
				Set<BaseRecord> set = new HashSet<>();
				set.add(rec);
				predecessors.put(parent, set);
			}

			dfsToSearch.push(parent);

		}

	}

	private boolean parentInPredecessors(BaseRecord rec, BaseRecord parent) {
		Stack<BaseRecord> predccsrs = new Stack<>();
		if (predecessors.get(rec) != null) {
			for (BaseRecord pdc : predecessors.get(rec)) {
				if (pdc != null) {
					predccsrs.push(pdc);
				}
			}
		}
		while (!predccsrs.isEmpty()) {
			BaseRecord pdc = predccsrs.pop();
			if (pdc.equals(parent)) {
				return true;
			} else {
				if (predecessors.get(pdc) != null) {
					for (BaseRecord pdc2 : predecessors.get(pdc)) {
						if (pdc2 != null) {
							predccsrs.push(pdc2);
						}
					}
				}
			}
		}
		return false;

	}

	private Folder next;

	Folder _getNext() {
		if (!init()) {
			return null;
		}
		if (dfsToSearch.isEmpty()) {
			throw new NoSuchElementException("There are no elements to iterate!");
		}
		Folder rc = dfsToSearch.pop();
		if (seen.contains(rc)) {
			return rc;
		}
		addParentsToStack(rc);
		if (!isCycleDetected()) {
			return rc;
		} else {
			return null;
		}
	}

	@Override
	public Folder next() {
		if (next == null) {
			throw new NoSuchElementException("There are no elements to iterate!");
		}
		// return next Folder and reset internal next
		Folder rc = next;
		next = null;
		return rc;

	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}