package com.researchspace.service.impl;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.CommunicationDao;
import com.researchspace.dao.SignatureDao;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.Witness;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.dtos.MessageTypeFilter;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.PiChangeContext;
import com.researchspace.service.PiChangeHandler;
import java.util.EnumSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class PiChangeHandlerImpl implements PiChangeHandler {

  private @Autowired CommunicationManager comms;
  private @Autowired CommunicationDao commsDao;
  private @Autowired SignatureDao signatureDao;
  private @Autowired IGroupPermissionUtils permUtils;

  @Override
  public void afterPiChanged(
      User oldPi, Group labGroup, User newPi, PiChangeContext piChangeContext) {
    transferOutstandingWitnessRequests(oldPi, labGroup, newPi);
    transferGroupDocumentEditReadPErmissions(oldPi, labGroup, newPi, piChangeContext);
  }

  private void transferGroupDocumentEditReadPErmissions(
      User oldPi, Group labGroup, User newPi, PiChangeContext cntext) {
    permUtils.setReadOrEditAllPermissionsForPi(
        labGroup, newPi, cntext.isInitialPiReadEditPermission());
  }

  // there may be alternative strategies e.g. cancellation in which case we should use strategy
  // pattern
  // to delegte to correct strategy
  private void transferOutstandingWitnessRequests(User oldPi, Group labGroup, User newPi) {
    PaginationCriteria<CommunicationTarget> pgcrit =
        PaginationCriteria.createDefaultForClass(CommunicationTarget.class);
    pgcrit.setResultsPerPage(Integer.MAX_VALUE);

    ISearchResults<MessageOrRequest> openWitnessRequsts =
        comms.getActiveMessagesAndRequestsForUserTargetByType(
            oldPi.getUsername(),
            pgcrit,
            new MessageTypeFilter(EnumSet.of(MessageType.REQUEST_RECORD_WITNESS)));

    log.info(
        "There are {} outstanding witness requests to transfer to {}",
        openWitnessRequsts.getTotalHits(),
        newPi.getUsername());
    for (MessageOrRequest req : openWitnessRequsts.getResults()) {
      CommunicationTarget transferredRequest = new CommunicationTarget();
      transferredRequest.setRecipient(newPi);
      transferredRequest.setStatus(req.getStatus());
      req.addRecipient(transferredRequest);
      // should always retrieve recipient as queries are returned in this way.
      CommunicationTarget toRemove =
          req.getRecipients().stream()
              .filter(ct -> ct.getRecipient().equals(oldPi))
              .findFirst()
              .get();
      req.removeRecipient(toRemove);
      commsDao.save(req);
    }
    // now, update witness objects
    for (Witness witness : signatureDao.getOpenWitnessesByWitnessUser(oldPi)) {
      witness.setWitness(newPi);
      signatureDao.saveOrUpdateWitness(witness);
    }
  }

  @Override
  public void beforePiChanged(User currPI, Group labGroup, User newPi, PiChangeContext cntext) {
    permUtils.setReadOrEditAllPermissionsForPi(labGroup, currPI, false);
  }
}
