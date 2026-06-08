import useStores from "../../stores/use-stores";
import AttachmentsField from "../components/Fields/Attachments/Attachments";
import IdentifiersField from "../components/Fields/Identifiers/Identifiers";
import DescriptionField from "../components/Fields/Description";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import ImageField from "../components/Fields/Image";
import NameField from "../components/Fields/Name";
import TagsField from "../components/Fields/Tags";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { observer } from "mobx-react-lite";
import React, { type ReactNode } from "react";
import InstrumentModel from "../../stores/models/InstrumentModel";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import OwnerField from "../components/Fields/Owner";
import LocationField from "../components/Fields/Location";
import {
  useFormSectionError,
  setFormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import LimitedAccessAlert from "../components/LimitedAccessAlert";
import { type Person } from "../../stores/definitions/Person";

type OverviewSectionArgs = {
  activeResult: InstrumentModel;
};

const OverviewSection = observer(({ activeResult }: OverviewSectionArgs) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrument"
      title="Overview"
      sectionName="overview"
      formSectionError={formSectionError}
      recordType="instrument"
    >
      <NameField
        fieldOwner={activeResult}
        record={activeResult}
        onErrorStateChange={(e) => {
          setFormSectionError(formSectionError, "name", e);
        }}
      />
      <OwnerField fieldOwner={activeResult} />
      {activeResult.readAccessLevel !== "public" && (
        <>
          <LocationField fieldOwner={activeResult} />
          <ImageField
            fieldOwner={activeResult}
            alt="What the instrument looks like"
          />
        </>
      )}
    </StepperPanel>
  );
});

type DetailsSectionArgs = {
  activeResult: InstrumentModel;
};

const DetailsSection = observer(({ activeResult }: DetailsSectionArgs) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrument"
      title="Details"
      sectionName="details"
      formSectionError={formSectionError}
      recordType="instrument"
    >
      <DescriptionField
        fieldOwner={activeResult}
        onErrorStateChange={(e) =>
          setFormSectionError(formSectionError, "description", e)
        }
      />
      <TagsField fieldOwner={activeResult} />
    </StepperPanel>
  );
});

type ExtraFieldSectionArgs = {
  activeResult: InstrumentModel;
};

const ExtraFieldSection = observer(
  ({ activeResult }: ExtraFieldSectionArgs) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
        icon="instrument"
        title="Custom Fields"
        sectionName="customFields"
        formSectionError={formSectionError}
        recordType="instrument"
      >
        <ExtraFields
          onErrorStateChange={(field, value) =>
            setFormSectionError(formSectionError, field, value)
          }
          result={activeResult}
        />
      </StepperPanel>
    );
  }
);

function InstrumentForm(): ReactNode {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentModel))
    throw new Error("ActiveResult must be an Instrument");
  if (!activeResult.owner) throw new Error("Instrument does not have an owner");
  const owner: Person = activeResult.owner;

  return (
    <Stepper
      titleText={activeResult.name}
      resetScrollPosition={activeResult}
      factory={activeResult.factory}
    >
      <LimitedAccessAlert
        readAccessLevel={activeResult.readAccessLevel}
        whatLabel="instrument"
        owner={owner}
      />
      <OverviewSection activeResult={activeResult} />
      {activeResult.readAccessLevel !== "public" && (
        <>
          <DetailsSection activeResult={activeResult} />
          <StepperPanel
            icon="instrument"
            title="Barcodes"
            sectionName="barcodes"
            recordType="instrument"
          >
            <BarcodesField
              fieldOwner={activeResult}
              factory={activeResult.factory}
              connectedItem={activeResult}
            />
          </StepperPanel>
        </>
      )}
      {activeResult.readAccessLevel === "full" && (
        <>
          <StepperPanel
            icon="instrument"
            title="Identifiers"
            sectionName="identifiers"
            recordType="instrument"
          >
            <IdentifiersField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="instrument"
            title="Attachments"
            sectionName="attachments"
            recordType="instrument"
          >
            <AttachmentsField fieldOwner={activeResult} />
          </StepperPanel>
          <ExtraFieldSection activeResult={activeResult} />
        </>
      )}
    </Stepper>
  );
}

export default observer(InstrumentForm);
