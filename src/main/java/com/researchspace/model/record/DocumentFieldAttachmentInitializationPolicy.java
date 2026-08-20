package com.researchspace.model.record;

import com.researchspace.model.FieldAttachment;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.field.Field;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.MovableInventoryRecord;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.InventoryEntityField;

/**
 * Decorator for DocumentInitializationPolicy that will initialise a Document's
 * field's attachment and field's list of materials collections.<br/>
 * 
 * This class must be used within a Hibernate session.
 */
public class DocumentFieldAttachmentInitializationPolicy extends DocumentInitializationPolicy {


	public DocumentFieldAttachmentInitializationPolicy(DocumentInitializationPolicy policy) {
		super(policy);
	}

	public DocumentFieldAttachmentInitializationPolicy() {
		super();
	}

	@Override
	protected void doInitialize(BaseRecord baseRecord) {	
		if (baseRecord.isStructuredDocument()) {
			StructuredDocument sd = (StructuredDocument) baseRecord;
			for (Field f : sd.getFields()) {
				for (FieldAttachment fa : f.getLinkedMediaFiles()) {
					fa.getClass();
				}
				for (ListOfMaterials lom : f.getListsOfMaterials()) {
					for (MaterialUsage mu : lom.getMaterials()) {
						InventoryRecord invRec = mu.getInventoryRecord();
						if (invRec != null) {
							invRec.getActiveBarcodes();
							if (invRec instanceof MovableInventoryRecord) {
								populateParentChain(((MovableInventoryRecord) invRec));
							}
							initializeParentSample(invRec); // RSDEV-80
							initializeParentSampleTemplate(invRec); // RSINV-625
						}
					}
				}
			}
		}
	}

	private void populateParentChain(MovableInventoryRecord record) {
		if (record != null) {
			record.getActiveBarcodes();
			populateParentChain(record.getParentContainer());
			populateParentChain(record.getLastNonWorkbenchParent());
		}
	}

	private void initializeParentSampleTemplate(InventoryRecord invRec) {
		// getLinkedSampleTemplate() dispatches polymorphically through Hibernate proxies (Sample
		// returns its template, SubSample delegates to its parent), so no unproxy/cast is needed.
		SampleTemplate templateToInitialize = invRec.getLinkedSampleTemplate();
		if (templateToInitialize != null) {
			templateToInitialize.getImageFileProperty();
		}
	}

	private void initializeParentSample(InventoryRecord invRec) {
		if (invRec.isSubSample()) {
			SampleEntity parentSample = ((SubSample) invRec).getSample();
			for (InventoryEntityField sf : parentSample.getActiveFields()) {
				sf.getAttachedFile();
			}
		}
	}

}
