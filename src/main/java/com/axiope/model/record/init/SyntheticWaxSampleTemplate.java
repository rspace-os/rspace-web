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
import com.researchspace.model.inventory.field.InventoryTimeField;
import com.researchspace.model.inventory.field.SampleField;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.util.List;
import java.util.Optional;

public class SyntheticWaxSampleTemplate extends BuiltinContent implements SampleTemplateBuiltIn {

  public SyntheticWaxSampleTemplate(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public SyntheticWaxSampleTemplate() {}

  @Override
  protected String getFormName() {
    return "Synthetic Wax";
  }

  public Optional<Sample> createSampleTemplate(User createdBy) {
    Sample sample = createTemplate(createdBy);
    m_initializer.saveSampleTemplate(sample);
    m_sampleTemplate = sample;
    return Optional.of(sample);
  }

  private Sample createTemplate(User createdBy) {
    Sample sample = recordFactory.createSample("Synthetic Wax", createdBy);
    sample.setSampleSource(SampleSource.LAB_CREATED);
    sample.setSubSampleAliases(SubSampleName.PORTION);
    sample.setDefaultUnitId(RSUnitDef.GRAM.getId());
    sample.setDescription(
        "Standard block of synthetic wax for testing. Subsamples used for quality testing are 5g"
            + " sqaure portions cut from main block using Acme wax block cutter.");
    sample.setTags("wax");

    SampleField id = new InventoryTextField("Identifier");
    sample.addSampleField(id);
    SampleField productionDate = new InventoryDateField("Production Date");
    sample.addSampleField(productionDate);
    SampleField productionTime = new InventoryTimeField("Production Time");
    sample.addSampleField(productionTime);
    SampleField batchNumber = new InventoryNumberField("Batch Number");
    sample.addSampleField(batchNumber);

    InventoryChoiceFieldDef choiceFieldDef = new InventoryChoiceFieldDef();
    List<String> options =
        TransformerUtils.toList(
            "viscosity", "opacity", "melting point", "flash point", "full fractionation analysis");
    choiceFieldDef.setChoiceOptionsList(options);
    InventoryChoiceField icf = new InventoryChoiceField(choiceFieldDef, "Requested test types");
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
    return "synthetic-wax.png";
  }

  @Override
  public String getPreviewImageName() {
    return "synthetic-wax.png";
  }
}
