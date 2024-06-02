package com.researchspace.service.archive.export;

import com.researchspace.archive.ArchivalNfsFile;
import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ArchiveManifest;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.archive.ImmutableExportRecordList;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.archive.model.IArchiveExportConfig;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Record;
import com.researchspace.service.archive.ArchiveExportServiceManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.ui.velocity.VelocityEngineUtils;

@Service("htmlexportWriter")
public class HTMLArchiveExporter extends AbstractArchiveExporter
    implements ArchiveExportServiceManager {

  public static final String NFS_LINKS_HTML = "nfs-Links.html";
  static final String[] CSSFILES = new String[] {"document.css", "index.css"};

  @Autowired private ResourceToArchiveCopier resourceCopier;

  @Autowired private VelocityEngine velocity;

  @Override
  protected void preZip(
      ArchiveManifest manifest,
      ImmutableExportRecordList exportList,
      IArchiveExportConfig aconfig,
      ExportContext context,
      List<ExportedRecord> archived)
      throws Exception {
    copyIconsToArchiveResourceFile(context.getArchiveAssmblyFlder());
    // generate top level index
    File indexFile = new File(context.getArchiveAssmblyFlder(), "index.html");
    String archiveId = manifest.generateArchiveIdInManifest();
    context.setArchiveId(archiveId);
    writeManifestFile(manifest, context.getArchiveAssmblyFlder());
    List<ArchiveFolder> topLevelFolders = exportList.getTopLevelFolders();
    List<IndexItem> links = new ArrayList<IndexItem>();

    makeFolderItem(links, topLevelFolders);
    for (ExportedRecord exported : archived) {
      // just add top-level records to the index.
      if (!exportList.getArchiveFolder(exported.getParentFolderId()).isPresent()) {
        links.add(
            new IndexItem(
                getLinkToDocFile(exported), exported.getExportedRecord().getName(), true));
      }
    }
    generateAZIndex(context.getArchiveAssmblyFlder(), archived);
    Set<ArchivalNfsFile> allNfs =
        generateSummaryNfsExports(context.getArchiveAssmblyFlder(), archived);

    // we want escaped description for index file
    String escapedDescription = StringEscapeUtils.escapeHtml(aconfig.getDescription());
    manifest.addItem(ArchiveManifest.DESCRIPTION, escapedDescription);
    String html = processIndexTemplate(links, "Export Root", manifest, allNfs.size() > 0);
    FileUtils.write(indexFile, html, "UTF-8");

    processArchiveFolder(exportList, context.getArchiveAssmblyFlder(), archived, topLevelFolders);
  }

  private Set<ArchivalNfsFile> generateSummaryNfsExports(
      File archiveAssmblyFlder, List<ExportedRecord> archived) throws IOException {
    File nfsFile = new File(archiveAssmblyFlder, NFS_LINKS_HTML);
    // filter duplicates
    Set<ArchivalNfsFile> allNfs = getAllNfsLinksInExport(archived);
    String allNfsLinksString = processNfsExportsTemplate(allNfs);
    FileUtils.write(nfsFile, allNfsLinksString, "UTF-8");
    return allNfs;
  }

  private String processNfsExportsTemplate(Set<ArchivalNfsFile> allNfs) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("nfsList", allNfs);
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "nfsExports.vm", "UTF-8", velocityModel);
    return msg;
  }

  private void generateAZIndex(File archiveAssmblyFlder, List<ExportedRecord> archived)
      throws IOException {
    File azIndexFile = new File(archiveAssmblyFlder, "a-zIndex.html");
    List<IndexItem> allLinks = new ArrayList<IndexItem>();

    for (ExportedRecord exported : archived) {
      allLinks.add(
          new IndexItem(getLinkToDocFile(exported), exported.getExportedRecord().getName(), true));
    }
    Collections.sort(allLinks);
    String azIndexString = processAZIndexTemplate(allLinks);
    FileUtils.write(azIndexFile, azIndexString, "UTF-8");
  }

  private void copyIconsToArchiveResourceFile(File archiveAssmblyFlder) {
    String[] icons =
        new String[] {
          "/images/icons/folder.png",
          "/images/icons/document.png",
          "/images/icons/notebook.png",
          "/images/icons/video.png",
          "/images/icons/audioIcon.png"
        };
    for (String iconPath : icons) {
      resourceCopier.copyFromClassPathResourceToArchiveResources(iconPath, archiveAssmblyFlder);
    }

    for (String cssFile : CSSFILES) {
      resourceCopier.copyFromClassPathResourceToArchiveResources(
          "classpath:archiveResources/" + cssFile, archiveAssmblyFlder);
    }
  }

  /** Simple class to hold information for populating index HTML files */
  public static class IndexItem implements Comparable<IndexItem> {

    public String getUrl() {
      return url;
    }

    public String getName() {
      return name;
    }

    public boolean isRecord() {
      return record;
    }

    private String url, name;
    private boolean record = false;

    public boolean isNotebook() {
      return notebook;
    }

    protected boolean notebook;

    IndexItem(String url, String name, boolean isRecord) {
      super();
      this.url = url;
      this.name = name;
      this.record = isRecord;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((name == null) ? 0 : name.hashCode());
      result = prime * result + ((url == null) ? 0 : url.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      IndexItem other = (IndexItem) obj;
      if (name == null) {
        if (other.name != null) {
          return false;
        }
      } else if (!name.equals(other.name)) {
        return false;
      }
      if (url == null) {
        if (other.url != null) {
          return false;
        }
      } else if (!url.equals(other.url)) {
        return false;
      }
      return true;
    }

    @Override
    public int compareTo(IndexItem other) {
      int compare = this.name.toLowerCase().compareTo(other.name.toLowerCase());
      if (compare != 0) {
        return compare;
      }
      return this.url.compareTo(other.url);
    }
  }

  private void processArchiveFolder(
      ImmutableExportRecordList exportList,
      File archiveAssmblyFlder,
      List<ExportedRecord> archived,
      List<ArchiveFolder> archiveFolders)
      throws IOException {
    for (ArchiveFolder folder : archiveFolders) {
      File indexFile = createIndexFileForFolder(archiveAssmblyFlder, folder);
      List<IndexItem> links = new ArrayList<IndexItem>();

      // TODO need to recurse and format string in index file.
      List<ArchiveFolder> children = exportList.getChildren(folder.getId());
      makeFolderItem(links, children);
      for (ExportedRecord expDoc : archived) {
        Record doc = expDoc.getExportedRecord();
        // add link if this this document belongs to the folder.
        if (folder.getId().equals(expDoc.getParentFolderId())) {
          links.add(new IndexItem(getLinkToDocFile(expDoc), doc.getName(), true));
        }
      }

      String html = processIndexTemplate(links, folder.getName(), null, false);
      FileUtils.write(indexFile, html, "UTF-8");
      processArchiveFolder(exportList, archiveAssmblyFlder, archived, children);
    }
  }

  private void makeFolderItem(List<IndexItem> links, List<ArchiveFolder> children) {
    for (ArchiveFolder child : children) {
      IndexItem item =
          new IndexItem(ArchiveUtils.getFolderIndexName(child), child.getName(), false);
      if (child.getType().contains(RecordType.NOTEBOOK.name())) {
        item.notebook = true;
      }
      links.add(item);
    }
  }

  private String processIndexTemplate(
      List<IndexItem> indexItems,
      String pageTitle,
      ArchiveManifest manifest,
      boolean includeLinkToNfsEports) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("indexItems", indexItems);
    velocityModel.put("folderName", pageTitle);
    velocityModel.put("manifest", manifest);
    velocityModel.put("includeLinkToNfsEports", includeLinkToNfsEports);
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "htmlIndex.vm", "UTF-8", velocityModel);
    return msg;
  }

  private String processAZIndexTemplate(List<IndexItem> indexItems) {
    Map<String, Object> velocityModel = new HashMap<String, Object>();
    velocityModel.put("indexItems", indexItems);
    String msg =
        VelocityEngineUtils.mergeTemplateIntoString(
            velocity, "a-zIndex.vm", "UTF-8", velocityModel);
    return msg;
  }

  private String getLinkToDocFile(ExportedRecord expDoc) {
    return expDoc.getRecordFolder().getName() + File.separator + expDoc.getOutFile().getName();
  }

  private File createIndexFileForFolder(File archiveAssmblyFlder, ArchiveFolder root) {
    File file = new File(archiveAssmblyFlder, ArchiveUtils.getFolderIndexName(root));
    return file;
  }

  @Override
  public boolean isArchiveType(String archiveType) {
    return ArchiveExportConfig.HTML.equals(archiveType);
  }
}
