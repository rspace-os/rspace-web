package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.permissions.PermissionType;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;

/** Handles API actions around list of inventory materials used in ELN document. */
public interface ListOfMaterialsApiManager {

  List<ApiListOfMaterials> getListOfMaterialsForDocId(Long documentId, User user);

  List<ApiListOfMaterials> getListOfMaterialsForFieldId(Long fieldId, User user);

  List<ApiListOfMaterials> getListOfMaterialsForInvRecGlobalId(
      GlobalIdentifier globalId, User user);

  ApiListOfMaterials getListOfMaterialsById(Long lomId, User user);

  ListOfMaterials getIfExists(Long lomId);

  void assertUserCanAccessApiLom(ApiListOfMaterials apiLom, User user, PermissionType permission);

  boolean canUserAccessApiLom(Long connectedElnFieldId, User user, PermissionType permission);

  ApiListOfMaterials createNewListOfMaterials(ApiListOfMaterials newLom, User user);

  ApiListOfMaterials updateListOfMaterials(ApiListOfMaterials lomUpdate, User user);

  void deleteListOfMaterials(Long lomId, User user);

  ApiListOfMaterials getListOfMaterialsRevision(Long lomId, Long revisionId);

  /*
   * ==============
   * for testing
   * ==============
   */
  void setPublisher(ApplicationEventPublisher publisher);
}
