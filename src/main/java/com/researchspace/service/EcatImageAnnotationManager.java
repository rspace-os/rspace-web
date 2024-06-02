package com.researchspace.service;

import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.User;
import org.apache.shiro.authz.AuthorizationException;

public interface EcatImageAnnotationManager {

  /**
   * Gets an {@link EcatImageAnnotation} by id, with no permissions checking.
   *
   * @param id
   * @param user
   * @return
   */
  EcatImageAnnotation get(long id, User user);

  EcatImageAnnotation save(EcatImageAnnotation ecatImageAnnotation, User user);

  /**
   * GEts image annoation based on field id and image id
   *
   * @param parentId
   * @param imageId
   * @param user
   * @return
   * @throws AuthorizationException if <code>user</code> lacks read permission for the annotation.
   */
  EcatImageAnnotation getByParentIdAndImageId(long parentId, long imageId, User user);

  void delete(long id, User user) throws Exception;

  String removeBackgroundImageNodesFromZwibblerAnnotation(String annotations);
}
