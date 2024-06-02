package com.researchspace.service.archive.export;

import static com.researchspace.core.util.imageutils.ImageUtils.TIFF_EXTENSIONS;

import com.researchspace.archive.ArchivalGalleryMetadata;
import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.model.record.Record;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.tools.generic.DateTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.velocity.VelocityEngineUtils;

public class HTMLWriter implements ExportObjectWriter {

  @Autowired private VelocityEngine velocity;
  Logger logger = LoggerFactory.getLogger(HTMLWriter.class);

  @Override
  public void writeExportObject(File outputFile, ExportedRecord exported) {
    String msg = null;
    if (exported.getArchivedRecord() != null) {
      msg = createDocumentHTML(exported);
    } else if (exported.getArchivedMedia() != null) {
      msg = createMediaHTML(exported);
    }
    if (msg == null) {
      throw new IllegalStateException();
    }
    try {
      FileUtils.writeStringToFile(outputFile, msg, "UTF-8");
    } catch (IOException e) {
      logger.error("Error writing XML files: " + e.getMessage());
      if (e.getCause() != null) {
        logger.error("Underlying cause: " + e.getCause().getMessage());
      }
      throw new ExportFailureException(e.getMessage());
    }
  }

  private String createMediaHTML(ExportedRecord exported) {
    ArchivalGalleryMetadata mediadata = exported.getArchivedMedia();
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("document", mediadata);

    if (exported.getExportedRecord().isImage()) {
      velocityModel.put("isImage", true);
    }

    velocityModel.put("date", new DateTool());
    addParentLink(velocityModel, exported.getExportedRecord());
    addTiffLinks(exported, velocityModel);
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "mediaView.vm", "UTF-8", velocityModel);
    return msg;
  }

  private String createDocumentHTML(ExportedRecord exported) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("document", exported.getArchivedRecord());
    velocityModel.put("date", new DateTool());
    // get all nfsFiles in the document from all fields, to add as footer: RSPAC-1354
    List<ArchivalNfsFile> nfsFiles = new ArrayList<>();
    if (exported.getArchivedRecord() != null) {
      nfsFiles = exported.getArchivedRecord().getAllDistinctNfsElements();
    }
    velocityModel.put("nfsFiles", nfsFiles);

    Record exportedRcd = exported.getExportedRecord();
    addParentLink(velocityModel, exportedRcd);

    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "documentView.vm", "UTF-8", velocityModel);
    return msg;
  }

  // adds info about original tiff files if these are in media file's export folder.
  private void addTiffLinks(ExportedRecord exported, Map<String, Object> velocityModel) {
    File outFolder = exported.getRecordFolder();
    Collection<File> files =
        FileUtils.listFiles(outFolder, TIFF_EXTENSIONS.toArray(new String[0]), false);
    velocityModel.put("tiffs", files);
  }

  private void addParentLink(Map<String, Object> velocityModel, Record exportedRcd) {
    String parentLinkFile = ArchiveUtils.getFolderIndexName(exportedRcd.getOwnerParent().get());
    velocityModel.put("parentRef", parentLinkFile);
  }
}
