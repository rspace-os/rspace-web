package com.researchspace.dao;

/*EcatCommentDao data access object as persistent layer. CommentEcat is atomic comment
 * A comment may contains multiple atomic comments.
 * @sunny
 */

import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import java.util.List;

public interface EcatCommentDao extends GenericDao<EcatComment, Long> {

  /**
   * Persists a new comment
   *
   * @param cm A transient comment
   * @return the persistted comment
   */
  EcatComment addComment(EcatComment cm);

  /**
   * @param cm
   * @param itm
   */
  void addCommentItem(EcatComment cm, EcatCommentItem itm);

  List<EcatComment> getCommentAll(Long parentId);

  EcatComment getEcatComment(Long comId, Long parentId);

  List<EcatCommentItem> getCommentItems(Long comId);

  void deleteComments(Long parentId);

  void deleteComment(Long comId, Long parentId);

  void deleteCommentItem(Long itemId);

  void updateCommentItem(EcatCommentItem citm);
}
