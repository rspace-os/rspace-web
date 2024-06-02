package com.researchspace.document.importer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.Validate;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/** Stateful content provider */
public class HTMLContentProvider implements ContentProvider {

  private File contentFolder;
  private File htmlSource;
  private Elements imagesElements = null;

  @Override
  public File getContentFolder() {
    return contentFolder;
  }

  /**
   * @param contentFolder
   * @param htmlSource an HTML file that is inside <code>contentFolder</code> that will be the m
   */
  public HTMLContentProvider(File contentFolder, File htmlSource) {
    super();
    Validate.isTrue(contentFolder.exists(), "Content folder does not exist");
    Validate.isTrue(contentFolder.isDirectory(), "Content folder must be a folder");
    Validate.isTrue(
        htmlSourceIsInContentFolder(contentFolder, htmlSource),
        "html source must be in content folder");
    this.contentFolder = contentFolder;
    this.htmlSource = htmlSource;
  }

  private boolean htmlSourceIsInContentFolder(File contentFolder, File htmlSource) {
    return FileUtils.listFiles(
                contentFolder,
                FileFilterUtils.and(
                    FileFilterUtils.nameFileFilter(htmlSource.getName()),
                    FileFilterUtils.fileFileFilter()),
                null)
            .size()
        == 1;
  }

  /** Gets an input stream onto the HTML content */
  @Override
  public InputStream getTextFieldSource() throws IOException {
    return new FileInputStream(htmlSource);
  }

  /** Boolean query as to whether the HTML has any images */
  @Override
  public boolean hasImages() throws IOException {
    return getImages().size() > 0;
  }

  /** Gets elements that are img tags */
  @Override
  public Elements getImages() throws IOException {
    if (imagesElements == null) {
      Document doc = Jsoup.parse(getTextFieldSource(), null, "");
      this.imagesElements = doc.getElementsByTag("img");
    }
    return imagesElements;
  }
}
