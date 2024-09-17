package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.FileProperty;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Facade object dealing with repeating need of retrieve inventory items by inventory-related code.
 */
@Lazy
@Component
public class InventoryRecordRetriever {

  // lazy-loaded as a quick fix for non-obvious cycle problem with spring context loading
  private @Autowired @Lazy ContainerDao containerDao;
  private @Autowired SampleDao sampleDao;
  private @Autowired SubSampleDao subSampleDao;

  /**
   * Returns inventory record of given type.
   *
   * @throws NotFoundException if record doesn't exits.
   */
  public InventoryRecord getInvRecForIdAndType(Long id, ApiInventoryRecordType type) {
    switch (type) {
      case SAMPLE:
        return getSampleIfExists(id);
      case SUBSAMPLE:
        return getSubSampleIfExists(id);
      case CONTAINER:
        return getContainerIfExists(id);
      default:
        throw new IllegalArgumentException("Unsupported type: " + type);
    }
  }

  /**
   * Returns inventory record matching global id.
   *
   * @return found record, or null if record doesn't exist
   */
  public InventoryRecord getInvRecordByGlobalId(GlobalIdentifier globalId) {
    if (globalId == null) {
      return null;
    }
    switch (globalId.getPrefix()) {
      case SA:
      case IT:
        return getSampleIfExists(globalId.getDbId());
      case SS:
        return getSubSampleIfExists(globalId.getDbId());
      case IC:
        return getContainerById(globalId.getDbId());
      default:
        return null;
    }
  }

  /**
   * Looks for a container with given id.
   *
   * @return a Container with given id, or null if doesn't exist
   */
  public Container getContainerById(Long id) {
    Container result = null;
    try {
      result = getContainerIfExists(id);
    } catch (NotFoundException nfe) {
      ;
    }
    return result;
  }

  /**
   * Looks for a sample (or sample template) with given id.
   *
   * @return a Sample with given id, or null if doesn't exist
   */
  public Sample getSampleById(Long id) {
    Sample result = null;
    try {
      result = getSampleIfExists(id);
    } catch (NotFoundException nfe) {
      ;
    }
    return result;
  }

  /** Looks for a subsample with given id, visible to the user. */
  public SubSample getSubSampleById(Long id) {
    SubSample result = null;
    try {
      result = getSubSampleIfExists(id);
    } catch (NotFoundException nfe) {
      ;
    }
    return result;
  }

  /**
   * Tries retrieving container with given id.
   *
   * @throws NotFoundException if there is no container with given id
   */
  public Container getContainerIfExists(Long id) {
    boolean exists = containerDao.exists(id);
    if (!exists) {
      throw new NotFoundException("No container with id: " + id);
    }
    return containerDao.get(id);
  }

  /**
   * Tries retrieving sample with given id.
   *
   * @throws NotFoundException if there is no sample with given id
   */
  public Sample getSampleIfExists(Long id) {
    boolean exists = sampleDao.exists(id);
    if (!exists) {
      throw new NotFoundException("No sample with id: " + id);
    }
    return sampleDao.get(id);
  }

  /**
   * Tries retrieving subsample with given id.
   *
   * @throws NotFoundException if there is no subsample with given id
   */
  public SubSample getSubSampleIfExists(Long id) {
    boolean exists = subSampleDao.exists(id);
    if (!exists) {
      throw new NotFoundException("No subsample with id: " + id);
    }
    return subSampleDao.get(id);
  }

  /** Get container for given location id. */
  public Container getContainerByLocationId(Long id) {
    return containerDao.getContainerByLocationId(id);
  }

  /** Get workbench of given user. */
  public Container getWorkbenchForUser(User user) {
    return containerDao.getWorkbenchForUser(user);
  }

  public List<InventoryRecord> recordsUsingImageFile(FileProperty fileProperty) {
    List<InventoryRecord> ids = new ArrayList<>();
    ids.addAll(containerDao.getAllUsingImage(fileProperty));
    ids.addAll(sampleDao.getAllUsingImage(fileProperty));
    ids.addAll(subSampleDao.getAllUsingImage(fileProperty));
    return ids;
  }
}
