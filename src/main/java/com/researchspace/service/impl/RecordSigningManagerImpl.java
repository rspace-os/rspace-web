package com.researchspace.service.impl;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static com.researchspace.model.comms.MessageType.REQUEST_RECORD_WITNESS;
import static com.researchspace.model.views.SigningResult.DOC_SIGN_SUCCESS_MSG;

import com.researchspace.comms.CommunicationTargetFinderPolicy;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.SignatureDao;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.FileProperty;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Signature;
import com.researchspace.model.User;
import com.researchspace.model.Witness;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.model.events.SigningCreationEvent;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;
import com.researchspace.model.views.JournalEntry;
import com.researchspace.model.views.SigningResult;
import com.researchspace.service.AuditManager;
import com.researchspace.service.IReauthenticator;
import com.researchspace.service.MessageOrRequestCreatorManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RSpaceRequestManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.service.UserManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class RecordSigningManagerImpl implements RecordSigningManager {

  private @Autowired UserManager userManager;
  private @Autowired IReauthenticator reauthenticator;
  private @Autowired RSpaceRequestManager requestMgr;
  private @Autowired MessageOrRequestCreatorManager requestCreateMgr;
  private @Autowired RecordDao recordDao;
  private @Autowired IPermissionUtils permissnUtils;
  private @Autowired SignatureDao signatureDao;
  private @Autowired RecordEditorTracker tracker;
  private @Autowired OperationFailedMessageGenerator authMsgGen;
  private @Autowired ApplicationEventPublisher publisher;
  private @Autowired RichTextUpdater updater;
  private @Autowired AuditManager auditMgr;

  @Autowired
  @Qualifier("strictTargetFinderPolicy")
  private CommunicationTargetFinderPolicy policy;

  @Override
  public SigningResult signRecordNoPublishEvent(
      Long recordId, User signer, String[] witnesses, String statement) {
    return signRecord(recordId, signer, witnesses, statement, false);
  }

  private SigningResult signRecord(
      Long recordId,
      User signer,
      String[] witnesses,
      String statement,
      boolean shouldPublishEvent) {

    Record record = recordDao.get(recordId);
    Optional<String> editor = tracker.isEditing(record);
    if (editor.isPresent() && record.getOwner().getUsername().equals(signer.getUsername())) {
      return new SigningResult(
          record, "This document is currently being edited by " + editor.get(), null);
    }

    if (record.isStructuredDocument()) {
      AuditedEntity<Record> currentRevision =
          auditMgr.getNewestRevisionForEntity(Record.class, recordId);
      if (currentRevision != null) {
        updater.updateLinksWithRevisions(
            record.asStrucDoc(), currentRevision.getRevision().intValue());
      }
    }

    // We set the attribute true (signed) in the record.
    record.setSigned(true);
    recordDao.save(record);

    // Creation of signature.
    Signature signature = new Signature();
    signature.setRecordSigned(record);
    signature.generateRecordContentHash();
    Date signatureDate = new Date();
    signature.setSignatureDate(signatureDate);
    signature.setSigner(signer);
    signature.setStatement(statement);

    // Creation of witness list.
    List<Witness> list = new ArrayList<>();
    if (witnesses != null && !witnesses[0].equalsIgnoreCase("NoWitnesses")) {
      for (int i = 0; i < witnesses.length; i++) {
        User witnessUser = userManager.getUserByUsername(witnesses[i]);
        Witness witness = new Witness(witnessUser);
        list.add(witness);
      }
    }
    signature.addWitnesses(list);

    // Save the signature and witnesses in the DB.
    signature = signatureDao.save(signature);

    // Send the request to possible witnesses.
    if (witnesses != null && !witnesses[0].equalsIgnoreCase("NoWitnesses")) {
      MessageOrRequest mor = sendWitnessRequestsToPotentialWitnesses(witnesses, record, signer);
      signature.setWitnessRequest(mor);
      signature = signatureDao.save(signature);
    }
    SigningResult result = new SigningResult(record, DOC_SIGN_SUCCESS_MSG, signature);
    if (shouldPublishEvent) {
      publisher.publishEvent(new SigningCreationEvent(result, signer));
    }
    return result;
  }

  @Override
  public SigningResult signRecord(
      Long recordId, User signer, String[] witnesses, String statement) {
    return signRecord(recordId, signer, witnesses, statement, true);
  }

  @Override
  public Witness updateWitness(Long recordId, User witnessUser, Boolean option, String declineMsg) {
    Record record = recordDao.get(recordId);
    record.setWitnessed(option);
    recordDao.save(record);

    Witness witness = signatureDao.getWitnessforRecord(recordId, witnessUser);
    Date witnessesDate = new Date();
    witness.setOptionString(declineMsg);
    witness.setWitnessed(option);
    witness.setWitnessesDate(witnessesDate);

    updateMessageStateOnWitnessing(witness.getSignature(), witnessUser, witness);
    witness = signatureDao.saveOrUpdateWitness(witness);
    witness.getSignature().getHashes().size(); // initialise
    return witness;
  }

  @Override
  public boolean hasPermission(String username, String password) {
    User user = userManager.getUserByUsername(username);
    return reauthenticator.reauthenticate(user, password);
  }

  @Override
  public boolean isReauthenticated(User user, String password) {
    return reauthenticator.reauthenticate(user, password);
  }

  @Override
  public UserPublicInfo[] getPotentialWitnesses(Record record, User user) {
    Set<User> users = policy.findPotentialTargetsFor(REQUEST_RECORD_WITNESS, record, null, user);
    User anonymousUser = userManager.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
    users.remove(anonymousUser);
    UserPublicInfo[] witnesses = new UserPublicInfo[users.size()];
    User[] array = users.toArray(new User[0]);

    for (int i = 0; i < array.length; i++) {
      witnesses[i] = array[i].toPublicInfo();
    }
    return witnesses;
  }

  private Witness getWitnessedBy(Long recordId, User witnessUser) {
    return signatureDao.getWitnessforRecord(recordId, witnessUser);
  }

  @Override
  public Signature getSignatureForRecord(Long recordId) {
    Signature signature = signatureDao.getSignatureByRecordId(recordId);
    signature.getHashes().size(); // initialise
    return signature;
  }

  private MessageOrRequest sendWitnessRequestsToPotentialWitnesses(
      String[] witnessUserNames, Record signed, User signer) {

    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg(signer, permissnUtils);
    config.setRecordId(signed.getId());
    config.setMessageType(MessageType.REQUEST_RECORD_WITNESS);
    config.setRecipientnames(StringUtils.join(witnessUserNames, ","));
    Set<String> unames = toSet(witnessUserNames);
    config.setTargetFinderPolicy("STRICT");
    return requestCreateMgr.createRequest(config, signer.getUsername(), unames, null, null);
  }

  /**
   * Call this method at the end of the method updating the witness.
   *
   * @param updated
   * @param witness
   * @param witness2
   */
  private void updateMessageStateOnWitnessing(
      Signature updated, User witnessUser, Witness witness) {
    CommunicationStatus status = null;
    String message = null;
    if (witness.isWitnessed()) {
      status = CommunicationStatus.COMPLETED;
    } else {
      status = CommunicationStatus.REJECTED;
      message = witness.getOptionString();
    }
    requestMgr.updateStatus(
        witnessUser.getUsername(), status, updated.getWitnessRequest().getId(), message);
  }

  @Override
  public boolean canSign(Long recordId, User user) {
    BaseRecord record = recordDao.get(recordId);
    return !record.isSigned() && permissnUtils.isPermitted(record, PermissionType.SIGN, user);
  }

  @Override
  public boolean canWitness(Long recordId, User user) {
    // BaseRecord record = recordDao.get(recordId);
    Signature sig = signatureDao.getSignatureByRecordId(recordId);
    if (sig != null) {
      Witness w = getWitnessedBy(recordId, user);
      if (w != null && !w.isWitnessed()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isSigned(Long recordId) {
    BaseRecord record = recordDao.get(recordId);
    return record.isSigned();
  }

  @Override
  public boolean isWitnessed(Long recordId) {
    BaseRecord record = recordDao.get(recordId);
    return record.isWitnessed();
  }

  @Override
  public void updateSigningAttributes(JournalEntry entry, Long recordId, User user) {

    BaseRecord record = recordDao.get(recordId);
    boolean permission = permissnUtils.isPermitted(record, PermissionType.SIGN, user);
    boolean canSign = permission && !record.isSigned();
    entry.setCanSign(canSign);

    boolean canWitness = false;
    if (record.isSigned()) {
      Signature signature = getSignatureForRecord(recordId);
      entry.setSignatureInfo(signature.toSignatureInfo());

      Witness w = getWitnessedBy(recordId, user);
      if (w != null && !w.isWitnessed()) {
        canWitness = true;
      }
    }
    entry.setCanWitness(canWitness);

    entry.setSigned(record.isSigned());
    entry.setWitnessed(record.isWitnessed());
  }

  @Override
  public Optional<FileProperty> getSignedExport(
      Long signatureId, User subject, Long filePropertyId) {
    Signature sig = signatureDao.get(signatureId);
    if (!permissnUtils.isPermitted(sig.getRecordSigned(), PermissionType.READ, subject)) {
      throw new AuthorizationException(
          authMsgGen.getFailedMessage(subject.getUsername(), "download signed exports"));
    }
    Optional<FileProperty> fp =
        sig.getHashes().stream()
            .filter(hash -> hash.getFile() != null && hash.getFile().getId().equals(filePropertyId))
            .map(hash -> hash.getFile())
            .findFirst();
    if (fp.isPresent()) {
      Hibernate.initialize(fp.get());
    }
    return fp;
  }
}
