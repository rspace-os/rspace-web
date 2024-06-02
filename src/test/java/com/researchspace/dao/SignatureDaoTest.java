package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.Signature;
import com.researchspace.model.SignatureHash;
import com.researchspace.model.SignatureHashType;
import com.researchspace.model.User;
import com.researchspace.model.Witness;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import org.apache.shiro.crypto.hash.Sha256Hash;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class SignatureDaoTest extends SpringTransactionalTest {

  private @Autowired SignatureDao signatureDao;
  private @Autowired FileMetadataDao fileDao;

  @Test
  public void testCreateAndSave() throws Exception {
    Signature sig = createSignature();
    signatureDao.save(sig);

    Signature reloaded = signatureDao.get(sig.getId());
    assertEquals(1, reloaded.getWitnesses().size());
    assertNotNull(reloaded.getRecordSigned());
  }

  @Test
  public void testGetWitnessForRecord() throws Exception {
    Signature sig = createSignature();
    signatureDao.save(sig);

    Signature reloaded = signatureDao.getSignatureByRecordId(sig.getRecordSigned().getId());
    assertEquals(sig, reloaded);

    // returns null if not founds
    Signature notexists = signatureDao.getSignatureByRecordId(1234567L);
    assertNull(notexists);
  }

  @Test
  public void testSaveWitness() throws Exception {
    Signature sig = createSignature();
    signatureDao.save(sig);

    Signature reloaded = signatureDao.getSignatureByRecordId(sig.getRecordSigned().getId());
    Witness witness = reloaded.getWitnesses().iterator().next();
    assertFalse(witness.isWitnessed());
    assertNull(witness.getWitnessesDate());

    witness.setWitnessed(true);
    witness.setWitnessesDate(new Date());
    Witness saved = signatureDao.saveOrUpdateWitness(witness);
    assertTrue(saved.isWitnessed());
    assertNotNull(saved.getWitnessesDate());
  }

  @Test
  public void testGetWitnessForUser() throws Exception {
    Signature sig = createSignature();
    signatureDao.save(sig);

    Signature reloaded = signatureDao.getSignatureByRecordId(sig.getRecordSigned().getId());
    Witness witness = reloaded.getWitnesses().iterator().next();
    assertEquals(
        witness,
        signatureDao.getWitnessforRecord(sig.getRecordSigned().getId(), witness.getWitness()));

    // now lets load a user who's not a witness:
    User notAWitness = createAndSaveUserIfNotExists("notawitness");
    assertNull(signatureDao.getWitnessforRecord(sig.getRecordSigned().getId(), notAWitness));
  }

  @Test
  public void testAddSignatures() {

    User anyUser = createAndSaveRandomUser();

    Signature sig = createSignature();
    signatureDao.save(sig);

    Signature saved = signatureDao.getSignatureByRecordId(sig.getRecordSigned().getId());
    assertTrue(saved.getHashes().isEmpty());

    FileStoreRoot root = fileStore.getCurrentFileStoreRoot();
    File actualFile = RSpaceTestUtils.getAnyAttachment();
    FileProperty file = TestFactory.createAFileProperty(actualFile, anyUser, root);
    file.generateURIFromProperties(actualFile);
    fileDao.save(file);

    sig.addHash(new Sha256Hash("test"), SignatureHashType.HTML_EXPORT, file);
    signatureDao.save(sig);

    Signature updated = signatureDao.getSignatureByRecordId(sig.getRecordSigned().getId());
    assertEquals(1, updated.getHashes().size());

    SignatureHash savedHash = (SignatureHash) updated.getHashes().toArray()[0];
    assertNotNull(savedHash.getId());
    assertNotNull(savedHash.getFile().getId());
    assertNotNull(actualFile.getName(), savedHash.getFile().getFileName());

    sig.generateRecordContentHash();
    signatureDao.save(sig);
    Signature updated2 = signatureDao.getSignatureByRecordId(sig.getRecordSigned().getId());
    assertEquals(2, updated2.getHashes().size());

    sig.setHashes(null);
    signatureDao.save(sig);
    Signature updated3 = signatureDao.getSignatureByRecordId(sig.getRecordSigned().getId());
    assertNull(
        updated3
            .getHashes()); /* FIXME not what we expect, hashes shouldn't be directly deletable */
  }

  Signature createSignature() {
    Signature sig = new Signature();
    User u = createAndSaveUserIfNotExists("anyuser");
    User witnessU = createAndSaveUserIfNotExists("witness");
    initialiseContentWithEmptyContent(u);
    initialiseContentWithEmptyContent(witnessU);
    Record r = createBasicDocumentInRootFolderWithText(u, "any");

    Witness witness = new Witness(witnessU);
    sig.setRecordSigned(r);
    sig.setSigner(u);
    sig.addWitnesses(Arrays.asList(new Witness[] {witness}));
    return sig;
  }
}
