package com.researchspace.service;

import com.researchspace.archive.ArchiveResult;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.repository.spi.IRepository;
import com.researchspace.repository.spi.RepositoryConfig;
import com.researchspace.repository.spi.RepositoryOperationResult;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.Async;

public interface IAsyncArchiveDepositor {
  @Async(value = "archiveTaskExecutor")
  Future<RepositoryOperationResult> depositDocument(
      App app,
      User subject,
      IRepository repository,
      RepoDepositConfig metadata,
      RepositoryConfig repoCfg,
      Future<EcatDocumentFile> document)
      throws InterruptedException, ExecutionException;

  @Async(value = "archiveTaskExecutor")
  Future<RepositoryOperationResult> depositArchive(
      App app,
      User subject,
      IRepository repository,
      RepoDepositConfig metadata,
      RepositoryConfig repoCfg,
      Future<ArchiveResult> archive)
      throws InterruptedException, ExecutionException;
}
