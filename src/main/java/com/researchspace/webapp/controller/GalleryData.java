package com.researchspace.webapp.controller;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.record.RecordInformation;
import lombok.Data;

/** POJO class to hold data to populate Gallery view . Should be convertible into JSON */
@Data
public class GalleryData {

  public GalleryData() {}

  public GalleryData(boolean isOnRoot) {
    this.onRoot = isOnRoot;
  }

  private ISearchResults<RecordInformation> items;

  /**
   * Returns true if this Gallery data is showing content of the top-level folder for that
   * particular media type.
   *
   * @return
   */
  private boolean onRoot = true;

  /**
   * This is the id of the immediate parent folder that contains the gallery items listed in the
   * <code> items</code> field. <br>
   * If onRoot == true, this will be the same as grandparentId (since we don't want to navigate
   * higher up the folder tree, beyond the Images/ Documents etc folders).
   */
  private long parentId;

  /**
   * This is the id that will be used in the 'ParentFolder' link. I.e., it is the id of the <em>
   * grandparent </em> of the Gallery items
   *
   * @return
   */
  private long itemGrandparentId;
}
