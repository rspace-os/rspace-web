package com.researchspace.testutils;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.lang3.RandomUtils;

@Value
@EqualsAndHashCode(callSuper = false)
@Builder
public class DrosophilaTestSample extends InventoryTestSample {

  private Date expiryDate;
  private String name,
      description,
      tags,
      flybaseId,
      collection,
      stockCV,
      species,
      genotype,
      desc,
      stockId,
      notes;

  /**
   * This completely depends on the field structure of the Antibody template we are using in
   * production-testing
   *
   * @param templateId
   * @return
   */
  public InventorySamplePost toInventorySamplePost(Long templateId) {
    InventorySamplePost sp = new InventorySamplePost();
    sp.setName(name);
    sp.setDescription(description);
    sp.setTags(tags);
    sp.setTemplateId(templateId);
    int subsampleCount = RandomUtils.nextInt(3, 20);
    // quantity should be 1 for each subsample,
    sp.setQuantity(new QuantityPost(1, subsampleCount));
    sp.setNewSampleSubSamplesCount(subsampleCount);
    List<EmptyFieldPost> fields = new ArrayList<>();

    fields.add(new InventoryFieldPost("Flybase ID", flybaseId, ApiFieldType.STRING.toString()));
    fields.add(
        new InventoryFieldPost("Collection Name", collection, ApiFieldType.RADIO.toString()));
    fields.add(new InventoryFieldPost("Stock Type CV", stockCV, ApiFieldType.STRING.toString()));
    fields.add(new InventoryFieldPost("Species", species, ApiFieldType.STRING.toString()));
    fields.add(new InventoryFieldPost("FB Genotype", genotype, ApiFieldType.STRING.toString()));
    fields.add(new InventoryFieldPost("Description", desc, ApiFieldType.STRING.toString()));
    fields.add(new InventoryFieldPost("Stock Number", stockId, ApiFieldType.NUMBER.toString()));
    fields.add(new InventoryFieldPost("Notes", notes, ApiFieldType.TEXT.toString()));

    sp.setFields(fields);
    return sp;
  }

  public static DrosophilaTestSample createSample(String line) {
    String[] fields = line.split("\t");
    String name = fields[0];

    return DrosophilaTestSample.builder()
        .name(name)
        .tags(randomAlphabetic(10))
        .flybaseId(fields[0])
        .collection(fields[1])
        .stockCV(fields[2])
        .species(fields[3])
        .genotype(fields[4])
        .desc(fields[5])
        .stockId(fields[6])
        .build();
  }
}
