package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.record.StructuredDocument;
import lombok.Value;

@Value
public class ListOfMaterialsDeleteEvent implements DeleteEvent<ListOfMaterials> {

  private ListOfMaterials deletedItem;
  private User deletedBy;
  private StructuredDocument elnDocument;
}
