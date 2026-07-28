package com.researchspace.model.record;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.researchspace.core.util.RSCollectionUtils;
import com.researchspace.model.core.RecordType;

/**
 * Represents a folder path in RSpace.<br/>
 * Path order is from parent down to child. So getLastElement () will return the
 * furthest descendant from the parent( which is getFirstElement).
 * <p/>
 * Only the last element can be a non-Folder, all other items must be folders.
 */
public class RSPath implements Iterable<BaseRecord> {

	private List<BaseRecord> pathElements = new ArrayList<>();

	/**
	 * 
	 * @param pathElementsIn
	 *            A List of records in a parent/child hierarchy
	 * @throws if
	 *             path elements are not in parent/child relations
	 */
	public RSPath(List<BaseRecord> pathElementsIn) {
		super();
		if (pathElementsIn != null) {
			this.pathElements = pathElementsIn;
		}
		if (pathElements.size() > 1) {
			for (int i = pathElements.size() - 1; i > 0; i--) {
				if (!pathElements.get(i).getParentFolders().contains(pathElements.get(i - 1))) {
					throw new IllegalArgumentException(pathElements.get(i).getName()
							+ " not in folder tree: current elements are : " + StringUtils.join(pathElements, ","));
				}
			}
		}
	}

	/**
	 * Gets the last element of the path, or Optional.empty  if it is empty.
	 * 
	 * @return
	 */
	public Optional<BaseRecord> getLastElement() {
		BaseRecord  rc = null;
		if (!pathElements.isEmpty()) {
			rc =  pathElements.get(pathElements.size() - 1);
		}
		return Optional.ofNullable(rc);
	}

	/**
	 * Gets the number of elements in the path.
	 * 
	 * @return
	 */
	public int size() {
		return pathElements.size();
	}

	/**
	 * Gets immediate parent of the argument br, so long as br is in the path
	 * and is not the 1st element in the path. Otherwise, returns
	 * <code>Optional.empty</code>.
	 * 
	 * @param br
	 * @return
	 */
	public Optional<Folder> getImmediateParentOf(BaseRecord br) {
		int index = pathElements.indexOf(br);
		Folder  rc = null;
		if (index > 0) { // if index is 0, doesn't have a parent
			rc = (Folder) pathElements.get(index - 1);
		}
		return Optional.ofNullable(rc);
	}

	/**
	 * Gets the first element in the path, or <code>Optional.empty</code> if path is
	 * empty.
	 * 
	 * @return
	 */
	public Optional<BaseRecord> getFirstElement() {
		BaseRecord  rc = null;
		if (!pathElements.isEmpty()) {
			rc =  pathElements.get(0);
		}
		return Optional.ofNullable(rc);
	}

	/**
	 * Boolean test for whether the supplied record is in this path.
	 * 
	 * @param br
	 * @return
	 */
	public boolean contains(BaseRecord br) {
		return pathElements.contains(br);
	}

	@Override
	public Iterator<BaseRecord> iterator() {
		return pathElements.iterator();
	}

	public boolean isEmpty() {
		return pathElements.isEmpty();
	}

	/**
	 * Gets the record at the specified index in the path, or <code>Optional.empty</code>
	 * if index is outside the range.
	 * 
	 * @param i
	 * @return
	 */
	public Optional<BaseRecord> get(int i) {
		if (i >= pathElements.size() || i < 0) {
			return Optional.empty();
		}
		return Optional.of(pathElements.get(i));
	}

	/**
	 * Gets the first {@link BaseRecord} which has the specified
	 * <code>type</code> else returns <code>null</code>
	 * 
	 * @param type
	 *            A RecordType
	 * @return A {@link BaseRecord} or <code>Optional.empty</code> if no BaseRecord with
	 *         this {@link RecordType} exists in this {@link RSPath}.
	 */
	public Optional<BaseRecord> findFirstByType(RecordType type) {
		return pathElements.stream().filter(br->br.hasType(type)).findFirst();
	}

	public String getPathAsString(String separator) {
		StringBuffer sb = new StringBuffer();
		for (BaseRecord br : pathElements) {
			sb.append(br.getName()).append(separator);
		}
		if (sb.indexOf(separator) != -1) {
			return sb.substring(0, sb.lastIndexOf(separator)).toString();
		} else {
			return sb.toString();
		}
	}

	public String toString() {
		return getPathAsString("->");
	}

	/**
	 * Merges 2 paths together. Returns an empty path if there is no overlap
	 * between the 2 paths. <br/>
	 * Should be symmetrical, i.e.,
	 * <code> a.merge(b).equals (b.merge(a)) == true </code> should be
	 * invariant.
	 * 
	 * @param other
	 *            another path
	 * @return the merged path, or empty path if no common path elements
	 */
	public RSPath merge(RSPath other) {
		List<BaseRecord> merged = RSCollectionUtils.mergeLists(this.pathElements, other.pathElements);
		if (merged != null) {
			return new RSPath(merged);
		} else {
			return new RSPath(new ArrayList<>());
		}
	}
}
