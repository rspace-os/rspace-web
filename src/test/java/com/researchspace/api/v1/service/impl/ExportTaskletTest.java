package com.researchspace.api.v1.service.impl;

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
import com.researchspace.service.UserManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.PostArchiveCompletion;
import com.researchspace.service.archive.export.ExportFailureException;
import com.researchspace.testutils.TestFactory;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.test.MetaDataInstanceFactory;

@ExtendWith(MockitoExtension.class)
class ExportTaskletTest {

  private static final String EXPORT_ID = "abcde";

  @Mock private ExportImport exportService;
  @Mock private IPropertyHolder properties;
  @Mock private UserManager userManager;
  @Mock private PostArchiveCompletion postArchiveCompleter;
  @Mock private ExportApiStateTracker exportApiState;

  @InjectMocks private ExportTasklet tasklet;

  @Test
  void exportFailureFailsTheBatchStep() throws Exception {
    User user = TestFactory.createAnyUser("exporter");
    ChunkContext chunkContext =
        new ChunkContext(
            new StepContext(
                MetaDataInstanceFactory.createStepExecution(
                    ExportJobParamFactory.createJobParams(
                        new ExportApiConfig("xml", "selection"), user, EXPORT_ID))));
    when(userManager.getUserByUsernameNoSession(user.getUsername())).thenReturn(user);
    when(exportApiState.getById(EXPORT_ID)).thenReturn(Optional.of(new ExportRecordList()));
    when(properties.getServerUrl()).thenReturn("http://localhost:8080");
    when(exportService.syncExportSelectionToArchive(
            any(ArchiveExportConfig.class),
            eq(user),
            eq(URI.create("http://localhost:8080")),
            eq(postArchiveCompleter),
            any()))
        .thenThrow(new ExportFailureException("Error writing form XML"));

    assertThrows(ExportFailureException.class, () -> tasklet.execute(null, chunkContext));
    verify(exportApiState).clear(EXPORT_ID);
  }
}
