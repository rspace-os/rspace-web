package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.record.StructuredDocument;
import lombok.Value;

@Value
public class ListOfMaterialsCreationEvent implements CreationEvent<ListOfMaterials> {

  private ListOfMaterials createdItem;
  private User createdBy;
  private StructuredDocument elnDocument;
}
