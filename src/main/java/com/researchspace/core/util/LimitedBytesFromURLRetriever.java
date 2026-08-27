package com.researchspace.core.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.function.Supplier;
import org.apache.commons.lang3.Validate;

/**
 * Retrieves bytes from an external URL in a safe way. Content-length can be manipulated, so can't
 * be totally relied on. This method downloads bytes from the URI until the maxByteSize has been
 * reached.
 */
public class LimitedBytesFromURLRetriever {
  private int timeoutMillis = 2000;
  private long maxByteSize = 2_000_000;

  /**
   * @param timeoutMillis The maximum time in millis to wait for a connection to be established,
   *     &gt; 0
   * @param maxByteSize The maximum size of the byte array, &gt; 0
   */
  public LimitedBytesFromURLRetriever(int timeoutMillis, long maxByteSize) {
    Validate.isTrue(timeoutMillis > 0, "timeoutMillis must be > 0");
    Validate.isTrue(maxByteSize > 0, "maxByteSize must be > 0");
    this.timeoutMillis = timeoutMillis;
    this.maxByteSize = maxByteSize;
  }

  /**
   * Retrieves bytes from a URL. Will return the default bytes if
   *
   * <ul>
   *   <li>imgSrcValue is not parseable into aa URL
   *   <li>the URL does not exist or exceeds timeout
   *   <li>the size of the retrieved image exceeds <code>maxByteSize</code>
   *   <li>An IO exception is thrown internally
   * </ul>
   *
   * @param imgSrcValue A String URL
   * @param defaultValueSupplier Returns a default byte array if retrieval fails
   * @return The byte array from the URL or the default bytes if the requested URL failed.
   */
  public byte[] retrieveUrlBytesQuietly(String imgSrcValue, Supplier<byte[]> defaultValueSupplier) {
    Validate.noNullElements(new Object[] {imgSrcValue, defaultValueSupplier});
    URLConnection urlConnection = null;
    try {
      urlConnection = getUrlConnection(imgSrcValue);
      urlConnection.setReadTimeout(timeoutMillis);
    } catch (IOException e2) {
      return defaultValueSupplier.get();
    }

    try (InputStream is = getInputStream(urlConnection);
        ByteArrayOutputStream fos = new ByteArrayOutputStream()) {
      int n;
      int total = 0;
      final int bufferSize = 8192;
      byte[] buff = new byte[bufferSize];
      while ((n = is.read(buff, 0, bufferSize)) >= 0) {
        fos.write(buff, 0, n);
        if (total + n > maxByteSize) {
          return defaultValueSupplier.get();
        }
        total += n;
      }
      return fos.toByteArray();
    } catch (IOException e) {
      return defaultValueSupplier.get();
    }
  }

  private URLConnection getUrlConnection(String imgSrcValue) throws IOException {
    URL url = new URL(imgSrcValue);
    URLConnection urlConnection = url.openConnection();
    return urlConnection;
  }

  InputStream getInputStream(URLConnection urlConnection) throws IOException {
    return urlConnection.getInputStream();
  }
}
