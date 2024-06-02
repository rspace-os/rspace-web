package com.researchspace.testutils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

/**
 * To get sample template ID for the correct template to create samples from: <code>
 * curl  -H"apiKey: $RSPACE_API_KEY" "$RSPACE_URL/api/inventory/v1/sampleTemplates" | jq '.templates[] | {id: .id, name: .name}'
 * </code>
 *
 * <p>To post sample files: <code>
 * for f in *.json; do curl -X POST -H"apiKey: $RSPACE_API_KEY" -H"Content-Type: application/json" -d"@${f}" "$RSPACE_URL/api/inventory/v1/samples"; done
 * </code>
 */
public class SampleJsonGenerator {

  /**
   * Generate a folder full of .json files of unique samples to upload
   *
   * @throws IOException
   */
  @Test
  public void generateManyAntibodies() throws IOException {
    // edit as appropriate, you'll need the form id of the antibody template and
    // an empty directory to create the json files into.
    // once you've created the files:
    Long formId = 2L;
    String dirName = "samples-ab3";
    int numSamplesToCreate = 997; // off set so
    doCreate(formId, dirName, numSamplesToCreate, AntibodyTestSample::createAntibodySample);
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public class SampleWrapper {

    List<InventoryTestSample.InventorySamplePost> samples = new ArrayList<>();
  }

  @Test
  public void generateManyAntibodiesList() throws IOException {
    // edit as appropriate, you'll need the form id of the antibody template and
    // an empty directory to create the json files into.
    // once you've created the files:
    Long formId = 1081345L;
    String dirName = "samples-ab2";
    int numSamplesToCreate = 50;
    List<InventoryTestSample.InventorySamplePost> samples = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      AntibodyTestSample ts = AntibodyTestSample.createAntibodySample();
      InventoryTestSample.InventorySamplePost post = ts.toInventorySamplePost(formId);
      samples.add(post);
    }
    System.err.println(toJson(new SampleWrapper(samples)));
  }

  @Test
  public void generateManyDrosophilaList() throws IOException {
    // edit as appropriate, you'll need the form id of the antibody template and
    // an empty directory to create the json files into.
    // once you've created the files:
    File data = RSpaceTestUtils.getResource("inventory/bloomington-flybase.tsv");
    List<String> lines = FileUtils.readLines(data, "UTF-8");
    Long formId = 1310720L;
    String dirName = "drosophila2";
    List<InventoryTestSample.InventorySamplePost> samples = new ArrayList<>();
    for (int i = 1; i < lines.size(); i++) {
      InventoryTestSample sample = DrosophilaTestSample.createSample(lines.get(i));
      InventoryTestSample.InventorySamplePost post = sample.toInventorySamplePost(formId);
      samples.add(post);
      String json = toJson(post);
    }
    System.err.println(toJson(new SampleWrapper(samples)));
  }

  @Test
  public void generateManyDrosophila() throws IOException {
    // edit as appropriate, you'll need the form id of the antibody template and
    // an empty directory to create the json files into.
    // once you've created the files:
    File data = RSpaceTestUtils.getResource("inventory/bloomington-flybase.tsv");
    List<String> lines = FileUtils.readLines(data, "UTF-8");
    Long formId = 1310720L;
    String dirName = "drosophila";
    for (int i = 1; i < lines.size(); i++) {
      InventoryTestSample sample = DrosophilaTestSample.createSample(lines.get(i));
      InventoryTestSample.InventorySamplePost post = sample.toInventorySamplePost(formId);
      String json = toJson(post);
      writeToFile(dirName, i, json);
    }
  }

  /**
   * Generate a folder full of .json files of unique samples to upload
   *
   * @throws IOException
   */
  @Test
  public void generateManyBasicSamples() throws IOException {
    // edit as appropriate, you'll need the form id of the antibody template and
    // an empty directory to create the json files into.
    Long formId = 1L;
    String dirName = "samples";
    int numSamplesToCreate = 50;
    doCreate(formId, dirName, numSamplesToCreate, BasicTestSample::createBasicSample);
  }

  private void doCreate(
      Long formId,
      String dirName,
      int numSamplesToCreate,
      Supplier<InventoryTestSample> testSampleSupplier)
      throws IOException, JsonProcessingException {
    for (int i = 0; i < numSamplesToCreate; i++) {
      InventoryTestSample sample = testSampleSupplier.get();
      InventoryTestSample.InventorySamplePost post = sample.toInventorySamplePost(formId);
      String json = toJson(post);
      writeToFile(dirName, i, json);
    }
  }

  private void writeToFile(String dirName, int i, String json) throws IOException {
    File dir = new File(dirName);
    FileUtils.forceMkdir(dir);
    File out = new File(dir, "sample-" + i + ".json");
    FileUtils.writeStringToFile(out, json, "UTF-8");
  }

  private String toJson(InventoryTestSample.InventorySamplePost post)
      throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(post);
  }

  private String toJson(SampleWrapper post) throws JsonProcessingException {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(post);
  }
}
