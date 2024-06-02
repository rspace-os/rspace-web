package com.researchspace.service;

import static com.researchspace.model.comms.MessageType.REQUEST_RECORD_WITNESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.dao.SignatureDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Signature;
import com.researchspace.model.SignatureHash;
import com.researchspace.model.SignatureHashType;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.model.views.SigningResult;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RecordSigningManagerTest extends SpringTransactionalTest {

  private @Autowired RecordSigningManager signingMgr;
  private @Autowired SignatureDao signingDao;
  private User user;

  @Before
  public void setUp() throws Exception {
    user = createAndSaveUserIfNotExists("newUser");
    initialiseContentWithEmptyContent(user);
    // sets up 'role' permissions for sending requests
    setUpPermissionsForUser(user);
    assertTrue(user.isContentInitialized());
    propertyHolder.setStandalone("true");
  }

  @After
  public void tearDown() throws Exception {
    RSpaceTestUtils.logout();
  }

  @Test
  public void testSignRecordWithWitness() throws Exception {
    User witnessUser = createWitness();
    logoutAndLoginAs(user);
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(user, "any");
    assertFalse(sdoc.isSigned());
    assertFalse(signingMgr.isSigned(sdoc.getId()));

    SigningResult signResult =
        signingMgr.signRecord(
            sdoc.getId(), user, new String[] {witnessUser.getUsername()}, "statement");

    // ensure signature with content hash is immediately created
    assertTrue(signResult.getSignature().isPresent());
    Signature signature = signResult.getSignature().get();
    assertEquals(1, signature.getHashes().size());
    SignatureHash signatureHash = signature.getHashes().iterator().next();
    String expectedHash = sdoc.getRecordContentHashForSigning().toHex();
    assertEquals(expectedHash, signatureHash.getHexValue());

    // reload record from DB and ensure it's signed
    StructuredDocument sdoc2 = (StructuredDocument) recordMgr.get(sdoc.getId());
    assertTrue(sdoc2.isSigned());
    assertTrue(signingMgr.isSigned(sdoc.getId()));

    // check witness has got the message:
    ISearchResults<MessageOrRequest> mesges =
        commsDao.getActiveRequestsAndMessagesForUser(
            witnessUser, PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(1, mesges.getTotalHits().intValue());
    assertEquals(MessageType.REQUEST_RECORD_WITNESS, mesges.getResults().get(0).getMessageType());
  }

  @Test
  public void testSignRecordAndCopy() throws Exception {
    // sign with no witnesses, record with media link
    StructuredDocument sdoc3 = createBasicDocumentInRootFolderWithText(user, "any2");
    addImageToField(sdoc3.getFields().get(0), user);
    signingMgr.signRecord(sdoc3.getId(), user, null, "statement");

    // reload record from DB, ensure it's signed
    sdoc3 = (StructuredDocument) recordMgr.get(sdoc3.getId());
    assertTrue(sdoc3.isSigned());

    // now copy signed record
    RecordCopyResult copied =
        recordMgr.copy(sdoc3.getId(), "copy", user, user.getRootFolder().getId());
    StructuredDocument copiedSDoc3 = copied.getUniqueCopy().asStrucDoc();
    // the copy should NOT be signed
    assertFalse(copiedSDoc3.isSigned());
  }

  @Test
  public void testWitnessRecord() throws Exception {
    User witnessUser = createWitness();
    logoutAndLoginAs(user);
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(user, "any");
    assertFalse(sdoc.isSigned());
    assertFalse(signingMgr.isSigned(sdoc.getId()));

    signingMgr.signRecord(
        sdoc.getId(), user, new String[] {witnessUser.getUsername()}, "statement");
    // reload from DB
    StructuredDocument sdoc2 = (StructuredDocument) recordMgr.get(sdoc.getId());
    assertTrue(sdoc2.isSigned());
    assertTrue(signingMgr.isSigned(sdoc.getId()));

    // check witness has got the message:
    ISearchResults<MessageOrRequest> mesges =
        commsDao.getActiveRequestsAndMessagesForUser(
            witnessUser, PaginationCriteria.createDefaultForClass(CommunicationTarget.class));
    assertEquals(1, mesges.getTotalHits().intValue());
    assertEquals(REQUEST_RECORD_WITNESS, mesges.getResults().get(0).getMessageType());

    // check if witness has signed the document:
    logoutAndLoginAs(witnessUser);
    signingMgr.updateWitness(sdoc.getId(), witnessUser, true, "option");
    StructuredDocument witnessedDoc = (StructuredDocument) recordMgr.get(sdoc.getId());
    assertTrue(witnessedDoc.isSigned());
    assertTrue(signingMgr.isWitnessed(sdoc.getId()));
  }

  private User createWitness() throws IllegalAddChildOperation {
    User witness = createAndSaveUserIfNotExists("witness");
    initialiseContentWithEmptyContent(witness);
    return witness;
  }

  @Test
  public void testHasPermissionStringString() throws Exception {
    logoutAndLoginAs(user);
    assertFalse(signingMgr.hasPermission(user.getUsername(), TESTPASSWD + "???"));
    assertTrue(signingMgr.hasPermission(user.getUsername(), TESTPASSWD));
  }

  @Test
  public void testHasPermissionUserString() throws Exception {
    logoutAndLoginAs(user);
    assertFalse(signingMgr.isReauthenticated(user, TESTPASSWD + "???"));
    assertTrue(signingMgr.hasPermission(user.getUsername(), TESTPASSWD));
  }

  @Test
  public void testGetPotentialWitnesses() throws Exception {
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(user, "any");
    // unless shared with a group
    assertTrue(signingMgr.getPotentialWitnesses(sdoc, user).length == 0);
  }

  @Test
  public void testGetExportHashFileProperty() throws Exception {
    logoutAndLoginAs(user);
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(user, "any");
    SigningResult signature = signingMgr.signRecord(sdoc.getId(), user, null, "statement");
    Signature sig = signature.getSignature().get();

    final Long NOT_EXISTING_FP = -1l;
    assertFalse(signingMgr.getSignedExport(sig.getId(), user, NOT_EXISTING_FP).isPresent());
    FileProperty fp = createAndSaveAFileProperty();
    sig.addHash(SecureStringUtils.getHashForSigning("anystring"), SignatureHashType.PDF_EXPORT, fp);
    signingDao.save(sig);

    assertTrue(signingMgr.getSignedExport(sig.getId(), user, fp.getId()).isPresent());
    User other = createAndSaveRandomUser();

    // unauthorised access for other user.
    logoutAndLoginAs(other);
    assertAuthorisationExceptionThrown(
        () ->
            signingMgr.getSignedExport(
                signature.getSignature().get().getId(), other, NOT_EXISTING_FP));
  }

  private FileProperty createAndSaveAFileProperty() throws IOException {
    File anyFile = RSpaceTestUtils.getAnyPdf();
    FileStoreRoot fileRoot = fileStore.getCurrentFileStoreRoot();
    FileProperty fp = TestFactory.createAFileProperty(anyFile, user, fileRoot);
    fileStore.save(fp, anyFile, FileDuplicateStrategy.AS_NEW);
    return fp;
  }
}
