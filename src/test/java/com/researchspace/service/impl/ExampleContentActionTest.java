package com.researchspace.service.impl;

import static com.researchspace.model.record.TestFactory.createAFolder;
import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.axiope.model.record.init.UserFolderSetupImpl;
import com.researchspace.model.User;
import com.researchspace.service.InitializedContent;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.ImportStrategy;
import java.io.File;
import java.io.IOException;
import javax.servlet.http.HttpSession;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.web.MockHttpSession;

public class ExampleContentActionTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  private @Mock SystemPropertyManager sysPropMgr;
  private @Mock ExportImport exportImport;
  private @Mock ResourceLoader resourceLoader;
  private @Mock ImportStrategy importStrategy;
  private HttpSession session;
  User anyUser = createAnyUser("any");

  @Before
  public void setup() {
    session = new MockHttpSession();
  }

  @InjectMocks private ExampleContentAction exampleContentAction;

  @Test
  public void noImportInvokedIfPropertyNullOrEmpty() throws Exception {
    exampleContentAction.resourcesToLoad = null;
    InitializedContent content = createFolderContent(anyUser);

    exampleContentAction.onFirstLoginAfterContentInitialisation(anyUser, session, content);
    assertImportNotInvoked();

    exampleContentAction.resourcesToLoad = "";
    exampleContentAction.onFirstLoginAfterContentInitialisation(anyUser, session, content);
    assertImportNotInvoked();
  }

  @Test
  public void importSingleFile() throws Exception {
    String location = "file:/a/b/c.txt";
    exampleContentAction.resourcesToLoad = location;
    Resource resource = createValidResource();
    InitializedContent content = createFolderContent(anyUser);
    when(resourceLoader.getResource(location)).thenReturn(resource);

    exampleContentAction.onFirstLoginAfterContentInitialisation(anyUser, session, content);
    assertImportInvoked(1);
  }

  @Test
  public void import3ValidFiles() throws Exception {
    String location = "file:/a/b/c.txt,file:/c/d/e/f.txt,classpath:x/y/z.txt";
    exampleContentAction.resourcesToLoad = location;
    Resource resource = createValidResource();
    InitializedContent content = createFolderContent(anyUser);
    when(resourceLoader.getResource(Mockito.anyString())).thenReturn(resource);

    exampleContentAction.onFirstLoginAfterContentInitialisation(anyUser, session, content);
    assertImportInvoked(3);
  }

  @Test
  public void import3FilesWith1BadPathProceedsToImports2Files() throws Exception {
    String location = "file:/a/b/c.txt,file:/c/d/e/f.txt,classpath:x/y/z.txt";

    exampleContentAction.resourcesToLoad = location;
    Resource resource = createValidResource();
    Resource invalidResource = createInvalidResource();
    InitializedContent content = createFolderContent(anyUser);
    when(resourceLoader.getResource(Mockito.anyString()))
        .thenReturn(resource, invalidResource, resource);

    exampleContentAction.onFirstLoginAfterContentInitialisation(anyUser, session, content);
    assertImportInvoked(2);
  }

  private Resource createValidResource() throws IOException {
    Resource resource = Mockito.mock(Resource.class);
    when(resource.exists()).thenReturn(Boolean.TRUE);
    when(resource.getFile()).thenReturn(File.createTempFile("any", ".tmp"));
    return resource;
  }

  private Resource createInvalidResource() throws IOException {
    Resource resource = Mockito.mock(Resource.class);
    when(resource.exists()).thenReturn(Boolean.FALSE);
    return resource;
  }

  private void assertImportInvoked(int ntimes) throws Exception {
    verify(exportImport, times(ntimes))
        .importArchive(
            Mockito.any(File.class),
            Mockito.eq(anyUser.getUsername()),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(ImportStrategy.class));
  }

  private InitializedContent createFolderContent(User anyUser) {
    UserFolderSetupImpl setup = new UserFolderSetupImpl();
    setup.setExamples(createAFolder("examples", anyUser));
    return new InitializedContent(null, anyUser, setup);
  }

  private void assertImportNotInvoked() throws Exception {
    verify(exportImport, never())
        .importArchive(
            Mockito.any(File.class),
            Mockito.anyString(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any(ImportStrategy.class));
  }
}
