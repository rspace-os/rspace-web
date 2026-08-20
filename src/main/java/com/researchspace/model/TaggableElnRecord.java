package com.researchspace.model;

/* 
 * To mark ELN records that allow tagging with metadata.
 */
public interface TaggableElnRecord {

    String getDocTag();
    
    void setDocTag(String docTag);

    String getTagMetaData();
    
    void setTagMetaData(String tagMetaData); 
    
}
