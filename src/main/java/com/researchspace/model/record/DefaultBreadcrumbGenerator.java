package com.researchspace.model.record;

import org.apache.commons.lang3.Validate;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSPath;

/**
 * Generates breadcrumb showing parent folders of a record.
 */
public class DefaultBreadcrumbGenerator implements BreadcrumbGenerator {

	public Breadcrumb generateBreadcrumb(BaseRecord base, Folder top) {
		return generateBreadcrumb(base, top, null, null);
	}

	public Breadcrumb generateBreadcrumbToHome(BaseRecord base, Folder home, Folder via) {
		return generateBreadcrumb(base, home, BreadcrumbGenerator.HOME_FOLDER_DISPLAY_NAME, via);
	}

	private Breadcrumb generateBreadcrumb(BaseRecord base, Folder top, String topDisplayName, Folder via) {
		Validate.noNullElements(new Object[] {base, top});

		RSPath hierarchy = base.getShortestPathToParentVia(top, null, via);

		Breadcrumb bc = new Breadcrumb();
		for (BaseRecord br : hierarchy) {
			bc.addElement(br);
		}

		if (topDisplayName != null && bc.isContainLinks()) {
			bc.getElements().get(0).setDisplayname(topDisplayName);
		}		
		return bc;
	}
}
