package com.researchspace.document.importer;

import static org.apache.commons.io.FilenameUtils.getBaseName;
import static org.apache.commons.lang.StringUtils.join;
import static org.apache.commons.lang.StringUtils.replaceChars;

import com.researchspace.evernote.EnExport;
import com.researchspace.evernote.EvernoteParser;
import com.researchspace.evernote.FileAndOriginalName;
import com.researchspace.evernote.LinkUpdater;
import com.researchspace.evernote.Note;
import com.researchspace.evernote.ReplaceEnMediaWithSimpleATags;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.FieldManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RecordManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.util.Map;
import javax.xml.parsers.ParserConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.xml.sax.SAXException;

@Slf4j
public class EvernoteEnexImporter implements ExternalFileImporter {

  private @Autowired RecordManager recMgr;
  private @Autowired FolderManager fMgr;
  private @Autowired MediaManager mediaMgr;
  private @Autowired RichTextUpdater richTextUpdater;
  private @Autowired FieldManager fieldMgr;

  @Override
  public BaseRecord create(
      InputStream srcFile, User user, Folder targetFolder, Folder imageFolder, String originalName)
      throws IOException {

    String origDocNameNoSpaces = replaceChars(getBaseName(originalName), " ", "");
    File tempFolder =
        Files.createTempDirectory(origDocNameNoSpaces, new FileAttribute[] {}).toFile();
    File tempInputFile = new File(tempFolder, originalName);

    try (FileOutputStream tempFos = new FileOutputStream(tempInputFile)) {
      IOUtils.copy(srcFile, tempFos);
    }

    EvernoteParser parser = new EvernoteParser();
    EnExport evernoteXML;
    try {
      evernoteXML = parser.parse(tempInputFile);
      Map<String, FileAndOriginalName> hashToFile = evernoteXML.writeResourcesToFile(tempFolder);
      Folder folder = fMgr.createNewFolder(targetFolder.getId(), getBaseName(originalName), user);
      for (Note note : evernoteXML.getNotes()) {

        String content = note.getContent();
        LinkUpdater updater = new LinkUpdater(new ReplaceEnMediaWithSimpleATags());
        String updatedContentHTML = updater.update(hashToFile, content);
        File htmlFile =
            new File(tempFolder, StringUtils.replaceChars(note.getTitle(), " ", "") + ".html");

        // now we have HTML file with local relative links to the files, which can be
        // any type
        FileUtils.write(htmlFile, updatedContentHTML, "UTF-8");

        Document jsoupdoc = Jsoup.parse(htmlFile, null, "");
        log.info("Creating document  for note: {}", note.getTitle());
        StructuredDocument strucDoc = recMgr.createBasicDocument(folder.getId(), user);
        strucDoc.setName(note.getTitle());
        strucDoc.setDocTag(join(note.getTags(), ","));
        strucDoc.setTagMetaData(join(note.getTags(), ","));
        strucDoc = recMgr.save(strucDoc, user).asStrucDoc();
        updateFieldContent(user, tempFolder, jsoupdoc, imageFolder, strucDoc);
      }

      return folder;
    } catch (FileNotFoundException | ParserConfigurationException | SAXException e) {
      throw new IOException(e);
    }
  }

  private StructuredDocument updateFieldContent(
      User creator,
      File contentFolder,
      Document jsoupDoc,
      Folder imageFolder,
      StructuredDocument strucDoc)
      throws FileNotFoundException, IOException {

    Field textField = strucDoc.getFields().get(0);
    Elements aResourceLinks = jsoupDoc.select("a[data-en='true']");

    for (Element resourceEl : aResourceLinks) {
      String src = resourceEl.attr("href");

      File imageFile = new File(src);
      try (FileInputStream fis = new FileInputStream(imageFile)) {
        String displayName = FilenameUtils.getName(src);
        EcatMediaFile ecatMedia =
            mediaMgr.saveMediaFile(
                fis, null, displayName, displayName, null, imageFolder, null, creator);
        replaceCurrImageTagWithRSpaceImgTag(textField, resourceEl, ecatMedia);
        fieldMgr.addMediaFileLink(ecatMedia.getId(), creator, textField.getId(), true);
      }
    }
    textField.setFieldData(jsoupDoc.body().html());
    // changes proagated to fields in single transaction for audit trail
    return recMgr.save(strucDoc, creator).asStrucDoc();
  }

  private void replaceCurrImageTagWithRSpaceImgTag(
      Field textField, Element aResourceTag, EcatMediaFile ecatMedia) {
    String replacementHtml = "";
    Element replaceElement = null;
    if (ecatMedia.isImage()) {
      replacementHtml =
          richTextUpdater.generateRawImageElement((EcatImage) ecatMedia, textField.getId() + "");
      Document docx = Jsoup.parse(replacementHtml, "");
      replaceElement = (docx.getElementsByTag("img").first());
    } else if (ecatMedia.isEcatDocument()) {
      replacementHtml = richTextUpdater.generateURLString((EcatDocumentFile) ecatMedia);
      replaceElement = generateReplaceElement(replacementHtml);
    } else if (ecatMedia.isAudio()) {
      replacementHtml = richTextUpdater.generateURLString((EcatAudio) ecatMedia, textField.getId());
      replaceElement = generateReplaceElement(replacementHtml);
    } else if (ecatMedia.isVideo()) {
      replacementHtml = richTextUpdater.generateURLString((EcatVideo) ecatMedia, textField.getId());
      replaceElement = generateReplaceElement(replacementHtml);
    } else if (ecatMedia.isChemistryFile()) {
      replacementHtml =
          richTextUpdater.generateURLStringForEcatChemistryFile(
              1L, (EcatChemistryFile) ecatMedia, textField.getId(), 50, 50);
      replaceElement = generateReplaceElementChemFile(replacementHtml);
    }
    if (replaceElement != null) {
      aResourceTag.replaceWith(replaceElement);
    }
  }

  private Element generateReplaceElement(String replacementHtml) {
    Document docx = Jsoup.parse(replacementHtml, "");
    Element replaceElement = attachmentDiv(docx);
    return replaceElement;
  }

  private Element attachmentDiv(Document docx) {
    Elements attachmentDiv = docx.getElementsByClass("attachmentDiv");
    return attachmentDiv.first();
  }

  private Element generateReplaceElementChemFile(String replacementHtml) {
    Document docx = Jsoup.parse(replacementHtml, "");
    Element replaceElement = attachmentChemDiv(docx);
    return replaceElement;
  }

  private Element attachmentChemDiv(Document docx) {
    Elements img = docx.getElementsByClass("chem");
    return img.first();
  }

  @Override
  public BaseRecord replace(InputStream wordFile, User user, Long toReplaceId, String originalName)
      throws IOException, DocumentAlreadyEditedException {
    throw new UnsupportedOperationException("Replace not supported");
  }
}
