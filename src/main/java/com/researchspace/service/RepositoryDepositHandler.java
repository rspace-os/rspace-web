package com.researchspace.service;

import com.researchspace.archive.ArchiveResult;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.apps.AppConfigElementSet;
import com.researchspace.model.repository.RepoDepositConfig;
import com.researchspace.model.repository.RepoUIConfigInfo;
import com.researchspace.repository.spi.RepositoryOperationResult;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Performs a repository deposit operation */
public interface RepositoryDepositHandler {
  /**
   * Sends a validated Repository configuration to a repository
   *
   * @param archiveConfig The RepoDepositConfig of the metadata to send.
   * @param cfg The AppConfig for the repository
   * @return
   * @throws ExecutionException
   * @throws @throws MalformedURLException
   * @throws IOException
   * @throws InterruptedException
   */
  RepositoryOperationResult sendDocumentToRepository(
      RepoDepositConfig archiveConfig,
      Optional<AppConfigElementSet> cfg,
      App app,
      Future<EcatDocumentFile> document)
      throws IOException, ExecutionException, InterruptedException;

  /**
   * Sends a validated Repository configuration to a repository
   *
   * @param archiveConfig The RepoDepositConfig of the metadata to send.
   * @param cfg The AppConfigElementSet for the repository
   * @return
   * @throws ExecutionException
   * @throws @throws MalformedURLException
   * @throws IOException
   * @throws InterruptedException
   */
  RepositoryOperationResult sendArchiveToRepository(
      RepoDepositConfig archiveConfig,
      Optional<AppConfigElementSet> cfg,
      App app,
      Future<ArchiveResult> archive)
      throws IOException, ExecutionException, InterruptedException;

  /**
   * Tests connection to repository
   *
   * @param appCfgElementSet
   * @return
   * @throws MalformedURLException
   */
  RepositoryOperationResult testDataverseConnection(AppConfigElementSet appCfgElementSet)
      throws MalformedURLException;

  /**
   * Retrieves information from the repository implementation on how the UI should be configured.
   *
   * @param appCfg
   * @return
   */
  RepoUIConfigInfo getDataverseRepoUIConfigInfo(AppConfigElementSet appCfg, User user)
      throws MalformedURLException;

  RepoUIConfigInfo getFigshareRepoUIConfigInfo(User user) throws MalformedURLException;

  RepoUIConfigInfo getDryadRepoUIConfigInfo(User user) throws MalformedURLException;

  RepoUIConfigInfo getZenodoRepoUIConfigInfo(User user) throws MalformedURLException;
}
