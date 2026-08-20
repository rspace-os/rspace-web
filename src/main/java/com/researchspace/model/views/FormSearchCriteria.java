package com.researchspace.model.views;

import com.researchspace.core.util.FilterCriteria;
import com.researchspace.core.util.UISearchTerm;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.FormType;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Encapsulates search criteria for list of forms.
 */
@Data
@EqualsAndHashCode(callSuper=false)
public class FormSearchCriteria extends FilterCriteria {

	
	private static final long serialVersionUID = 1L;
	private boolean includeSystemForm;
	/**
	 * Only return forms that the user has selected to be in their create menu.
	 * @return the isInUserMenu
	 */
	private boolean inUserMenu = false;
	
	/**
	 * Restrict forms to a particular type
	 */
	private FormType formType = FormType.NORMAL;	
	
	/**
	 * Only include forms created by the user. DEfault is <code>false</code> since
	 *  this class is used in many different sorts of Form search, not just in form listing.
	 *  RSPAC-1749
	 */
	@UISearchTerm
	private boolean userFormsOnly = false;


	/**
	 * @return <code>true</code> if we only want to search published forms.
	 * Defaults to <code>true</code>.
	 */
	private boolean publishedOnly = true;
	
	@UISearchTerm
	private String searchTerm;

	@UISearchTerm
	private PermissionType requestedAction = PermissionType.READ;
	
	public FormSearchCriteria() {
		super();
	}

	public FormSearchCriteria(PermissionType requestedAction) {
		this.requestedAction = requestedAction;
	}
}
