package com.researchspace.build;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces {@code ${name}} and {@code @name@} placeholders whose name is a known property and
 * leaves unknown ones untouched, as Maven resource filtering did. Unlike Gradle's line
 * filters it keeps the file's line endings, including lone carriage returns.
 *
 * <p>Gradle sets {@link #setProperties} from {@code filter(PlaceholderFilter, properties:
 * map)}.
 */
public class PlaceholderFilter extends FilterReader {

  private static final Pattern PLACEHOLDER =
      Pattern.compile("\\$\\{([A-Za-z0-9_.\\-]+)}|@([A-Za-z0-9_.\\-]+)@");

  private Map<String, String> properties = Map.of();
  private Reader filtered;

  public PlaceholderFilter(Reader in) {
    super(in);
  }

  public void setProperties(Map<String, String> properties) {
    this.properties = properties;
  }

  private Reader filtered() throws IOException {
    if (filtered == null) {
      StringWriter text = new StringWriter();
      in.transferTo(text);
      filtered = new StringReader(substitute(text.toString()));
    }
    return filtered;
  }

  private String substitute(String text) {
    if (text.indexOf('$') < 0 && text.indexOf('@') < 0) {
      return text;
    }
    Matcher m = PLACEHOLDER.matcher(text);
    StringBuilder out = new StringBuilder(text.length());
    while (m.find()) {
      String name = m.group(1) != null ? m.group(1) : m.group(2);
      String value = properties.get(name);
      m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
    }
    m.appendTail(out);
    return out.toString();
  }

  @Override
  public int read() throws IOException {
    return filtered().read();
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    return filtered().read(cbuf, off, len);
  }

  @Override
  public long skip(long n) throws IOException {
    return filtered().skip(n);
  }

  @Override
  public boolean ready() throws IOException {
    return filtered().ready();
  }

  @Override
  public boolean markSupported() {
    return false;
  }
}
