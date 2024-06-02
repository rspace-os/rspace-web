package com.researchspace.core.util;

import java.io.File;
import java.util.regex.Pattern;
import org.apache.commons.io.filefilter.AbstractFileFilter;
import org.apache.commons.lang.Validate;

/**
 * File filter solely to address RSPAC-1312, where MySQL that has changed a UTF-8 char to '?'
 * produces a file name that does not exist ( because the file system works with UTF-8)
 */
public class UnhandledUTF8FileFilter extends AbstractFileFilter {

  private String corruptedFileName;

  /**
   * @param corruptedFileName the filename as stored by MySQL
   */
  public UnhandledUTF8FileFilter(String corruptedFileName) {
    super();
    this.corruptedFileName = corruptedFileName;
  }

  /**
   * Checks to see if the filename matches a regex where utf-8 chars are replaced by '?' chars
   *
   * @param dir the file directory (ignored)
   * @param name the filename
   * @return true if the filename matches one of the regular expressions
   */
  @Override
  public boolean accept(final File dir, final String name) {
    // if they're not same length, can't be the same file
    if (name.length() != corruptedFileName.length()) {
      return false;
    }
    Pattern pattern = buildPattern(corruptedFileName, name);
    return pattern.matcher(name).matches();
  }

  private Pattern buildPattern(String corruptedName, String utf8FileName) {
    Validate.isTrue(
        corruptedName.length() == utf8FileName.length(), "Files names must be the same length");
    StringBuffer patternBuilder = new StringBuffer();
    for (int i = 0; i < corruptedName.length(); i++) {
      if (corruptedName.charAt(i) == utf8FileName.charAt(i)) {
        // we quote so that  '.' and other regexp metachars in the filename are treated as literals
        patternBuilder.append(Pattern.quote(Character.toString(corruptedName.charAt(i))));
      } else if (corruptedName.charAt(i) == '?') {
        // unless they correspond to a '?' in the corrupted name, in which we want to match any
        // char,
        // as we don't now what the original char was in the file name in the filestore.
        patternBuilder.append(".?");
      }
    }
    return Pattern.compile(patternBuilder.toString(), Pattern.CASE_INSENSITIVE);
  }
}
