import React from "react";
import useStores from "../../stores/use-stores";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { inventoryRecordTypeLabels } from "../../stores/definitions/BaseRecord";
import NameField from "../components/Fields/Name";
import DescriptionField from "../components/Fields/Description";
import TagsField from "../components/Fields/Tags";
import ImageField from "../components/Fields/Image";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import InstrumentTemplateModel from "../../stores/models/InstrumentTemplateModel";
import docLinks from "../../assets/DocLinks";
import { observer } from "mobx-react-lite";
import {
  useFormSectionError,
  setFormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import SynchroniseFormSections, {
  UnsynchroniseFormSections,
} from "../components/Stepper/SynchroniseFormSections";
import AccessPermissions from "../components/Fields/AccessPermissions";

const OverviewSection = observer(
  ({ activeResult }: { activeResult: InstrumentTemplateModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    React.useEffect(() => {
      setFormSectionError(formSectionError, "name", true);
    }, []);

    return (
      <StepperPanel
        title="Overview"
        sectionName="overview"
        formSectionError={formSectionError}
        recordType="instrumentTemplate"
      >
        <NameField
          fieldOwner={activeResult}
          record={activeResult}
          onErrorStateChange={(e) =>
            setFormSectionError(formSectionError, "name", e)
          }
        />
        <ImageField
          fieldOwner={activeResult}
          alt="A visual representation of the instrument template"
        />
      </StepperPanel>
    );
  }
);

const DetailsSection = observer(
  ({ activeResult }: { activeResult: InstrumentTemplateModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
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
  }
);

const CustomFieldsSection = observer(
  ({ activeResult }: { activeResult: InstrumentTemplateModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
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

export default function NewRecordForm(): React.ReactNode {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentTemplateModel))
    throw new Error("ActiveResult must be an Instrument Template");

  return (
    <SynchroniseFormSections>
      <Stepper
        helpLink={{
          link: docLinks.createInstrumentTemplate,
          title: "Info on creating new instrument templates.",
        }}
        titleText={`New ${inventoryRecordTypeLabels.instrumentTemplate}`}
        resetScrollPosition={Symbol("always reset scroll")}
      >
        <UnsynchroniseFormSections>
          <OverviewSection activeResult={activeResult} />
        </UnsynchroniseFormSections>
        <DetailsSection activeResult={activeResult} />
        <StepperPanel
          title="Access Permissions"
          sectionName="permissions"
          recordType="instrumentTemplate"
        >
          <AccessPermissions fieldOwner={activeResult} />
        </StepperPanel>
        <CustomFieldsSection activeResult={activeResult} />
      </Stepper>
    </SynchroniseFormSections>
  );
}
