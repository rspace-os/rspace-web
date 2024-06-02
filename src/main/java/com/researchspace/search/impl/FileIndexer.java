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
import org.apache.commons.io.FilenameUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
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

    Analyzer analyzer = new StandardAnalyzer();
    IndexWriterConfig writerConfig = new IndexWriterConfig(analyzer);
    writerConfig.setWriteLockTimeout(MAX_WRITELOCK_WAIT);
    if (deleteIndex) {
      writerConfig.setOpenMode(OpenMode.CREATE);
      writer = new IndexWriter(dir, writerConfig);
      writer.deleteAll();
      writer.commit();
    } else {
      writerConfig.setOpenMode(OpenMode.APPEND);
      writer = new IndexWriter(dir, writerConfig);
    }

    initialised = true;
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
    return writer.numDocs();
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
    Tika tika = new Tika();
    tika.setMaxStringLength(1_000_000);
    if (suffix.equals("pdf")) {
      PDDocument pdoc = PDDocument.load(fx);
      String content = new PDFTextStripper().getText(pdoc);
      contentFld = new Field(FIELD_CONTENTS, content, Field.Store.NO, Field.Index.ANALYZED);
      pdoc.close();
    } else {
      try {
        String wordText = tika.parseToString(fx);
        log.debug("Parsed string is {}", wordText);
        contentFld = new Field(FIELD_CONTENTS, wordText, Field.Store.NO, Field.Index.ANALYZED);
      } catch (TikaException e) {
        log.warn("Error reading file {} - msg: {}", fx.getName(), e.getMessage());
        contentFld =
            new Field(
                FIELD_CONTENTS,
                new InputStreamReader(new FileInputStream(fx), Charset.forName("UTF-8")));
      }
    }

    Field nameFld = new Field(FIELD_NAME, fx.getName(), Field.Store.YES, Field.Index.NOT_ANALYZED);
    Field pathFld =
        new Field(FIELD_PATH, fx.getAbsolutePath(), Field.Store.YES, Field.Index.NOT_ANALYZED);
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
