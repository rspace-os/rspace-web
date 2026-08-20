package com.researchspace.core.util;

import java.io.Serializable;

/**
 * Simple POJO class to hold information about a pagination link.
 */
public class PaginationObject implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 5974897012180102795L;

	/**
	 * The standard class name for links generated from this object. String=
	 * "page_link"
	 */
	public static final String DEFAULT_CLASS_NAME = "page_link";

	@Override
	public String toString() {
		return "PaginationObject [name=" + name + ", Link=" + Link + ", className=" + className + ", pageNumber="
				+ pageNumber + "]";
	}

	private String name;
	private String Link;
	private String className = DEFAULT_CLASS_NAME;
	private int pageNumber;

	/**
	 * Convenience factory method to populate a single pagination object
	 * 
	 * @param name
	 * @param link
	 * @param classname
	 * @return A new {@link PaginationObject}.
	 */
	public static PaginationObject create(String name, String link, String classname) {
		PaginationObject po = new PaginationObject();
		po.setClassName(classname);
		po.setLink(link);
		po.setName(name);
		return po;
	}

	public static PaginationObject create(String name, String link, String classname, int pageNumber) {
		PaginationObject po = new PaginationObject();
		po.setClassName(classname);
		po.setLink(link);
		po.setName(name);
		po.setPageNumber(pageNumber);
		return po;
	}

	/**
	 * Getter for the value of the HTML 'class' attribute for the <a> link </a>
	 * generated from this object.
	 * 
	 * @return
	 */
	public String getClassName() {
		return className;
	}

	/**
	 * Setter for the value of the HTML 'class' attribute for the <a> link </a>
	 * generated from this object. In most cases, this does not need to be
	 * called as there is a default value. However if there are > 1 paginated
	 * lists on a given HTML page, this may help distinguish between them.
	 * 
	 * @param className
	 */
	public void setClassName(String className) {
		this.className = className;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getLink() {
		return Link;
	}

	public void setLink(String link) {
		Link = link;
	}

	public int getPageNumber() {
		return pageNumber;
	}

	public void setPageNumber(int pageNumber) {
		this.pageNumber = pageNumber;
	}
}
