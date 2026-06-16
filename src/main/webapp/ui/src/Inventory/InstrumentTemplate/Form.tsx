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
import InstrumentTemplateModel from "../../stores/models/InstrumentTemplateModel";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import OwnerField from "../components/Fields/Owner";
import {
  useFormSectionError,
  setFormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import LimitedAccessAlert from "../components/LimitedAccessAlert";
import AccessPermissions from "../components/Fields/AccessPermissions";
import { type Person } from "../../stores/definitions/Person";

type OverviewSectionArgs = {
  activeResult: InstrumentTemplateModel;
};

const OverviewSection = observer(({ activeResult }: OverviewSectionArgs) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrumentTemplate"
      title="Overview"
      sectionName="overview"
      formSectionError={formSectionError}
      recordType="instrumentTemplate"
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
        <ImageField
          fieldOwner={activeResult}
          alt="What the instrument template looks like"
        />
      )}
    </StepperPanel>
  );
});

type DetailsSectionArgs = {
  activeResult: InstrumentTemplateModel;
};

const DetailsSection = observer(({ activeResult }: DetailsSectionArgs) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrumentTemplate"
      title="Details"
      sectionName="details"
      formSectionError={formSectionError}
      recordType="instrumentTemplate"
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
  activeResult: InstrumentTemplateModel;
};

const ExtraFieldSection = observer(
  ({ activeResult }: ExtraFieldSectionArgs) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
        icon="instrumentTemplate"
        title="Custom Fields"
        sectionName="customFields"
        formSectionError={formSectionError}
        recordType="instrumentTemplate"
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

function InstrumentTemplateForm(): ReactNode {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentTemplateModel))
    throw new Error("ActiveResult must be an Instrument Template");
  if (!activeResult.owner)
    throw new Error("Instrument Template does not have an owner");
  const owner: Person = activeResult.owner;

  return (
    <Stepper
      titleText={activeResult.name}
      resetScrollPosition={activeResult}
      factory={activeResult.factory}
    >
      <LimitedAccessAlert
        readAccessLevel={activeResult.readAccessLevel}
        whatLabel="instrument template"
        owner={owner}
      />
      <OverviewSection activeResult={activeResult} />
      {activeResult.readAccessLevel !== "public" && (
        <>
          <DetailsSection activeResult={activeResult} />
          <StepperPanel
            icon="instrumentTemplate"
            title="Barcodes"
            sectionName="barcodes"
            recordType="instrumentTemplate"
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
            icon="instrumentTemplate"
            title="Identifiers"
            sectionName="identifiers"
            recordType="instrumentTemplate"
          >
            <IdentifiersField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="instrumentTemplate"
            title="Attachments"
            sectionName="attachments"
            recordType="instrumentTemplate"
          >
            <AttachmentsField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="instrumentTemplate"
            title="Access Permissions"
            sectionName="permissions"
            recordType="instrumentTemplate"
          >
            <AccessPermissions fieldOwner={activeResult} />
          </StepperPanel>
          <ExtraFieldSection activeResult={activeResult} />
        </>
      )}
    </Stepper>
  );
}

export default observer(InstrumentTemplateForm);
