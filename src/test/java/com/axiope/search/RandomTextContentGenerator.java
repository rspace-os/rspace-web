package com.axiope.search;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;

/** For generating random text content with standard word frequency distribution. */
public class RandomTextContentGenerator {

  Integer[] cumulativeCount;
  String[] allWords;
  Set<String> usedWords = new HashSet<String>();
  File contentDirecotory = null;

  private StringBuffer createStringBuffer(final int LINE_LENGTH, int EXPECTED_WORD_SIZE) {
    StringBuffer sb;
    sb = new StringBuffer(LINE_LENGTH * EXPECTED_WORD_SIZE);
    return sb;
  }

  private Random fRandom = new Random();

  /*
   * Variable around normal distribution of mean
   */
  int calculateNumLines(int mean) {
    return mean + (int) (fRandom.nextGaussian() * (mean / 5));
  }

  /**
   * Parses word frequency list
   *
   * @throws IOException
   */
  protected void parseWordCountList() throws IOException {
    List<String> lines =
        FileUtils.readLines(new File("src/test/resources/TestResources/wordFreqs.txt"));
    cumulativeCount = new Integer[lines.size()];
    allWords = new String[lines.size()];
    int i = 0;
    int total = 0;
    Pattern p = Pattern.compile("\\d+");
    for (String line : lines) {
      if (line.matches("^$")) {
        continue;
      }
      String[] lineArr = line.split("\\s+");
      Matcher m = p.matcher(lineArr[2]);
      m.find();
      allWords[i] = lineArr[1];
      int x = java.lang.Integer.parseInt(m.group());
      total = total + (x / 100); // approximate

      cumulativeCount[i] = total;
      i++;
    }
  }

  public void setFileContentDirectory(File filesToIndexDir) {
    if (filesToIndexDir.exists() && !filesToIndexDir.isDirectory()) {
      throw new IllegalArgumentException(" not a directory ");
    }
    this.contentDirecotory = filesToIndexDir;
  }

  private boolean deleteContentBeforeStart;

  /**
   * Remove any existing content
   *
   * @param b
   */
  public void deleteExistingContent(boolean b) {
    this.deleteContentBeforeStart = b;
  }

  private boolean deleteContentOnExit;

  /**
   * Remove content after exiting
   *
   * @param b
   */
  public void deleteContentOnExit(boolean b) {
    this.deleteContentOnExit = b;
  }

  private boolean isinitialised = false;

  /** Runs once for all tests */
  public void init() {
    if (isinitialised) {
      return;
    }
    if (deleteContentBeforeStart && contentDirecotory.exists()) {
      deleteContent();
    }
    if (!contentDirecotory.exists()) {
      contentDirecotory.mkdir();
    }
    this.isinitialised = true;
  }

  private void deleteContent() {
    try {
      FileUtils.deleteDirectory(contentDirecotory);
    } catch (IOException e1) {
      e1.printStackTrace();
    }
  }
}
