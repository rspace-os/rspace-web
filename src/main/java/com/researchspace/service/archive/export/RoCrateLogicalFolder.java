package com.researchspace.service.archive.export;

import edu.kit.datamanager.ro_crate.entities.data.DataSetEntity;
import java.util.Optional;

/**
 * A folder in RSpace. Not exported as a physical folder but recorded in the export in
 * foldertree.xml and in RoCrate as a DataSet.
 */
public class RoCrateLogicalFolder {

  private final DataSetEntity roCrateDataSet;
  private String rspaceGlobalID;
  private Long rspaceParentID;
  private Long rspaceID;

  public RoCrateLogicalFolder(DataSetEntity dsTop) {
    this.roCrateDataSet = dsTop;
  }

  public void addToHasPart(String id) {
    roCrateDataSet.addToHasPart(id);
  }

  public void isPartOf(String id) {
    roCrateDataSet.addProperty("isPartOf", id);
  }

  public String getRspaceGlobalID() {
    return rspaceGlobalID;
  }

  public void setRspaceGlobalID(String rspaceGlobalID) {
    this.rspaceGlobalID = rspaceGlobalID;
  }

  public Optional<Long> getRspaceParentID() {
    return Optional.ofNullable(this.rspaceParentID);
  }

  public void setRspaceParentID(Long rspaceParentID) {
    this.rspaceParentID = rspaceParentID;
  }

  public Long getRspaceID() {
    return rspaceID;
  }

  public void setRspaceID(Long rspaceID) {
    this.rspaceID = rspaceID;
  }

  public String getId() {
    return roCrateDataSet.getId();
  }
}
