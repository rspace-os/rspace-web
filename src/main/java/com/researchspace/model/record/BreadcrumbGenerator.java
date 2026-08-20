package com.researchspace.model.record;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;

/**
 * For generation of breadcrumb HTML link elements for display in UI
 */
public interface BreadcrumbGenerator {

	/**
	 * Display name of first breadcrumb element 
	 * when using {@link #generateBreadcrumbToHome(BaseRecord, Folder, Folder)} method
	 */
	String HOME_FOLDER_DISPLAY_NAME = "Home";

	/**
	 * Generates breadcrumb starting at 'top' folder, ending on 'base' record.
	 * Both 'top' and 'base' elements are included.
	 *  
	 * @param base
	 *            The record for which the breadcrumb will be displayed
	 * @param top
	 *            The top of the breadcrumb hierarchy. May be optional.
	 * @return a {@link Breadcrumb} object.
	 */
	Breadcrumb generateBreadcrumb(BaseRecord base, Folder top);

	/**
	 * Same as {@link #generateBreadcrumb(BaseRecord, Folder)}, but
	 * sets a display name of first element to 'Home' 
	 */
	Breadcrumb generateBreadcrumbToHome(BaseRecord base, Folder home, Folder via);
	
}
