package com.researchspace.model.repository;

import java.util.Arrays;

public class RepositoryTestFactory {

  public static RepoDepositMeta createAValidRepoDepositMeta() {
    RepoDepositMeta meta = new RepoDepositMeta();
    meta.setTitle("title");
    meta.setDescription("desc");
    meta.setSubject("Chemistry");
    UserDepositorAdapter auth = new UserDepositorAdapter("email@x.com", "name");
    UserDepositorAdapter contact = new UserDepositorAdapter("email@x.com", "name");
    meta.setAuthors(Arrays.asList(new UserDepositorAdapter[] {auth}));
    meta.setContacts(Arrays.asList(new UserDepositorAdapter[] {contact}));
    meta.setLicenseName("GPL");
    meta.setLicenseUrl("http://gpl.license");
    return meta;
  }
}
