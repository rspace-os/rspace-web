package com.researchspace.service.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.netfiles.WriteAttribution;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.repository.spi.IdentifierScheme;
import com.researchspace.service.FilestoreAclChecker;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NfsFieldLinkManager;
import com.researchspace.service.NfsManager;
import com.researchspace.service.RoRService;
import com.researchspace.service.UserExternalIdResolver;
import com.researchspace.service.aws.S3Utilities;
import com.researchspace.service.aws.impl.S3UtilitiesFactory;
import com.researchspace.service.aws.impl.S3UtilitiesImpl.S3FolderContentItem;
import jakarta.ws.rs.NotFoundException;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class S3SidecarFileServiceTest {

  private final YAMLMapper yaml = new YAMLMapper();

  private NfsManager nfsManager;
  private S3UtilitiesFactory s3UtilitiesFactory;
  private S3Utilities s3Utilities;
  private FilestoreAclChecker aclChecker;
  private UserExternalIdResolver orcidResolver;
  private IPropertyHolder propertyHolder;
  private AuditTrailService auditService;
  private MessageSourceUtils messages;
  private RoRService rorService;
  private NfsFieldLinkManager nfsFieldLinkManager;
  private NfsFileSystem filesystem;
  private S3SidecarFileService service;

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
    messages = mock(MessageSourceUtils.class);
    rorService = mock(RoRService.class);
    nfsFieldLinkManager = mock(NfsFieldLinkManager.class);
    when(rorService.getSystemRoRValue()).thenReturn("https://ror.org/00t3r8h32");

    user.setFirstName("Jana");
    user.setLastName("Müller");

    filesystem = new NfsFileSystem();
    filesystem.setName("lrz-filestore");
    filesystem.setClientType(NfsClientType.S3);
    NfsFileStore filestore = new NfsFileStore();
    filestore.setFileSystem(filesystem);
    when(nfsManager.getNfsFileStore(1L)).thenReturn(filestore);
    when(s3UtilitiesFactory.createS3UtilitiesForNfsConnector(filesystem)).thenReturn(s3Utilities);
    when(s3Utilities.getBucketName()).thenReturn("lrz-rs-experiments");
    when(propertyHolder.getCustomerName()).thenReturn("Leibniz Supercomputing Centre");
    when(orcidResolver.getExternalIdForUser(user, IdentifierScheme.ORCID))
        .thenReturn(Optional.empty());

    service =
        new S3SidecarFileService(
            nfsManager,
            s3UtilitiesFactory,
            aclChecker,
            orcidResolver,
            propertyHolder,
            new DataCiteYamlSidecarFileGenerator(),
            auditService,
            messages,
            rorService,
            nfsFieldLinkManager);
  }

  private S3FolderContentItem file(String name, long size, String etag, String storageClass) {
    return new S3FolderContentItem(name, false, size, null, etag, storageClass);
  }

  private RecordInformation readableDoc(long id) {
    RecordInformation info = new RecordInformation();
    info.setId(id);
    info.setOid(new GlobalIdentifier(GlobalIdPrefix.SD, id));
    info.setName("doc " + id);
    return info;
  }

  @Test
  void namesReadableDocumentsLinkingTheFolderOrItsFilesSkippingPrivateOnes() throws Exception {
    when(s3Utilities.listFolderContents("XRD-Experiments"))
        .thenReturn(
            List.of(
                file("xrd_run_041.dat", 2202009L, "\"b2c3\"", "STANDARD"),
                new S3FolderContentItem("nested", true, null, null)));
    when(propertyHolder.getServerUrl()).thenReturn("https://rspace.example.com");
    RecordInformation privateDoc = new RecordInformation();
    privateDoc.setOwnerFullName("Grace Hopper");
    when(nfsFieldLinkManager.getLinkedDocuments(
            filesystem, List.of("XRD-Experiments", "XRD-Experiments/xrd_run_041.dat"), user))
        .thenReturn(List.of(readableDoc(11L), readableDoc(12L), privateDoc));

    JsonNode related =
        yaml.readTree(service.preview(1L, "XRD-Experiments", user).getContent())
            .path("relatedIdentifiers");

    assertThat(related).hasSize(2);
    assertEquals(
        "https://rspace.example.com/globalId/SD11",
        related.path(0).path("relatedIdentifier").path("value").asText());
    assertEquals(
        "https://rspace.example.com/globalId/SD12",
        related.path(1).path("relatedIdentifier").path("value").asText());
  }

  @Test
  void serverUrlWithTrailingSlashGivesOneSlashBeforeGlobalId() throws Exception {
    when(s3Utilities.listFolderContents("XRD-Experiments")).thenReturn(List.of());
    when(propertyHolder.getServerUrl()).thenReturn("https://rspace.example.com/");
    when(nfsFieldLinkManager.getLinkedDocuments(filesystem, List.of("XRD-Experiments"), user))
        .thenReturn(List.of(readableDoc(11L)));

    JsonNode related =
        yaml.readTree(service.preview(1L, "XRD-Experiments", user).getContent())
            .path("relatedIdentifiers");

    assertEquals(
        "https://rspace.example.com/globalId/SD11",
        related.path(0).path("relatedIdentifier").path("value").asText());
  }

  @Test
  void saveWritesTheLinkedDocumentsIntoTheFile() throws Exception {
    when(s3Utilities.listFolderContents("XRD-Experiments")).thenReturn(List.of());
    when(propertyHolder.getServerUrl()).thenReturn("https://rspace.example.com");
    when(nfsFieldLinkManager.getLinkedDocuments(filesystem, List.of("XRD-Experiments"), user))
        .thenReturn(List.of(readableDoc(11L)));
    StringBuilder uploaded = new StringBuilder();
    doAnswer(
            invocation -> {
              uploaded.append(Files.readString(invocation.<File>getArgument(1).toPath()));
              return null;
            })
        .when(s3Utilities)
        .uploadToS3(eq("XRD-Experiments"), any(File.class), any());

    service.save(1L, "XRD-Experiments", user);

    assertEquals(
        "https://rspace.example.com/globalId/SD11",
        yaml.readTree(uploaded.toString())
            .path("relatedIdentifiers")
            .path(0)
            .path("relatedIdentifier")
            .path("value")
            .asText());
  }

  @Test
  void rootFolderAsksForTheEmptyPathAndItsFiles() throws Exception {
    when(s3Utilities.listFolderContents(""))
        .thenReturn(List.of(file("top.dat", 10L, "\"e\"", "STANDARD")));
    RecordInformation privateDoc = new RecordInformation();
    privateDoc.setOwnerFullName("Grace Hopper");
    when(nfsFieldLinkManager.getLinkedDocuments(filesystem, List.of("", "top.dat"), user))
        .thenReturn(List.of(privateDoc));

    GeneratedSidecarFile result = service.preview(1L, "", user);

    verify(nfsFieldLinkManager).getLinkedDocuments(filesystem, List.of("", "top.dat"), user);
    assertTrue(yaml.readTree(result.getContent()).path("relatedIdentifiers").isMissingNode());
    verify(propertyHolder, never()).getServerUrl();
  }

  @Test
  void previewComposesFromListingWithoutWriting() throws Exception {
    when(s3Utilities.listFolderContents("XRD-Experiments"))
        .thenReturn(List.of(file("xrd_run_041.dat", 2202009L, "\"b2c3\"", "STANDARD")));

    GeneratedSidecarFile result = service.preview(1L, "XRD-Experiments", user);

    JsonNode loc =
        yaml.readTree(result.getContent()).path("relatedItems").path(0).path("s3Location");
    assertEquals("lrz-rs-experiments", loc.path("bucket").asText());
    assertEquals("XRD-Experiments/xrd_run_041.dat", loc.path("key").asText());
    assertEquals("STANDARD", loc.path("storageClass").asText());

    // The system ROR flows through to the creator's affiliation identifier.
    JsonNode affiliation =
        yaml.readTree(result.getContent()).path("creators").path(0).path("affiliations").path(0);
    assertEquals(
        "https://ror.org/00t3r8h32",
        affiliation.path("affiliationIdentifier").path("value").asText());

    verify(aclChecker).assertCanRead(eq(user), any(NfsFileSystem.class));
    verify(s3Utilities, never()).uploadToS3(anyString(), any(File.class), any());
  }

  @Test
  void saveWritesSidecarFileWithAttributionAndAuditsCreate() {
    when(s3Utilities.listFolderContents("XRD-Experiments"))
        .thenReturn(List.of(file("xrd_run_041.dat", 2202009L, "\"b2c3\"", "STANDARD")));

    service.save(1L, "XRD-Experiments", user);

    verify(aclChecker).assertCanWrite(eq(user), any(NfsFileSystem.class));

    ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
    ArgumentCaptor<Map<String, String>> metaCaptor = ArgumentCaptor.forClass(Map.class);
    verify(s3Utilities)
        .uploadToS3(eq("XRD-Experiments"), fileCaptor.capture(), metaCaptor.capture());
    assertThat(fileCaptor.getValue()).hasName("XRD-Experiments.sidecar.yaml");
    assertThat(metaCaptor.getValue()).containsEntry(WriteAttribution.META_CREATED_BY, "jmuller");

    ArgumentCaptor<GenericEvent> eventCaptor = ArgumentCaptor.forClass(GenericEvent.class);
    verify(auditService).notify(eventCaptor.capture());
    GenericEvent event = eventCaptor.getValue();
    assertEquals(AuditAction.CREATE, event.getAuditAction());

    SidecarFileAuditEvent payload = (SidecarFileAuditEvent) event.getAuditedObject();
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
  void skipsSubfoldersAndExistingSidecarFileWhenListingFiles() throws Exception {
    S3FolderContentItem subfolder = new S3FolderContentItem("nested", true, null, null);
    when(s3Utilities.listFolderContents("XRD-Experiments"))
        .thenReturn(
            List.of(
                file("xrd_run_041.dat", 2202009L, "\"b2c3\"", "STANDARD"),
                subfolder,
                file("XRD-Experiments.sidecar.yaml", 4096L, "\"aaaa\"", "STANDARD")));

    GeneratedSidecarFile result = service.preview(1L, "XRD-Experiments", user);

    JsonNode related = yaml.readTree(result.getContent()).path("relatedItems");
    assertThat(related).hasSize(1);
    assertEquals(
        "XRD-Experiments/xrd_run_041.dat", related.path(0).path("s3Location").path("key").asText());
  }

  @Test
  void unknownFilestoreIsNotFound() {
    // getNfsFileStore returns null for an unknown id; expect a clean 404, not an NPE/500.
    assertThrows(NotFoundException.class, () -> service.preview(999L, "folder", user));
    assertThrows(NotFoundException.class, () -> service.save(999L, "folder", user));
  }

  @Test
  void nonS3FilestoreIsNotFound() {
    NfsFileSystem sftp = new NfsFileSystem();
    sftp.setName("sftp-store");
    sftp.setClientType(NfsClientType.SFTP);
    NfsFileStore store = new NfsFileStore();
    store.setFileSystem(sftp);
    when(nfsManager.getNfsFileStore(2L)).thenReturn(store);

    assertThrows(NotFoundException.class, () -> service.preview(2L, "folder", user));
    assertThrows(NotFoundException.class, () -> service.save(2L, "folder", user));
  }

  @Test
  void leadingSlashInFolderPathIsStrippedFromKeyPrefix() throws Exception {
    // The UI sends the folder path with a leading slash; with an empty filestore base that would
    // otherwise become a "/dev-folder" prefix, which lists nothing in S3.
    when(s3Utilities.listFolderContents("dev-folder"))
        .thenReturn(List.of(file("run.dat", 5L, "\"c\"", "STANDARD")));

    GeneratedSidecarFile result = service.preview(1L, "/dev-folder", user);

    JsonNode loc =
        yaml.readTree(result.getContent()).path("relatedItems").path(0).path("s3Location");
    assertEquals("dev-folder/run.dat", loc.path("key").asText());
  }

  @Test
  void bucketRootFolderYieldsKeysWithoutLeadingSlashAndBucketNamedSidecarFile() throws Exception {
    when(s3Utilities.listFolderContents(""))
        .thenReturn(List.of(file("top.dat", 10L, "\"e\"", "STANDARD")));

    GeneratedSidecarFile result = service.preview(1L, "", user);

    assertEquals("lrz-rs-experiments.sidecar.yaml", result.getFilename());
    JsonNode loc =
        yaml.readTree(result.getContent()).path("relatedItems").path(0).path("s3Location");
    assertEquals("top.dat", loc.path("key").asText());
  }
}
