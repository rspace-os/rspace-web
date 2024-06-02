package com.researchspace.service.impl;

import com.researchspace.dao.EcatCommentDao;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.User;
import com.researchspace.service.EcatCommentManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** EcatComment methods moved from RecordManagerImpl */
@Service("ecatCommentManager")
public class EcatCommentManagerImpl implements EcatCommentManager {

  @Autowired private EcatCommentDao ecatCommentDao;

  @Override
  public EcatComment addComment(EcatComment cm) {
    return ecatCommentDao.addComment(cm);
  }

  @Override
  public void addCommentItem(EcatComment cm, EcatCommentItem itm) {
    ecatCommentDao.addCommentItem(cm, itm);
    // if this is the first comment, then the text of the field will be
    // modified to include the
    // comment link, and auditing mechanism will detect this change and
    // increment the revision number.
    if (isFirstCommentItem(cm)) {
      return;
    }

    // if it's an additional comment item, the field text will not
    // automatically be updated,
    // so we need to force a version increment of the record.
    //		forceVersionUpdate(cm.getParentId(), DeltaType.COMMENT,
    //				DeltaType.COMMENT + " by " + itm.getLastUpdater());
  }

  private boolean isFirstCommentItem(EcatComment cm) {
    return cm.getItems().size() == 1;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public List getCommentAll(Long parentId) {
    return ecatCommentDao.getCommentAll(parentId);
  }

  @Override
  public EcatComment getEcatComment(Long comId, Long parentId, User subject) {
    return ecatCommentDao.getEcatComment(comId, parentId);
  }

  @Override
  @SuppressWarnings("rawtypes")
  public List getCommentItems(Long comId) {

    List<EcatCommentItem> comments = ecatCommentDao.getCommentItems(comId);
    return comments;
  }
}
