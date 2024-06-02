package com.researchspace.testutils;

import static com.researchspace.testutils.ScienceTestUtils.anyAntibodyApplication;
import static com.researchspace.testutils.ScienceTestUtils.anyAntibodyIsotype;
import static com.researchspace.testutils.ScienceTestUtils.anyAntibodySource;
import static com.researchspace.testutils.ScienceTestUtils.anyDilution;
import static com.researchspace.testutils.ScienceTestUtils.anyModelOrganismCommonName;
import static com.researchspace.testutils.ScienceTestUtils.anyPeptide;
import static com.researchspace.testutils.ScienceTestUtils.anyProteinLongName;
import static com.researchspace.testutils.ScienceTestUtils.anyProteinShortName;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import java.io.IOException;
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
public class AntibodyTestSample extends InventoryTestSample {

  private Date expiryDate;
  private String name,
      description,
      tags,
      antigen,
      peptide,
      applicableSpecies,
      clonality,
      isoType,
      applications,
      dilutions,
      raisedIn,
      reference;

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
    sp.setQuantity(new QuantityPost(RandomUtils.nextInt(5, 500)));
    sp.setNewSampleSubSamplesCount(RandomUtils.nextInt(1, 20));
    List<EmptyFieldPost> fields = new ArrayList<>();

    fields.add(new InventoryFieldPost("Antigen", antigen, ApiFieldType.STRING.toString()));
    fields.add(new InventoryFieldPost("Peptide", peptide, ApiFieldType.STRING.toString()));
    fields.add(
        new InventoryFieldPost(
            "Applicable-species", applicableSpecies, ApiFieldType.CHOICE.toString()));
    fields.add(new InventoryFieldPost("Clonality", clonality, ApiFieldType.RADIO.toString()));
    fields.add(new InventoryFieldPost("Isotype", isoType, ApiFieldType.RADIO.toString()));
    fields.add(
        new InventoryFieldPost("Applications", applications, ApiFieldType.CHOICE.toString()));
    fields.add(new InventoryFieldPost("Dilutions", dilutions, ApiFieldType.TEXT.toString()));
    fields.add(new InventoryFieldPost("Raised-in", raisedIn, ApiFieldType.RADIO.toString()));
    fields.add(new InventoryFieldPost("Reference", reference, ApiFieldType.TEXT.toString()));

    sp.setFields(fields);
    return sp;
  }

  public static AntibodyTestSample createAntibodySample() {
    String name, longName;
    try {
      name = anyProteinShortName();
      longName = anyProteinLongName();
    } catch (IOException e) {
      throw new IllegalStateException();
    }
    return AntibodyTestSample.builder()
        .name("anti-" + name)
        .description("Anti- " + longName)
        .tags(randomAlphabetic(10))
        .antigen(name)
        .peptide(anyPeptide(12))
        .applicableSpecies(anyModelOrganismCommonName())
        .clonality("polyclonal")
        .isoType(anyAntibodyIsotype())
        .applications(anyAntibodyApplication())
        .dilutions(anyDilution())
        .raisedIn(anyAntibodySource())
        .reference("Abcam-" + RandomUtils.nextInt(0, 1000))
        .build();
  }
}
