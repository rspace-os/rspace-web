package com.researchspace.search.impl;

import com.axiope.search.IFileIndexer;
import com.researchspace.core.util.FolderOperator;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexFormatTooNewException;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;

/** Index Files in file store for search */
@Slf4j
public class FileIndexer extends AttachmentSearchBase implements IFileIndexer {

  private static final long MAX_WRITELOCK_WAIT = 5000L;

  public static final String FIELD_PATH = "fullpath";

  public static final String FIELD_NAME = "filename";

  public static final String FIELD_CONTENTS = "contents";

  @SuppressWarnings("unused")
  private static final String[] ENGLISH_STOP_WORDS = {
    "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into", "is", "it",
    "no", "not", "of", "on", "or", "such", "that", "the", "their", "then", "there", "these", "they",
    "this", "to", "was", "will", "with"
  };

  private static final String[] SUFFIXES = {
    ".odp", ".odt", ".ods", ".pdf", ".doc", ".txt", ".xml", ".html", ".rtf", ".xls", ".ppt",
    ".pptx", ".docx", ".xlsx", ".csv"
  };

  private IndexWriter writer;
  private FileFilter filter;
  private Directory dir;
  private boolean initialised;

  public FileIndexer() {
    filter = new IndexFileFilter();
  }

  // called outside of constructor so that properties from external property
  // files will have been set
  public void init(boolean deleteIndex) throws IOException {
    if (initialised) {
      return;
    }
    setIndexFolder();
    File folder = getIndexFolder();
    dir = FSDirectory.open(folder.toPath());

    if (deleteIndex) {
      writer = openWriter(folder, OpenMode.CREATE);
      writer.deleteAll();
      writer.commit();
    } else {
      // CREATE_OR_APPEND (not APPEND) so a missing/empty index folder is created on the
      // fly rather than throwing IndexNotFoundException. APPEND requires a pre-existing
      // committed index, which breaks on a clean environment (e.g. fresh CI runner) where
      // onInitialAppDeployment hasn't created one yet.
      writer = openWriter(folder, OpenMode.CREATE_OR_APPEND);
    }

    initialised = true;
  }

  // An index written by an incompatible Lucene version cannot be opened at all, even
  // just to delete it (IndexWriter reads the existing segments file in every OpenMode),
  // so recover by wiping the folder and starting an empty index. The index is derived
  // data: a full reindex (rs.indexOnstartup=true) rebuilds it from the file store.
  private IndexWriter openWriter(File folder, OpenMode openMode) throws IOException {
    try {
      return new IndexWriter(dir, newWriterConfig(openMode));
    } catch (IndexFormatTooOldException | IndexFormatTooNewException e) {
      log.warn(
          "Deleting attachment search index at {}, which was written by an incompatible"
              + " Lucene version; set rs.indexOnstartup=true to rebuild it. Cause: {}",
          folder.getAbsolutePath(),
          e.getMessage());
      dir.close();
      FileUtils.cleanDirectory(folder);
      dir = FSDirectory.open(folder.toPath());
      return new IndexWriter(dir, newWriterConfig(openMode));
    }
  }

  private IndexWriterConfig newWriterConfig(OpenMode openMode) {
    IndexWriterConfig writerConfig = new IndexWriterConfig(new StandardAnalyzer());
    writerConfig.setOpenMode(openMode);
    return writerConfig;
  }

  @Override
  public void init() throws IOException {
    init(false);
  }

  public IndexWriter getWriter() throws IOException {
    if (!initialised) {
      init();
    }
    return writer;
  }

  /*
   * Forcibly removes lock, for use in testing (non-Javadoc)
   *
   * @see com.axiope.search.IFileIndexer#close()
   */
  public void close() throws IOException {
    try {
      if (writer != null) {
        writer.close();
      }
    } finally {
      initialised = false;
      writer = null;
    }
  }

  public void commit() throws IOException {
    if (!initialised) {
      init();
    }
    writer.commit();
  }

  public void deleteAll() throws IOException {
    if (!initialised) {
      init();
    }
    writer.deleteAll();
    commit();
  }

  public int indexFileStore(boolean failFast) throws IOException {
    FolderOperator fop = new FolderOperator();
    File dataFolder = fop.getBaseDir();
    return indexFolder(dataFolder, failFast);
  }

  public int indexFolder(File folder, boolean failFast) throws IOException {
    List<File> fileList = new ArrayList<File>();
    extractFiles(folder, fileList);
    int indexed = 0;
    for (File fx : fileList) {
      if (accept(fx)) {
        try {
          indexFile(fx);
        } catch (Exception e) {
          log.warn("Could not index file {} - {}", fx.getAbsolutePath(), e.getMessage());
          if (e instanceof IOException) {
            if (!failFast || "Error: End-of-File, expected line".equals(e.getMessage())) {
              // most probably harmless PDFBox error, just skip the file
            } else {
              throw e; // rethrow IO exception which might be due to full index or file problem
            }
          }
        }
      }
      indexed++;
      if ((indexed % 100) == 0) {
        log.info("{} files indexed, {} to go.", indexed, (fileList.size() - indexed));
      }
    }
    return writer.getDocStats().numDocs;
  }

  public void indexFile(File file) throws IOException {
    if (!initialised) {
      init();
    }
    if (!filter.accept(file)) {
      return;
    }

    Document dc = getIndexDocument(file);
    writer.addDocument(dc);
    commit();
  }

  @Override
  public boolean accept(File file) {
    return filter.accept(file);
  }

  // ------------ support method --------------------

  Document getIndexDocument(File fx) throws IOException {

    Field contentFld = null;
    String suffix = FilenameUtils.getExtension(fx.getName()).toLowerCase();
    if (suffix.equals("pdf")) {
      PDDocument pdoc = Loader.loadPDF(fx);
      String content = new PDFTextStripper().getText(pdoc);
      contentFld = new TextField(FIELD_CONTENTS, content, Field.Store.NO);
      pdoc.close();
    } else {
      try {
        Tika tika = new Tika();
        tika.setMaxStringLength(1_000_000);
        String wordText = tika.parseToString(fx);
        log.debug("Parsed string is {}", wordText);
        contentFld = new TextField(FIELD_CONTENTS, wordText, Field.Store.NO);
      } catch (TikaException e) {
        log.warn("Error reading file {} - msg: {}", fx.getName(), e.getMessage());
        contentFld =
            new TextField(
                FIELD_CONTENTS,
                new InputStreamReader(new FileInputStream(fx), Charset.forName("UTF-8")));
      }
    }

    Field nameFld = new StringField(FIELD_NAME, fx.getName(), Field.Store.YES);
    Field pathFld = new StringField(FIELD_PATH, fx.getAbsolutePath(), Field.Store.YES);
    Document dc = new Document();
    dc.add(contentFld);
    dc.add(nameFld);
    dc.add(pathFld);
    return dc;
  }

  protected void extractFiles(File folder, List<File> fileList) {
    IndexableFileLocator fileLocator = new IndexableFileLocator(folder);
    fileLocator.doExtractFiles(folder, fileList);
  }

  static class IndexFileFilter implements FileFilter {

    @Override
    public boolean accept(File fx) {
      boolean fg = false;
      for (int i = 0; i < SUFFIXES.length; i++) {
        if (fx.getName().toLowerCase().endsWith(SUFFIXES[i])) {
          fg = true;
          break;
        }
      }
      return fg;
    }
  }

  @Override
  public boolean isInitialised() {
    return initialised;
  }
}
