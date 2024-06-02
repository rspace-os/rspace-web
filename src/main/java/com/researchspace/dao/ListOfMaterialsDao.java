package com.researchspace.dao;

import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.elninventory.ListOfMaterials;
import java.util.List;

/** For DAO operations on List of Materials. */
public interface ListOfMaterialsDao extends GenericDao<ListOfMaterials, Long> {

  List<ListOfMaterials> findLomsByElnFieldIds(Long... elnFieldIds);

  List<ListOfMaterials> findLomsByInvRecGlobalId(GlobalIdentifier invRecGlobalId);
}
