package com.researchspace.service;

import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.User;
import java.util.List;

/** EcatComment methods moved from RecordManager */
public interface EcatCommentManager {

  /**
   * adds a new transient {@link EcatComment}.
   *
   * @param cm A transient {@link EcatComment}
   * @return the persisted comment
   * @see MediaManager#insertEcatComment(String, String, User) which handles authorisation
   */
  EcatComment addComment(EcatComment cm);

  /**
   * adds a new transient {@link EcatCommentItem} to a nexisting {@link EcatComment}.
   *
   * @param cm
   * @see MediaManager#addEcatComment(String, String, User) which handles authorisation
   */
  void addCommentItem(EcatComment cm, EcatCommentItem itm);

  List<EcatComment> getCommentAll(Long parentId);

  /**
   * Gets an {@link EcatComment} with the give id belonging to the given field
   *
   * @param comId
   * @param parentId A Field id
   * @param user subject
   * @return
   */
  EcatComment getEcatComment(Long comId, Long parentId, User user);

  @SuppressWarnings("rawtypes")
  List getCommentItems(Long comId);
}
