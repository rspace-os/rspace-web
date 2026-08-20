package com.researchspace.model.record;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.RecordAttachment;
import lombok.NoArgsConstructor;

/**
 * Loads a media record with initialised linked fields and records. Use this
 * method within a hibernate session.
 */
@NoArgsConstructor
public class LinkedFieldsToMediaRecordInitPolicy extends DocumentInitializationPolicy {

	public LinkedFieldsToMediaRecordInitPolicy(DocumentInitializationPolicy decoratedPolicy) {
		super(decoratedPolicy);
	}

	@Override
	protected void doInitialize(BaseRecord baseRecord) {
		if (baseRecord.isMediaRecord()) {
			EcatMediaFile media = (EcatMediaFile) baseRecord;
			for (FieldAttachment f : media.getLinkedFields()) {
				f.getField();
			}
			for (RecordAttachment r : media.getLinkedRecords()) {
				r.getRecord();
			}
		}
	}

}
