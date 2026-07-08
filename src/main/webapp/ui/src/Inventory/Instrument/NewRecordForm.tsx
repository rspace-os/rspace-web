import { observer } from "mobx-react-lite";
import React from "react";
import docLinks from "../../assets/DocLinks";
import { inventoryRecordTypeLabels } from "../../stores/definitions/BaseRecord";
import InstrumentModel from "../../stores/models/InstrumentModel";
import useStores from "../../stores/use-stores";
import DescriptionField from "../components/Fields/Description";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import ImageField from "../components/Fields/Image";
import NameField from "../components/Fields/Name";
import TagsField from "../components/Fields/Tags";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import SynchroniseFormSections, { UnsynchroniseFormSections } from "../components/Stepper/SynchroniseFormSections";
import InstrumentTemplateField from "./Fields/InstrumentTemplateField";
import TemplateFields from "./Fields/TemplateFields";

const OverviewSection = observer(({ activeResult }: { activeResult: InstrumentModel }) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  React.useEffect(() => {
    setFormSectionError(formSectionError, "name", true);
  }, []);

  return (
    <StepperPanel title="Overview" sectionName="overview" formSectionError={formSectionError} recordType="instrument">
      <NameField
        fieldOwner={activeResult}
        record={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "name", e)}
      />
      <InstrumentTemplateField />
      <ImageField fieldOwner={activeResult} alt="What the instrument looks like" />
    </StepperPanel>
  );
});

const DetailsSection = observer(({ activeResult }: { activeResult: InstrumentModel }) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel title="Details" sectionName="details" formSectionError={formSectionError} recordType="instrument">
      <DescriptionField
        fieldOwner={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
      />
      <TagsField fieldOwner={activeResult} />
    </StepperPanel>
  );
});

const CustomFieldsSection = observer(({ activeResult }: { activeResult: InstrumentModel }) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      title="Custom Fields"
      sectionName="customFields"
      formSectionError={formSectionError}
      recordType="instrument"
    >
      <TemplateFields
        onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
        instrument={activeResult}
      />
      <ExtraFields
        onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
        result={activeResult}
      />
    </StepperPanel>
  );
});

export default function NewRecordForm(): React.ReactNode {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentModel))
    throw new Error("ActiveResult must be an Instrument");

  return (
    <SynchroniseFormSections>
      <Stepper
        helpLink={{
          link: docLinks.createInstrument,
          title: "Info on creating new instruments.",
        }}
        titleText={`New ${inventoryRecordTypeLabels.instrument}`}
        resetScrollPosition={Symbol("always reset scroll")}
      >
        <UnsynchroniseFormSections>
          <OverviewSection activeResult={activeResult} />
        </UnsynchroniseFormSections>
        <DetailsSection activeResult={activeResult} />
        <CustomFieldsSection activeResult={activeResult} />
      </Stepper>
    </SynchroniseFormSections>
  );
}
