package com.researchspace.model.record;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import java.io.Serializable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;

import org.hibernate.envers.Audited;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.UniquelyIdentifiable;

/**
 * A Form defines the content of a {@link StructuredDocument} but does not
 * itself contain any data. <br/>
 * Forms exist independently of <code>Structured Documents</code> and should not
 * be removed when a <code>Structured Document</code> is removed.
 * <p>
 * Forms can exist in 1 of 4 states: <b>NEW</b>, <b>PUBLISHED</b> or
 * <b>UNPUBLISHED</b> or <b>OLD</b>. <br>
 * NEW signifies a newly created Form that is still under development. Edits to
 * a Form in this state won't trigger a version change. <br>
 * PUBLISHED signifies a Form that is available to users to create documents.
 * Changes to a Form in this state will trigger a version change. <br>
 * UNPUBLISHED indicates a Form that has been withdrawn from use. Changes to a
 * Form in this state will trigger a version change. <br>
 * OLD - a newer version of the Form exists. <br/>
 * The following transitions are allowed:
 * <ul>
 * <li>New->Published
 * <li>New->Unpublished
 * <li>Published<->Unpublished
 * <li>Published | Unpublished -> OLD
 * <li>OLD-> Published | Unpublished, only if the current Form also is set to
 * OLD, to ensure that there are never > 1 current Form.
 * </ul>
 * I.e., once a Form has become published, it cannot be set to 'NEW' again.
 * 
 * @see <a href=
 *      "https://docs.google.com/document/d/1PyujZ67FAF0jEHsTLwCqlShaLLVnYY8pWf316bXbQLc/edit">
 *      Form GoogleDoc </a>
 */
@Entity
@Audited
@DiscriminatorValue("RSF")
public class RSForm  extends AbstractForm implements Serializable, UniquelyIdentifiable, PermissionsAdaptable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -244687216857908083L;
	
	/**
	 * Default constructor, sets creation/ modification times and a generic
	 * title. This is for internal use; services/clients should call the
	 * constructor that sets in user and name.
	 */
	public RSForm() {
		super();
	}

	/**
	 * Creates form of type FormType.NORMAL
	 * @param name
	 * @param desc
	 * @param createdBy
	 */
	public RSForm(String name, String desc, User createdBy) {
		super(name, desc, createdBy, FormType.NORMAL);
	}
	
	@Override
	@Transient
	public GlobalIdentifier getOid() {
		return new GlobalIdentifier(GlobalIdPrefix.FM, getId());
	}

	private RSForm tempForm = null;
	
	@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = true)
	public RSForm getTempForm() {
		return tempForm;
	}

	/**
	 * Sets the temporary form for this form, and sets the 'temporary' flag to
	 * <code>true</code> in the temp form.
	 * 
	 * @param tempForm
	 *            The temporary form (i.e., the form being edited).
	 */
	public void setTempForm(RSForm tempForm) {
		this.tempForm = tempForm;
		if (tempForm != null) {
			tempForm.setTemporary(true);
		}
	}

	@Transient
	@JsonIgnore
	@AuditTrailIdentifier()
	public String getOidString() {
		return getOid().getIdString();
	}

}
