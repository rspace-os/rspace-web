package com.axiope.model.record.init;

import static com.researchspace.core.util.TransformerUtils.toList;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSampleName;
import com.researchspace.model.inventory.field.InventoryChoiceField;
import com.researchspace.model.inventory.field.InventoryChoiceFieldDef;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.model.inventory.field.InventoryTextField;
import com.researchspace.model.inventory.field.SampleField;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.util.List;
import java.util.Optional;

public class AntibodySampleTemplate extends BuiltinContent implements SampleTemplateBuiltIn {

  public AntibodySampleTemplate(IBuiltInPersistor initializer) {
    super(initializer);
  }

  public AntibodySampleTemplate() {}

  @Override
  protected String getFormName() {
    return "Antibody";
  }

  public Optional<Sample> createSampleTemplate(User createdBy) {
    Sample sample = createTemplate(createdBy);
    m_initializer.saveSampleTemplate(sample);
    m_sampleTemplate = sample;
    return Optional.of(sample);
  }

  private Sample createTemplate(User createdBy) {
    Sample sample = recordFactory.createSample("Antibody", createdBy);
    sample.setStorageTempMax(QuantityInfo.of(5, RSUnitDef.CELSIUS));
    sample.setStorageTempMin(QuantityInfo.of(3, RSUnitDef.CELSIUS));

    SampleField id = new InventoryTextField("Identifier");
    sample.addSampleField(id);
    SampleField antigen = new InventoryStringField("Antigen");
    sample.addSampleField(antigen);

    SampleField pep = new InventoryStringField("Peptide");
    sample.addSampleField(pep);

    InventoryChoiceFieldDef speciesFieldChoiceDef = new InventoryChoiceFieldDef();
    List<String> speciesList =
        TransformerUtils.toList(
            "bovine", "fly", "human", "mouse", "nematode", "rat", "xenopus", "yeast", "zebrafish");
    speciesFieldChoiceDef.setChoiceOptionsList(speciesList);
    InventoryChoiceField icf =
        new InventoryChoiceField(speciesFieldChoiceDef, "Applicable-species");
    sample.addSampleField(icf);

    InventoryRadioFieldDef clonalityFieldRadioDef = new InventoryRadioFieldDef();
    List<String> cList = TransformerUtils.toList("monoclonal", "polyclonal");
    clonalityFieldRadioDef.setRadioOptionsList(cList);
    InventoryRadioField rcf = new InventoryRadioField(clonalityFieldRadioDef, "Clonality");
    sample.addSampleField(rcf);

    InventoryRadioFieldDef isotypeFieldForm = new InventoryRadioFieldDef();
    List<String> iList =
        TransformerUtils.toList(
            "IgG", "IgA", "IgD", "IgE", "IgG1", "IgG2", "IgG3", "IgG4", "IgM", "IgY");
    isotypeFieldForm.setRadioOptionsList(iList);
    InventoryRadioField iso = new InventoryRadioField(isotypeFieldForm, "Isotype");
    iso.setFieldData("IgG");
    sample.addSampleField(iso);

    InventoryChoiceFieldDef appFieldForm = new InventoryChoiceFieldDef();
    List<String> appList =
        toList(
            "ChIP",
            "ELISA",
            "Flow Cytometry",
            "Immunocytochemistry",
            "Immunofluorescence",
            "Immunohistochemistry",
            "Western");
    appFieldForm.setChoiceOptionsList(appList);
    InventoryChoiceField apps = new InventoryChoiceField(appFieldForm, "Applications");
    sample.addSampleField(apps);

    SampleField dilutions = new InventoryTextField("Dilutions");
    sample.addSampleField(dilutions);

    InventoryRadioFieldDef raisedInDef = new InventoryRadioFieldDef();
    List<String> rList =
        TransformerUtils.toList(
            "Chicken", "Goat", "Guinea Pig", "Human", "Mouse", "Rabbit", "Rat", "Sheep");
    raisedInDef.setRadioOptionsList(rList);
    InventoryRadioField raisedIn = new InventoryRadioField(raisedInDef, "Raised-in");
    sample.addSampleField(raisedIn);

    InventoryTextField ref = new InventoryTextField("Reference");
    sample.addSampleField(ref);

    sample.setSubSampleAliases(SubSampleName.ALIQUOT);
    sample.setTemplate(true);
    return sample;
  }

  public RSForm createForm(User createdBy) {
    return null;
  }

  @Override
  public String getFormIconName() {
    return "antibodySample32.png";
  }

  @Override
  public String getPreviewImageName() {
    return "antibodySample150.png";
  }
}
