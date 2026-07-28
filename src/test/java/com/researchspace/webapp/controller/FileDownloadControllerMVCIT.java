package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.core.testutil.InvokableWithResult;
import com.researchspace.model.EcatImage;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Signature;
import com.researchspace.model.SignatureHash;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.SigningResult;
import com.researchspace.service.AuditManager;
import java.util.List;
import java.util.function.Function;
import org.hibernate.HibernateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

public class FileDownloadControllerMVCIT extends MVCTestBase {

  private @Autowired AuditManager auditMgr;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testDownloadSignedHashHappyCase() throws Exception {
    User any = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(any, "any");
    SigningResult signResult =
        signingManager.signRecord(doc.getId(), any, new String[] {"NoWitnesses"}, "statement");
    assertTrue(signResult.isSuccessful());
    InvokableWithResult<FileProperty> inv =
        () -> {
          List<SignatureHash> hashes2;
          try {
            hashes2 = getSignatureHashesForSignature(signResult.getSignature().get());
            return hashes2.stream().filter(h -> h.getFile() != null).findAny().get().getFile();
          } catch (Exception e) {
            assertFalse(
                1 == 1, "Invokable threw internal exception " + e.getMessage()); // fail test
            return null;
          }
        };
    Function<InvokableWithResult<FileProperty>, FileProperty> p = t -> t.invokeWithResult();
    FileProperty fp = waitUntilNotNull(p, inv, 5000L);
    assertNotNull(fp);
    MvcResult result =
        mockMvc
            .perform(
                get(
                    "/Streamfile/filestore/{signatureId}/{filePropertyId}",
                    signResult.getSignature().get().getId(),
                    fp.getId()))
            .andReturn();
    assertNull(result.getResolvedException());
  }

  private List<SignatureHash> getSignatureHashesForSignature(Signature sig)
      throws HibernateException, Exception {
    return doInTransaction(() -> doGetHashes(sig));
  }

  private List<SignatureHash> doGetHashes(Signature sig) {
    return sessionFactory
        .getCurrentSession()
        .createQuery("from SignatureHash sh where sh.signature = :sig", SignatureHash.class)
        .setParameter("sig", sig)
        .list();
  }

  @Test
  public void testDownloadGalleryImageRevisions() throws Exception {

    User user = createInitAndLoginAnyUser();
    EcatImage image = addImageToGallery(user);
    updateImageInGallery(image.getId(), user);

    // check both image and doc have new revision
    List<AuditedEntity<EcatImage>> updatedImageRevs =
        auditMgr.getRevisionsForEntity(EcatImage.class, image.getId());
    assertEquals(2, updatedImageRevs.size());
    long firstRevId = updatedImageRevs.get(0).getRevision().longValue();

    // download current revision of image
    MvcResult currentRevImageResult =
        mockMvc.perform(get("/Streamfile/" + image.getId())).andExpect(status().isOk()).andReturn();
    assertNull(currentRevImageResult.getResolvedException());
    MockHttpServletResponse currentRevResponse = currentRevImageResult.getResponse();

    assertTrue(currentRevResponse.getHeader("Content-Disposition").contains("Picture2"));
    assertEquals(289399, currentRevResponse.getContentAsByteArray().length);

    // download first revision of image
    MvcResult firstRevImageResult =
        mockMvc
            .perform(get("/Streamfile/" + image.getId() + "?revision=" + firstRevId))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(firstRevImageResult.getResolvedException());
    MockHttpServletResponse firstRevResponse = firstRevImageResult.getResponse();
    assertTrue(firstRevResponse.getHeader("Content-Disposition").contains("Picture1"));
    assertEquals(47326, firstRevResponse.getContentAsByteArray().length);
  }
}
