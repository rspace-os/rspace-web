package com.researchspace.api.v1.service.impl;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.Constants;
import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ExportSelection;
import com.researchspace.model.permissions.IGroupPermissionUtils;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.DiskSpaceChecker;
import com.researchspace.service.GroupManager;
import com.researchspace.service.OperationFailedMessageGenerator;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.export.ArchiveExportPlanner;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.batch.test.MetaDataInstanceFactory;

public class ExportApiSpringBatchHandlerTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  private @Mock JobLauncher launcher;
  private @Mock Job job;
  private @Mock IGroupPermissionUtils grpPermUtils;
  private @Mock UserManager userMgr;
  private @Mock GroupManager grpMgr;
  private @Mock ExportImport exportManager;
  private @Mock JobExplorer jobExplorer;
  private @Mock ArchiveExportPlanner planner;
  private @Mock BaseRecordManager brMgr;
  private @Mock RecordManager recMgr;
  private @Mock DiskSpaceChecker diskSpaceChecker;

  private @Mock OperationFailedMessageGenerator authGen;
  JobExecution exe;

  User user, pi;
  Group group;

  @InjectMocks ExportApiSpringBatchHandlerImpl exportHandler;

  @Before
  public void setUp() throws Exception {
    pi = TestFactory.createAnyUserWithRole("pi", Constants.PI_ROLE);
    pi.setId(1L);
    user = TestFactory.createAnyUser("user");
    user.setId(2L);
    group = TestFactory.createAnyGroup(pi, user);
    group.setId(3L);
    exe = MetaDataInstanceFactory.createJobExecution(5L);
    ExportApiStateTracker store = new ExportApiStateTracker();
    exportHandler.setIdStore(store);
  }

  @After
  public void tearDown() throws Exception {}

  private void mockLaunchJob()
      throws JobExecutionAlreadyRunningException,
          JobRestartException,
          JobInstanceAlreadyCompleteException,
          JobParametersInvalidException {
    Mockito.when(launcher.run(Mockito.eq(job), Mockito.any(JobParameters.class))).thenReturn(exe);
  }

  private void mockCreateRecordList() {
    Mockito.when(
            planner.createExportRecordList(
                Mockito.any(IArchiveExportConfig.class), Mockito.any(ExportSelection.class)))
        .thenReturn(new ExportRecordList());
  }

  private void mockLoadRecords() {
    Mockito.when(brMgr.getByIdAndReadPermission(Mockito.anyList(), Mockito.any(User.class)))
        .thenReturn(Collections.emptyList());
  }

  // numbers in tests correspond to decision table in RSPAC-1315
  @Test
  public void otherUserToHtmlOK_1() throws Exception {
    ExportApiConfig cfg = otherUserToHtml(user);
    setUpSuccessMocks();
    Mockito.when(userMgr.get(user.getId())).thenReturn(user);
    exportHandler.export(cfg, pi);
    verifyJobLaunched();
  }

  @Test()
  public void otherUserToHtmlNotAuthorised_2() throws Exception {
    ExportApiConfig cfg = otherUserToHtml(user);
    when(userMgr.get(user.getId())).thenReturn(user);
    doThrow(new AuthorizationException())
        .when(exportManager)
        .assertExporterCanExportUsersWork(user, pi);
    assertExceptionThrown(() -> exportHandler.export(cfg, pi), AuthorizationException.class);
    verifyJobNotLaunched();
  }

  @Test
  public void userSelfExportOK_3() throws Exception {
    ExportApiConfig cfg = selfUserToHtml();
    setUpSuccessMocks();
    exportHandler.export(cfg, pi);
    verifyJobLaunched();
  }

  @Test
  public void specifiedGroupExportOK_5() throws Exception {
    ExportApiConfig cfg = groupToHtml(group);
    setUpSuccessMocks();
    mockgetGroup();
    when(grpPermUtils.userCanExportGroup(pi, group)).thenReturn(true);
    exportHandler.export(cfg, pi);
    mockCreateRecordList();
    verifyJobLaunched();
  }

  @Test()
  public void groupExportFailsIfNotPi_5b() throws Exception {
    ExportApiConfig cfg = groupToHtml(group);
    mockgetGroup();
    when(grpPermUtils.userCanExportGroup(user, group)).thenReturn(false);
    assertExceptionThrown(() -> exportHandler.export(cfg, user), AuthorizationException.class);
    verifyJobNotLaunched();
  }

  @Test
  public void groupExportFailsIfPiNotAuthorised_6() throws Exception {
    ExportApiConfig cfg = groupToHtml(group);
    mockgetGroup();
    when(grpPermUtils.userCanExportGroup(pi, group)).thenReturn(false);
    assertExceptionThrown(() -> exportHandler.export(cfg, pi), AuthorizationException.class);
    verifyJobNotLaunched();
  }

  @Test
  public void nonSpecifiedGroupExportOK_7() throws Exception {
    ExportApiConfig cfg = groupToHtml(group);
    cfg.setId(null);
    setUpSuccessMocks();
    exportHandler.export(cfg, pi);
    verifyJobLaunched();
  }

  private void setUpSuccessMocks()
      throws JobExecutionAlreadyRunningException,
          JobRestartException,
          JobInstanceAlreadyCompleteException,
          JobParametersInvalidException {
    mockLaunchJob();
    mockCreateRecordList();
    mockLoadRecords();
    Mockito.when(diskSpaceChecker.canStartArchiveProcess()).thenReturn(true);
  }

  @Test
  public void nonSpecifiedGroupByUserNotAuthOK_7b() throws Exception {
    ExportApiConfig cfg = groupToHtml(group);
    cfg.setId(null);
    mockLaunchJob();
    assertExceptionThrown(() -> exportHandler.export(cfg, user), AuthorizationException.class);
    verifyJobNotLaunched();
  }

  @Test
  public void groupExportFailsIfPiNotInGroup8() throws Exception {
    User otherPiNotInGrp = TestFactory.createAnyUserWithRole("other", Constants.PI_ROLE);
    ExportApiConfig cfg = groupToHtml(group);
    mockgetGroup();
    when(grpPermUtils.userCanExportGroup(otherPiNotInGrp, group)).thenReturn(false);
    assertExceptionThrown(
        () -> exportHandler.export(cfg, otherPiNotInGrp), AuthorizationException.class);
    verifyJobNotLaunched();
  }

  @Test
  public void jobNotLaunchedIfJobAlreadyRunning() throws Exception {
    JobParameters params =
        ExportJobParamFactory.createJobParams(new ExportApiConfig("html", "user"), user, "abcde");
    exe = MetaDataInstanceFactory.createJobExecution("exportJob", 1L, 2L, params);
    Mockito.when(jobExplorer.findRunningJobExecutions(ExportTasklet.EXPORT_JOB_NAME))
        .thenReturn(TransformerUtils.toSet(exe));
    assertExceptionThrown(
        () -> exportHandler.export(otherUserToHtml(user), user), TooManyRequestsException.class);
  }

  private void mockgetGroup() {
    when(grpMgr.getGroup(group.getId())).thenReturn(group);
  }

  private ExportApiConfig groupToHtml(Group grp) {
    ExportApiConfig rc = new ExportApiConfig("html", "group");
    rc.setId(grp.getId());
    return rc;
  }

  @Test
  public void exportSelectionThrowsIAEIfNoSelection() throws Exception {
    ExportApiConfig cfg = setUpSelection();
    assertIllegalArgumentException(() -> exportHandler.export(cfg, pi));
  }

  @Test
  public void exportSelectionThrowsIAEIfTooManySelection() throws Exception {
    ExportApiConfig cfg = setUpSelection();
    cfg.setSelections(createNIds(ExportApiSpringBatchHandlerImpl.MAX_IDS_ALLOWED + 1));
    assertIllegalArgumentException(() -> exportHandler.export(cfg, pi));
  }

  @Test
  public void exportSelection() throws Exception {
    ExportApiConfig cfg = setUpSelection();
    cfg.setSelections(createNIds(2));
    setUpSuccessMocks();
    RSpaceDocView view = new RSpaceDocView();
    view.setId(0L);
    view.setType("RECORD:NORMAL");
    RSpaceDocView view2 = new RSpaceDocView();
    view2.setId(1L);
    view2.setType("FOLDER:NOTEBOOK");
    Mockito.when(recMgr.getAllFrom(cfg.getSelections()))
        .thenReturn(TransformerUtils.toList(view, view2));
    exportHandler.export(cfg, pi);
    mockCreateRecordList();
    verifyJobLaunched();
  }

  private Set<Long> createNIds(int size) {
    Set<Long> ids = new TreeSet<>();
    IntStream.range(0, size).asLongStream().forEach(ids::add);
    return ids;
  }

  private ExportApiConfig setUpSelection() {
    ExportApiConfig rc = new ExportApiConfig("html", "selection");
    return rc;
  }

  private void verifyJobNotLaunched()
      throws JobExecutionAlreadyRunningException,
          JobRestartException,
          JobInstanceAlreadyCompleteException,
          JobParametersInvalidException {
    verify(launcher, never()).run(Mockito.eq(job), Mockito.any(JobParameters.class));
  }

  private void verifyJobLaunched()
      throws JobExecutionAlreadyRunningException,
          JobRestartException,
          JobInstanceAlreadyCompleteException,
          JobParametersInvalidException {
    verify(launcher).run(Mockito.eq(job), Mockito.any(JobParameters.class));
  }

  private ExportApiConfig otherUserToHtml(User toExport) {
    ExportApiConfig rc = selfUserToHtml();
    rc.setId(toExport.getId());
    return rc;
  }

  private ExportApiConfig selfUserToHtml() {
    return new ExportApiConfig("html", "user");
  }
}
