package com.researchspace.document.importer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.jsoup.select.Elements;

/** A source for content that will be used to automatically generate an RSpace document. */
public interface ContentProvider {
  /**
   * A folder that contains source material from which to create an RSpace document
   *
   * @return a {@link File}
   */
  File getContentFolder();

  /**
   * An input stream onto the source HTML content that will be inserted into the newly created text
   * field.
   *
   * @return an {@link InputStream}
   * @throws IOException
   */
  InputStream getTextFieldSource() throws IOException;

  /**
   * Return <code>true</code> if content HTML has at least 1 &lt;img&gt; tag. <br>
   * The result of this computation may be cached for future use.
   *
   * @return
   * @throws IOException
   */
  boolean hasImages() throws IOException;

  /**
   * Gets Elements that are &lt;img&gt; tags
   *
   * @return
   * @throws IOException
   */
  Elements getImages() throws IOException;
}
