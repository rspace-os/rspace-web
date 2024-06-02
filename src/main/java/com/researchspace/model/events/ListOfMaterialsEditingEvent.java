package com.researchspace.model.events;

import com.researchspace.model.User;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.record.StructuredDocument;
import java.util.List;
import lombok.Value;

@Value
public class ListOfMaterialsEditingEvent implements EditingEvent<ListOfMaterials> {

  private ListOfMaterials editedItem;
  private User editedBy;
  private StructuredDocument elnDocument;
  private List<MaterialUsage> originalMaterials;
}
