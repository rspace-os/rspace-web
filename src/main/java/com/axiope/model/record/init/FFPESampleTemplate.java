package com.axiope.model.record.init;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryChoiceFieldDef;
import com.researchspace.model.inventory.field.InventoryDateField;
import com.researchspace.model.inventory.field.InventoryNumberField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.inventory.field.SampleField;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.util.List;
import java.util.Optional;

public class FFPESampleTemplate extends BuiltinContent implements SampleTemplateBuiltIn {

  public FFPESampleTemplate(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public FFPESampleTemplate() {}

  @Override
  protected String getFormName() {
    return "FFPE ";
  }

  public Optional<Sample> createSampleTemplate(User createdBy) {
    Sample sample = createTemplate(createdBy);
    m_initializer.saveSampleTemplate(sample);
    m_sampleTemplate = sample;
    return Optional.of(sample);
  }

  private Sample createTemplate(User createdBy) {
    Sample sample = recordFactory.createSample("FFPE tissue", createdBy);
    sample.setSampleSource(SampleSource.LAB_CREATED);
    sample.setSubSampleAliases(SubSampleName.PORTION);
    sample.setDefaultUnitId(RSUnitDef.DIMENSIONLESS.getId());
    sample.setDescription(
        "Human tissue sample from surgical excision.The tissue is the sample. The subsamples are"
            + " made up of the parent FFPE block, plus any cut sections on glass slides. Sets of"
            + " blocks from the same procedure should stored together in a single draw.");
    sample.setTags("FFPE,tissue,slide,section,pathology");

    SampleField id = new InventoryTextField("Identifier");
    sample.addSampleField(id);
    SampleField donorNumber = new InventoryNumberField("Donor number");
    sample.addSampleField(donorNumber);
    SampleField blockNumber = new InventoryNumberField("Block number");
    sample.addSampleField(blockNumber);
    SampleField genderRace = new InventoryTextField("Gender,Race");
    sample.addSampleField(genderRace);
    SampleField heightWeightAge = new InventoryTextField("Height (cm), Weight (kg), BMI");
    sample.addSampleField(heightWeightAge);

    SampleField pathReport = new InventoryTextField("Pathology report as text if available");
    sample.addSampleField(pathReport);

    SampleField diagnosis = new InventoryTextField("Indication / Diagnosis");
    sample.addSampleField(diagnosis);

    SampleField date_of_birth_of_donor = new InventoryDateField("Date of birth of donor");
    sample.addSampleField(date_of_birth_of_donor);

    SampleField dateOfProcedure =
        new InventoryDateField("Date of surgical procedure (creation of sample)");
    sample.addSampleField(dateOfProcedure);

    InventoryChoiceFieldDef choiceFieldDef = new InventoryChoiceFieldDef();
    List<String> areaOptions =
        TransformerUtils.toList(
            "Cancer only", "Cancer and normal adjacent", "Normal", "Other pathology");
    choiceFieldDef.setChoiceOptionsList(areaOptions);
    InventoryChoiceField icf =
        new InventoryChoiceField(choiceFieldDef, "This sample includes areas showing:");
    sample.addSampleField(icf);

    sample.setStorageTempMax(QuantityInfo.of(5, RSUnitDef.CELSIUS));
    sample.setStorageTempMin(QuantityInfo.of(3, RSUnitDef.CELSIUS));
    sample.setTemplate(true);
    return sample;
  }

  public RSForm createForm(User createdBy) {
    return null;
  }

  @Override
  public String getFormIconName() {
    return "ffpe.png";
  }

  @Override
  public String getPreviewImageName() {
    return "ffpe.png";
  }
}
