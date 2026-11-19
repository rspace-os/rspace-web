package com.researchspace.api.v1.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.service.archive.export.ExportFailureException;
import com.researchspace.testutils.TestFactory;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.test.MetaDataInstanceFactory;

@ExtendWith(MockitoExtension.class)
class ExportTaskletTest {

  private static final String EXPORT_ID = "abcde";
  private static final Long GROUP_ID = 7L;

  @Mock private ExportImport exportService;
  @Mock private IPropertyHolder properties;
  @Mock private UserManager userManager;
  @Mock private PostArchiveCompletion postArchiveCompleter;
  @Mock private ExportApiStateTracker exportApiState;
  @Mock private MessageSourceUtils messages;

  @InjectMocks private ExportTasklet tasklet;

  @ParameterizedTest
  @ValueSource(strings = {"user", "group", "selection"})
  void exportFailureFailsTheBatchStep(String scope) throws Exception {
    User user = TestFactory.createAnyUser("exporter");
    user.setId(1L);
    ExportApiConfig apiConfig = new ExportApiConfig("xml", scope);
    if ("group".equals(scope)) {
      apiConfig.setId(GROUP_ID);
    }
    ChunkContext chunkContext =
        new ChunkContext(
            new StepContext(
                MetaDataInstanceFactory.createStepExecution(
                    ExportJobParamFactory.createJobParams(apiConfig, user, EXPORT_ID))));
    when(userManager.getUserByUsernameNoSession(user.getUsername())).thenReturn(user);
    when(exportApiState.getById(EXPORT_ID)).thenReturn(Optional.of(new ExportRecordList()));
    when(properties.getServerUrl()).thenReturn("http://localhost:8080");
    URI serverUrl = URI.create("http://localhost:8080");
    ExportFailureException writeFailure = new ExportFailureException("Error writing form XML");
    when(messages.getExceptionChainMessage(writeFailure)).thenReturn("SD1 could not be exported");
    switch (scope) {
      case "user" ->
          when(exportService.syncExportUserWorkToArchive(
                  any(ArchiveExportConfig.class),
                  eq(user),
                  eq(serverUrl),
                  eq(user),
                  eq(postArchiveCompleter),
                  any()))
              .thenThrow(writeFailure);
      case "group" ->
          when(exportService.syncExportGroupToArchive(
                  any(ArchiveExportConfig.class),
                  eq(user),
                  eq(GROUP_ID),
                  eq(serverUrl),
                  eq(postArchiveCompleter),
                  any()))
              .thenThrow(writeFailure);
      default ->
          when(exportService.syncExportSelectionToArchive(
                  any(ArchiveExportConfig.class),
                  eq(user),
                  eq(serverUrl),
                  eq(postArchiveCompleter),
                  any()))
              .thenThrow(writeFailure);
    }

    ExportFailureException jobFailure =
        assertThrows(ExportFailureException.class, () -> tasklet.execute(null, chunkContext));
    verify(exportApiState).clear(EXPORT_ID);
    assertSame(writeFailure, jobFailure.getCause());
    // the jobs API reports the first line of the stack trace, which starts with toString()
    assertEquals("SD1 could not be exported", jobFailure.toString());
  }
}
