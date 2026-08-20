package com.researchspace.model.record;

import java.util.Optional;
import java.util.Set;

import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.Thumbnail;

/**
 * Interface for converting an object to its containing BaseRecord. This is
 * mainly useful for permissions checking for associated objects.
 */
public interface BaseRecordAdaptable {

	/**
	 * Equivalent to : <br/>
	 * <code>
	 *  getAsBaseRecord( toAdapt, false);
	 *  </code>
	 *
	 * See getAsBaseRecord(Object toAdapt, boolean isLinkedRecord).
	 *
	 * @param toAdapt
	 *            An object that can be linked to from a field - sketch,
	 *            comment, gallery item etc
	 * @return the containing BaseRecord or <code>null</code> if this object
	 *         does not have a containing BaseRecord, or if the containing base
	 *         record is deleted. This is to prevent access by others to these
	 *         elements if the containing record is deleted.
	 *
	 */
	Optional<BaseRecord> getAsBaseRecord(IFieldLinkableElement toAdapt);

	/**
	 * Adapts the supplied object to a BaseRecord, or returns <code>null</code>
	 * if this object cannot be adapted.
	 * <p>
	 * Supported items are :
	 * <ul>
	 * <li>{@link BaseRecord}
	 * <li>{@link EcatComment}
	 * <li>{@link EcatCommentItem}
	 * <li>{@link RSMath}
	 * <li>{@link RSChemElement}
	 * <li>{@link EcatImageAnnotation}
	 * <li>{@link Thumbnail}
	 * <li>{@link Field}
	 * <li>A linked {@link EcatMediaFile} document.
	 * </ul>
	 * 
	 * @param toAdapt
	 * @param isLinkedRecord
	 *            If toAdapt is a BaseRecord or subclass, is this a linked
	 *            record (i.e., are we adapting it to the record containing the
	 *            link (true) or the record itself?(false)
	 * @return return possibly empty but non-null set of {@link BaseRecord}
	 */
	Set<BaseRecord> getAsBaseRecord(IFieldLinkableElement toAdapt, boolean isLinkedRecord);

}
