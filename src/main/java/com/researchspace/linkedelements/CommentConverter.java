package com.researchspace.linkedelements;

import com.researchspace.dao.EcatCommentDao;
import com.researchspace.model.EcatComment;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

class CommentConverter extends AbstractFieldElementConverter implements FieldElementConverter {

  private static final Logger LOG = LoggerFactory.getLogger(CommentConverter.class.getName());

  private @Autowired EcatCommentDao ecatCommentDao;

  public Optional<EcatComment> jsoup2LinkableElement(FieldContents contents, Element el) {
    EcatComment ecatComment = null;
    if (el.hasAttr("id")) {
      long id = Long.parseLong(el.attr("id"));
      ecatComment = getComment(id);
      if (ecatComment != null) {
        contents.addElement(
            ecatComment,
            "/workspace/editor/structuredDocument/getComments?commentId=" + id,
            EcatComment.class);
      } else {
        logNotFound("Comment", id);
      }
    }
    return Optional.ofNullable(ecatComment);
  }

  EcatComment getComment(long id) {
    try {
      return ecatCommentDao.getEcatComment(id, null);
    } catch (Exception e) {
      LOG.error("Error getting comment with id {}.", id, e);
      return null;
    }
  }
}
