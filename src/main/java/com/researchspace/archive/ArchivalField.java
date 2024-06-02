package com.researchspace.archive;

import com.researchspace.archive.elninventory.ArchivalListOfMaterials;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

/** <XML representation of a {@link Field} */
@XmlType(
    propOrder = {
      "fieldId",
      "code",
      "fieldName",
      "fieldType",
      "lastModifiedDate",
      "fieldData",
      "comments",
      "imgMeta",
      "audioMeta",
      "videoMeta",
      "chemFileMeta",
      "chemElementMeta",
      "mathMeta",
      "annotMeta",
      "attachMeta",
      "linkMeta",
      "sktchMeta",
      "listsOfMaterials",
      "nfsElements"
    })
public class ArchivalField {
  long fieldId;
  String code;
  String fieldName;
  String fieldType;
  String lastModifiedDate;
  String fieldData;
  String fieldDataPrintable;
  List<ArchiveComment> comments = new ArrayList<>();
  List<ArchivalGalleryMetadata> imgMeta = new ArrayList<>();
  List<ArchivalGalleryMetadata> audioMeta = new ArrayList<>();
  List<ArchivalGalleryMetadata> videoMeta = new ArrayList<>();
  List<ArchivalGalleryMetadata> chemFileMeta = new ArrayList<>();
  List<ArchivalGalleryMetadata> chemElementMeta = new ArrayList<>();
  List<ArchivalGalleryMetadata> annotMeta = new ArrayList<>();
  List<ArchivalGalleryMetadata> attachMeta = new ArrayList<>();
  List<ArchivalGalleryMetadata> linkMeta = new ArrayList<>();
  List<ArchivalGalleryMetadata> sktchMeta = new ArrayList<>();
  List<ArchivalGalleryMetadata> mathMeta = new ArrayList<>();
  List<ArchivalNfsFile> nfsElements = new ArrayList<>();
  List<ArchivalListOfMaterials> listsOfMaterials = new ArrayList<>();

  @XmlAttribute(name = "id", required = true)
  public long getFieldId() {
    return fieldId;
  }

  @XmlElement
  public String getCode() {
    return code;
  }

  @XmlElement(name = "fieldName")
  public String getFieldName() {
    return fieldName;
  }

  @XmlElement(name = "fieldType")
  public String getFieldType() {
    return fieldType;
  }

  @XmlElement
  public String getLastModifiedDate() {
    return lastModifiedDate;
  }

  @XmlElement
  public String getFieldData() {
    return fieldData;
  }

  @XmlElementWrapper(name = "commentList")
  @XmlElement(name = "comment")
  public List<ArchiveComment> getComments() {
    return comments;
  }

  @XmlElementWrapper(name = "imageList")
  @XmlElement(name = "image-Info")
  public List<ArchivalGalleryMetadata> getImgMeta() {
    return imgMeta;
  }

  @XmlElementWrapper(name = "audioList")
  @XmlElement(name = "audio-Info")
  public List<ArchivalGalleryMetadata> getAudioMeta() {
    return audioMeta;
  }

  @XmlElementWrapper(name = "videoList")
  @XmlElement(name = "video-info")
  public List<ArchivalGalleryMetadata> getVideoMeta() {
    return videoMeta;
  }

  @XmlElementWrapper(name = "chemList")
  @XmlElement(name = "chem-info")
  public List<ArchivalGalleryMetadata> getChemFileMeta() {
    return chemFileMeta;
  }

  @XmlElementWrapper(name = "chemdooleImageList")
  @XmlElement(name = "chemdoodle-info")
  public List<ArchivalGalleryMetadata> getChemElementMeta() {
    return chemElementMeta;
  }

  @XmlElementWrapper(name = "mathList")
  @XmlElement(name = "math-info")
  public List<ArchivalGalleryMetadata> getMathMeta() {
    return mathMeta;
  }

  @XmlElementWrapper(name = "ImageAnnotationList")
  @XmlElement(name = "annotation-info")
  public List<ArchivalGalleryMetadata> getAnnotMeta() {
    return annotMeta;
  }

  @XmlElementWrapper(name = "attachList")
  @XmlElement(name = "attach-info")
  public List<ArchivalGalleryMetadata> getAttachMeta() {
    return attachMeta;
  }

  @XmlElementWrapper(name = "linkRecordList")
  @XmlElement(name = "link-info")
  public List<ArchivalGalleryMetadata> getLinkMeta() {
    return linkMeta;
  }

  @XmlElementWrapper(name = "sketchImageList")
  @XmlElement(name = "sketch-info")
  public List<ArchivalGalleryMetadata> getSktchMeta() {
    return sktchMeta;
  }

  @XmlElementWrapper(name = "nfsElementList")
  @XmlElement(name = "nfsElement")
  public List<ArchivalNfsFile> getNfsElements() {
    return nfsElements;
  }

  @XmlElementWrapper(name = "listOfMaterialsList")
  @XmlElement(name = "listOfMaterials")
  public List<ArchivalListOfMaterials> getListsOfMaterials() {
    return listsOfMaterials;
  }

  @XmlTransient // used for html output only
  public String getFieldDataPrintable() {
    return fieldDataPrintable;
  }

  @XmlTransient
  public List<ArchivalGalleryMetadata> getAllGalleryMetaData() {
    List<ArchivalGalleryMetadata> archivalGalleryMetadata = new ArrayList<>();
    archivalGalleryMetadata.addAll(getSktchMeta());
    archivalGalleryMetadata.addAll(getLinkMeta());
    archivalGalleryMetadata.addAll(getAudioMeta());
    archivalGalleryMetadata.addAll(getAnnotMeta());
    archivalGalleryMetadata.addAll(getChemElementMeta());
    archivalGalleryMetadata.addAll(getChemFileMeta());
    archivalGalleryMetadata.addAll(getMathMeta());
    archivalGalleryMetadata.addAll(getAttachMeta());
    archivalGalleryMetadata.addAll(getVideoMeta());
    archivalGalleryMetadata.addAll(getImgMeta());
    return archivalGalleryMetadata;
  }

  public void setFieldId(long id) {
    fieldId = id;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  public void setFieldType(String type) {
    fieldType = type;
  }

  public void setLastModifiedDate(String date) {
    lastModifiedDate = date;
  }

  public void setFieldData(String rtfdata) {
    fieldData = rtfdata;
    // choice field data is manipulated elsewhere for html export
    if (fieldType != null && !fieldType.equals("CHOICE")) {
      setFieldDataPrintable(rtfdata);
    }
  }

  public void setFieldDataPrintable(String fieldDataPrintable) {
    this.fieldDataPrintable = fieldDataPrintable;
  }

  public void addArchivalComment(ArchiveComment acm) {
    comments.add(acm);
  }

  public void addArchivalImageMetadata(ArchivalGalleryMetadata meta) {
    imgMeta.add(meta);
  }

  public void addArchivalAudioMetadata(ArchivalGalleryMetadata meta) {
    audioMeta.add(meta);
  }

  public void addArchivalVideoMetadata(ArchivalGalleryMetadata meta) {
    videoMeta.add(meta);
  }

  public void addArchivalChemFileMetadata(ArchivalGalleryMetadata meta) {
    chemFileMeta.add(meta);
  }

  public void addArchivalChemdooleMetadata(ArchivalGalleryMetadata meta) {
    chemElementMeta.add(meta);
  }

  public void addArchivalMathMetadata(ArchivalGalleryMetadata math) {
    mathMeta.add(math);
  }

  public void addArchivalAnnotationMetadata(ArchivalGalleryMetadata meta) {
    annotMeta.add(meta);
  }

  public void addArchivalAttachMetadata(ArchivalGalleryMetadata meta) {
    attachMeta.add(meta);
  }

  public void addArchivalLinkMetadata(ArchivalGalleryMetadata meta) {
    linkMeta.add(meta);
  }

  public void addArchivalSketchMetadata(ArchivalGalleryMetadata meta) {
    sktchMeta.add(meta);
  }

  public void addArchivalNfs(ArchivalNfsFile nfs) {
    nfsElements.add(nfs);
  }
}
