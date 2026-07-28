package com.researchspace.model.record;

import org.apache.commons.lang3.Validate;

import com.researchspace.model.EcatDocumentFile;
/**
 * Initialises ECatDocument with associations to retrieve images
 */
public class EcatDocumentThumbnailInitializationPolicy extends DocumentInitializationPolicy {

	public EcatDocumentThumbnailInitializationPolicy(DocumentInitializationPolicy decorator) {
		super(decorator);
	}

	public EcatDocumentThumbnailInitializationPolicy() {
	}

	@Override
	protected void doInitialize(BaseRecord baseRecord) {
		Validate.isTrue(baseRecord.isEcatDocument(), "Must be an Ecat Document");
		EcatDocumentFile document = (EcatDocumentFile) baseRecord;
		if (document.getThumbNail() != null) {
			document.getThumbNail().getData();
		} else if (document.getDocThumbnailFP() != null) {
			document.getDocThumbnailFP().getRoot();
		}
	}
}
