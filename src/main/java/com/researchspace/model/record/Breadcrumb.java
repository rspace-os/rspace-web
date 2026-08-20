package com.researchspace.model.record;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.Validate;

import com.researchspace.model.record.BaseRecord;

/**
 * Encapsulates a breadcrumb for display in ui.
 */
public class Breadcrumb {

	public static final class BreadcrumbElement {

		private Long id;
		private String displayname;

		private BreadcrumbElement(Long id, String displayname) {
			this.id = id;
			this.displayname = displayname;
		}

		public void setDisplayname(String displayname) {
			this.displayname = displayname;
		}

		public Long getId() {
			return id;
		}

		public String getDisplayname() {
			return displayname;
		}
	}

	private List<BreadcrumbElement> elements = new ArrayList<>();
	private boolean containLinks = false;

	public boolean isContainLinks() {
		return containLinks;
	}

	/**
	 * Elements should be added in order of ascending position in hierarchy. <br/>
	 * The record is not modified by this method. E.g.,
	 * 
	 * <pre>
	 * addElement(child);
	 * addElement(parent);
	 * </pre>
	 * 
	 * @param r
	 */
	public void addElement(final BaseRecord r) {
		Validate.notNull(r);
		elements.add(new BreadcrumbElement(r.getId(), r.getName()));
		containLinks = true;
	}

	public List<BreadcrumbElement> getElements() {
		return elements;
	}
	
	/**
	 * Method to retrieve the parent folder id of every kind of documents (normal, shared,...).
	 * 
	 * @return id of one-before-the-last breadcrumb element, or null
	 */
	public Long getParentFolderId() {
		if (elements.size() > 1) {
			return elements.get(elements.size() - 2).getId();
		}
		return null;
	}

	public Long getFolderId() {
		if (!elements.isEmpty()) {
			return elements.get(elements.size() - 1).getId();
		}
		return null;
	}

	public String getAsStringPath() {
		return elements.stream().skip(1)
				.map(el -> el.getDisplayname())
				.reduce("", (a, b) -> a + "/" + b);
	}

}
