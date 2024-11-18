// @flow

import React, { type Node } from "react";
import useStores from "../../stores/use-stores";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { inventoryRecordTypeLabels } from "../../stores/definitions/InventoryRecord";
import NameField from "../components/Fields/Name";
import Description from "../components/Fields/Description";
import StorageTemperature from "../Sample/Fields/StorageTemperature";
import Tags from "../components/Fields/Tags";
import Fields from "./Fields/CustomFields";
import ImageField from "../components/Fields/Image";
import Source from "../Sample/Fields/Source";
import Expiry from "../Sample/Fields/Expiry";
import SubSampleAlias from "./Fields/Alias";

import QuantityUnits from "./Fields/QuantityUnits";
import docLinks from "../../assets/DocLinks";
import TemplateModel from "../../stores/models/TemplateModel";
import { observer } from "mobx-react-lite";
import {
  useFormSectionError,
  setFormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import SynchroniseFormSections from "../components/Stepper/SynchroniseFormSections";
import AccessPermissions from "../components/Fields/AccessPermissions";

const OverviewSection = observer(
  ({ activeResult }: { activeResult: TemplateModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
        title="Overview"
        sectionName="overview"
        formSectionError={formSectionError}
        recordType="sampleTemplate"
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
          alt="A visual representation of the samples that will be created from this new template"
        />
      </StepperPanel>
    );
  }
);

const DetailsSection = observer(
  ({ activeResult }: { activeResult: TemplateModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
        title="Details"
        sectionName="details"
        formSectionError={formSectionError}
        recordType="sampleTemplate"
      >
        <Expiry
          fieldOwner={activeResult}
          onErrorStateChange={(value) =>
            setFormSectionError(formSectionError, "expiry", value)
          }
        />
        <Source fieldOwner={activeResult} />
        <StorageTemperature
          fieldOwner={activeResult}
          onErrorStateChange={(value) =>
            setFormSectionError(formSectionError, "temperature", value)
          }
        />
        <SubSampleAlias
          fieldOwner={activeResult}
          onErrorStateChange={(value) =>
            setFormSectionError(formSectionError, "alias", value)
          }
        />
        <QuantityUnits />
        <Description
          fieldOwner={activeResult}
          onErrorStateChange={(e) =>
            setFormSectionError(formSectionError, "description", e)
          }
        />
        <Tags fieldOwner={activeResult} />
      </StepperPanel>
    );
  }
);

const FieldsSection = observer(
  ({ activeResult }: { activeResult: TemplateModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
        title="Custom Fields"
        sectionName="customFields"
        formSectionError={formSectionError}
        recordType="sampleTemplate"
      >
        <Fields
          onErrorStateChange={(name, value) =>
            setFormSectionError(formSectionError, name, value)
          }
        />
      </StepperPanel>
    );
  }
);

export default function NewRecordForm(): Node {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel))
    throw new Error("ActiveResult must be a Template");

  return (
    <SynchroniseFormSections>
      <Stepper
        helpLink={{
          link: docLinks.createTemplate,
          title: "Info on creating new templates.",
        }}
        titleText={`New ${inventoryRecordTypeLabels.sampleTemplate}`}
        resetScrollPosition={Symbol("always reset scroll")}
      >
        <OverviewSection activeResult={activeResult} />
        <DetailsSection activeResult={activeResult} />
        <StepperPanel
          title="Access Permissions"
          sectionName="permissions"
          recordType="sampleTemplate"
        >
          <AccessPermissions
            fieldOwner={activeResult}
            additionalExplanation="This template will also be accessible to anyone who has access to a sample that has been created from it."
          />
        </StepperPanel>
        <FieldsSection activeResult={activeResult} />
      </Stepper>
    </SynchroniseFormSections>
  );
}
