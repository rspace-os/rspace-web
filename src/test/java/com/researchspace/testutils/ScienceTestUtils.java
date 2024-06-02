package com.researchspace.testutils;

import static org.apache.commons.lang3.RandomUtils.nextInt;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

public class ScienceTestUtils {

  static final String AMINO_ACID_CODE = "ACDEFGHIKLMNPQRSTVWY";
  static final String RNA_CODE = "AUCG";
  static final String DNA_CODE = "ATCG";

  /** Common names of standard lab model organisms */
  public static final String[] MODEL_ORGANISMS_ENGLISH =
      new String[] {"fly", "mouse", "nematode", "rat", "xenopus", "yeast", "zebrafish"};

  /** Latin names of standard lab model organisms */
  public static final String[] MODEL_ORGANISMS_LATIN =
      new String[] {
        "Drosophila melanogaster",
        "Mus musculus",
        "Caenorhabditis elegans",
        "Rattus norvegicus",
        "Xenopus laevis",
        "Saccharomyces cervisiae",
        "Danio Rerio"
      };

  public static final String[] ANTIBODY_ISOTYPE =
      new String[] {"IgG", "IgA", "IgD", "IgE", "IgG1", "IgG2", "IgG3", "IgG4", "IgM", "IgY"};

  public static final String[] ANTIBODY_APPLICATION =
      new String[] {
        "ChIP",
        "ELISA",
        "Flow Cytometry",
        "Immunocytochemistry",
        "Immunofluorescence",
        "Immunohistochemistry",
        "Western"
      };

  public static final String[] ANTIBODY_SOURCE =
      new String[] {"Chicken", "Goat", "Guinea Pig", "Human", "Mouse", "Rabbit", "Rat", "Sheep"};

  public static final String[] DILUTIONS =
      new String[] {"1:10", "1:50", "1:100", "1:250", "1:500", "1:1000"};

  /**
   * Generates a random amino-acid sequence of specified length, using single-letter code of
   * standard 20 amino acids
   *
   * @param length
   * @return
   */
  public static String anyPeptide(int length) {
    return RandomStringUtils.random(length, AMINO_ACID_CODE);
  }

  /**
   * Gets random capitalised DNA sequence of standard nucleotides ATCG
   *
   * @param length
   * @return
   */
  public static String anyDNA(int length) {
    return RandomStringUtils.random(length, DNA_CODE);
  }

  public static String anyModelOrganismCommonName() {
    return MODEL_ORGANISMS_ENGLISH[nextInt(0, MODEL_ORGANISMS_ENGLISH.length)];
  }

  public static String anyModelOrganismLatinName() {
    return MODEL_ORGANISMS_LATIN[nextInt(0, MODEL_ORGANISMS_LATIN.length)];
  }

  /**
   * E.g. 'IgG'
   *
   * @return
   */
  public static String anyAntibodyIsotype() {
    return ANTIBODY_ISOTYPE[nextInt(0, ANTIBODY_ISOTYPE.length)];
  }

  /**
   * E.g. 'Western blot'
   *
   * @return
   */
  public static String anyAntibodyApplication() {
    return ANTIBODY_APPLICATION[nextInt(0, ANTIBODY_APPLICATION.length)];
  }

  /**
   * Gets random name of an organism used to make an antibody in e.g 'mouse', 'rabbit', etc.
   *
   * @return
   */
  public static String anyAntibodySource() {
    return ANTIBODY_SOURCE[nextInt(0, ANTIBODY_SOURCE.length)];
  }

  public static String anyRNA(int length) {
    return RandomStringUtils.random(length, RNA_CODE);
  }

  /**
   * Gets a real protein name randomly drawn from file 500 proteins previously retrieved at random
   * from Uniprot
   *
   * @return
   * @throws IOException
   */
  public static String anyProteinShortName() throws IOException {
    return fromProteinFile(0);
  }

  /**
   * Gets a real protein long name randomly drawn from file 500 proteins previously retrieved at
   * random from Uniprot.
   *
   * @return
   * @throws IOException
   */
  public static String anyProteinLongName() throws IOException {
    return fromProteinFile(1);
  }

  /**
   * Gets a n real protein long names randomly drawn from file of 500 proteins previously retrieved
   * at random from Uniprot. There may be duplicates.
   *
   * @return
   * @throws IOException
   */
  public static List<String> proteinLongNames(int number) throws IOException {
    final List<String> lines = readProteinFile();
    List<String> rc = new ArrayList<>();
    IntStream.range(0, number).mapToObj(i -> extractProteinData(1, lines)).forEach(rc::add);
    return rc;
  }

  /**
   * Gets n real protein short names randomly drawn from file of 500 proteins previously retrieved
   * at random from Uniprot. There may be duplicates.
   *
   * @return
   * @throws IOException
   */
  public static List<String> proteinShortNames(int number) throws IOException {
    final List<String> lines = readProteinFile();
    List<String> rc = new ArrayList<>();
    IntStream.range(0, number).mapToObj(i -> extractProteinData(0, lines)).forEach(rc::add);
    return rc;
  }

  /**
   * Gets a dilution string like '1:500'
   *
   * @return
   */
  public static String anyDilution() {
    return DILUTIONS[nextInt(0, DILUTIONS.length)];
  }

  private static String fromProteinFile(int tabIndex) throws IOException {
    List<String> lines = readProteinFile();
    return extractProteinData(tabIndex, lines);
  }

  private static String extractProteinData(int tabIndex, List<String> lines) {
    int max = lines.size();
    int randomIndex = RandomUtils.nextInt(0, max);
    String column = lines.get(randomIndex).split("\\t")[tabIndex];
    // alternative names are in (), we can ignore these.
    return column.substring(0, column.indexOf('(') != -1 ? column.indexOf('(') : column.length());
  }

  private static List<String> readProteinFile() throws IOException {
    File names = RSpaceTestUtils.getResource("proteinNames.tsv");
    List<String> lines = FileUtils.readLines(names, "UTF-8");
    return lines;
  }
}
