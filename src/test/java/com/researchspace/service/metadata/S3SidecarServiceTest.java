package com.researchspace.service.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.NfsManager;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesFactory;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class S3SidecarServiceTest {

  private final YAMLMapper yaml = new YAMLMapper();

  private NfsManager nfsManager;
  private S3UtilitiesFactory s3UtilitiesFactory;
  private S3Utilities s3Utilities;
  private FilestoreAclChecker aclChecker;
  private UserExternalIdResolver orcidResolver;
  private IPropertyHolder propertyHolder;
  private AuditTrailService auditService;
  private S3SidecarService service;

  private final User user = new User("jmuller");

  @BeforeEach
  void setUp() {
    nfsManager = mock(NfsManager.class);
    s3UtilitiesFactory = mock(S3UtilitiesFactory.class);
    s3Utilities = mock(S3Utilities.class);
    aclChecker = mock(FilestoreAclChecker.class);
    orcidResolver = mock(UserExternalIdResolver.class);
    propertyHolder = mock(IPropertyHolder.class);
    auditService = mock(AuditTrailService.class);

    user.setFirstName("Jana");
    user.setLastName("Müller");

    NfsFileSystem filesystem = new NfsFileSystem();
    filesystem.setName("lrz-filestore");
    NfsFileStore filestore = new NfsFileStore();
    filestore.setFileSystem(filesystem);
    when(nfsManager.getNfsFileStore(1L)).thenReturn(filestore);
    when(s3UtilitiesFactory.createS3UtilitiesForNfsConnector(filesystem)).thenReturn(s3Utilities);
    when(s3Utilities.getBucketName()).thenReturn("lrz-rs-experiments");
    when(propertyHolder.getCustomerName()).thenReturn("Leibniz Supercomputing Centre");
    when(orcidResolver.getExternalIdForUser(user, IdentifierScheme.ORCID))
        .thenReturn(Optional.empty());

    service =
        new S3SidecarService(
            nfsManager,
            s3UtilitiesFactory,
            aclChecker,
            orcidResolver,
            propertyHolder,
            new DataCiteYamlSidecarGenerator(),
            auditService);
  }

  private S3FolderContentItem file(String name, long size, String etag, String storageClass) {
    return new S3FolderContentItem(name, false, size, null, etag, storageClass);
  }

  @Test
  void previewComposesFromListingWithoutWriting() throws Exception {
    when(s3Utilities.listFolderContents("XRD-Experiments"))
        .thenReturn(List.of(file("xrd_run_041.dat", 2202009L, "\"b2c3\"", "STANDARD")));

    GeneratedSidecar result = service.preview(1L, "XRD-Experiments", user);

    JsonNode loc =
        yaml.readTree(result.getContent()).path("relatedItems").path(0).path("s3Location");
    assertEquals("lrz-rs-experiments", loc.path("bucket").asText());
    assertEquals("XRD-Experiments/xrd_run_041.dat", loc.path("key").asText());
    assertEquals("STANDARD", loc.path("storageClass").asText());

    verify(aclChecker).assertCanRead(eq(user), any(NfsFileSystem.class));
    verify(s3Utilities, never()).uploadToS3(anyString(), any(File.class), any());
  }

  @Test
  void saveWritesSidecarWithAttributionAndAuditsCreate() {
    when(s3Utilities.listFolderContents("XRD-Experiments"))
        .thenReturn(List.of(file("xrd_run_041.dat", 2202009L, "\"b2c3\"", "STANDARD")));

    service.save(1L, "XRD-Experiments", user);

    verify(aclChecker).assertCanWrite(eq(user), any(NfsFileSystem.class));

    ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
    ArgumentCaptor<Map<String, String>> metaCaptor = ArgumentCaptor.forClass(Map.class);
    verify(s3Utilities)
        .uploadToS3(eq("XRD-Experiments"), fileCaptor.capture(), metaCaptor.capture());
    assertEquals("XRD-Experiments.sidecar.yaml", fileCaptor.getValue().getName());
    assertEquals("jmuller", metaCaptor.getValue().get(WriteAttribution.META_CREATED_BY));

    ArgumentCaptor<GenericEvent> eventCaptor = ArgumentCaptor.forClass(GenericEvent.class);
    verify(auditService).notify(eventCaptor.capture());
    GenericEvent event = eventCaptor.getValue();
    assertEquals(AuditAction.CREATE, event.getAuditAction());

    SidecarAuditEvent payload = (SidecarAuditEvent) event.getAuditedObject();
    assertEquals("lrz-filestore", payload.filestore());
    assertEquals("XRD-Experiments", payload.path());
    assertEquals("XRD-Experiments.sidecar.yaml", payload.filename());
    assertTrue(
        event.getDescription().contains("XRD-Experiments.sidecar.yaml")
            && event.getDescription().contains("XRD-Experiments")
            && event.getDescription().contains("lrz-filestore"),
        "description should name the sidecar, folder and filestore: " + event.getDescription());
  }

  @Test
  void skipsSubfoldersAndExistingSidecarWhenListingFiles() throws Exception {
    S3FolderContentItem subfolder = new S3FolderContentItem("nested", true, null, null);
    when(s3Utilities.listFolderContents("XRD-Experiments"))
        .thenReturn(
            List.of(
                file("xrd_run_041.dat", 2202009L, "\"b2c3\"", "STANDARD"),
                subfolder,
                file("XRD-Experiments.sidecar.yaml", 4096L, "\"aaaa\"", "STANDARD")));

    GeneratedSidecar result = service.preview(1L, "XRD-Experiments", user);

    JsonNode related = yaml.readTree(result.getContent()).path("relatedItems");
    assertEquals(1, related.size());
    assertEquals(
        "XRD-Experiments/xrd_run_041.dat", related.path(0).path("s3Location").path("key").asText());
  }

  @Test
  void bucketRootFolderYieldsKeysWithoutLeadingSlashAndBucketNamedSidecar() throws Exception {
    when(s3Utilities.listFolderContents(""))
        .thenReturn(List.of(file("top.dat", 10L, "\"e\"", "STANDARD")));

    GeneratedSidecar result = service.preview(1L, "", user);

    assertEquals("lrz-rs-experiments.sidecar.yaml", result.getFilename());
    JsonNode loc =
        yaml.readTree(result.getContent()).path("relatedItems").path(0).path("s3Location");
    assertEquals("top.dat", loc.path("key").asText());
  }
}
