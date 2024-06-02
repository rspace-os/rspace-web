package com.researchspace.service.impl;

import com.researchspace.dao.RSMathDao;
import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.service.AuditManager;
import com.researchspace.service.RSMathManager;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RSMathManagerImpl extends GenericManagerImpl<RSMath, Long> implements RSMathManager {

  private @Autowired BaseRecordAdaptable recordAdapter;
  private @Autowired IPermissionUtils permissionUtils;
  private @Autowired AuditManager auditManager;

  RSMathManagerImpl(RSMathDao dao) {
    super(dao);
  }

  @Override
  public RSMath get(long id, Integer revision, User user, boolean getBytes) {
    RSMath math = dao.get(id);
    Optional<BaseRecord> br = recordAdapter.getAsBaseRecord(math);
    if (!br.isPresent() || !permissionUtils.isPermitted(br.get(), PermissionType.READ, user)) {
      throw new AuthorizationException(
          "Unauthorised attempt by user ["
              + user.getUsername()
              + "] to access math element with id ["
              + id
              + "]");
    }
    if (revision != null) {
      math = auditManager.getObjectForRevision(RSMath.class, id, revision).getEntity();
    }
    if (getBytes) {
      math.getMathSvg().getData();
    }
    return math;
  }
}
