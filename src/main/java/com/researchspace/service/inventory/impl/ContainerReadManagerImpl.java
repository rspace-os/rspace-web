package com.researchspace.service.inventory.impl;

import com.researchspace.dao.ContainerDao;
import com.researchspace.model.User;
import com.researchspace.model.collection.*;
import com.researchspace.model.inventory.Container;
import com.researchspace.service.inventory.ContainerReadAccess;
import com.researchspace.service.inventory.ContainerReadManager;
import org.springframework.stereotype.Service;

@Service
public class ContainerReadManagerImpl implements ContainerReadManager {
  private final ContainerDao dao;
  private final ContainerReadAccess access;

  public ContainerReadManagerImpl(ContainerDao dao, ContainerReadAccess access) {
    this.dao = dao;
    this.access = access;
  }

  @Override
  public ResourcePage<Container> find(ResourceRequest request, User actor) {
    return dao.getReadableResources(
        request,
        access.check(new AccessContext(actor, AccessContext.Operation.READ, "containers")));
  }

  @Override
  public long count(ResourceRequest request, User actor) {
    return dao.countReadableResources(
        request,
        access.check(new AccessContext(actor, AccessContext.Operation.READ, "containers")));
  }
}
