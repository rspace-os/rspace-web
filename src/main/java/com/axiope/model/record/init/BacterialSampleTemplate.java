package com.axiope.model.record.init;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.inventory.field.SampleField;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.util.Optional;

public class BacterialSampleTemplate extends BuiltinContent implements SampleTemplateBuiltIn {

  public BacterialSampleTemplate(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public BacterialSampleTemplate() {}

  @Override
  protected String getFormName() {
    return "Bacteria";
  }

  public Optional<Sample> createSampleTemplate(User createdBy) {
    Sample sample = createTemplate(createdBy);
    m_initializer.saveSampleTemplate(sample);
    m_sampleTemplate = sample;
    return Optional.of(sample);
  }

  private Sample createTemplate(User createdBy) {
    String[] stringFieldKeys1 =
        new String[] {
          "Organism",
          // "Materialsfieldname",
          "Gene name",
          "Allele",
          "Derivative-of",
          "Reporter",
          "Selectable marker"
        };

    Sample sample = recordFactory.createSample("Bacteria", createdBy);
    sample.setStorageTempMax(QuantityInfo.of(-18, RSUnitDef.CELSIUS));
    sample.setStorageTempMin(QuantityInfo.of(-30, RSUnitDef.CELSIUS));
    sample.setDescription("Description of a bacterial culture");
    sample.setTemplate(true);
    SampleField id = new InventoryTextField("Identifier");
    sample.addSampleField(id);

    for (String fieldKey : stringFieldKeys1) {
      InventoryStringField field = new InventoryStringField(fieldKey);
      sample.addSampleField(field);
    }

    InventoryTextField field = new InventoryTextField("Propagation");
    sample.addSampleField(field);

    InventoryStringField phenotype = new InventoryStringField("Phenotype");
    sample.addSampleField(phenotype);

    InventoryTextField ref = new InventoryTextField("Reference");
    sample.addSampleField(ref);

    sample.setSubSampleAliases(SubSampleName.ALIQUOT);
    return sample;
  }

  public RSForm createForm(User createdBy) {
    return null;
  }

  @Override
  public String getFormIconName() {
    return "bacteriaSample32.png";
  }

  @Override
  public String getPreviewImageName() {
    return "bacteriaSample150.png";
  }
}
