//@flow

import React, { type Node, type ComponentType } from "react";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import useStores from "../../stores/use-stores";
import { observer } from "mobx-react-lite";
import NameField from "../components/Fields/Name";
import TemplateField from "./Fields/Template/Template";
import StorageTemperature from "./Fields/StorageTemperature";
import Description from "../components/Fields/Description";
import Tags from "../components/Fields/Tags";
import Quantity from "./Fields/Quantity";
import Fields from "./Fields/TemplateFields/Fields";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import SubsampleListing from "./Content/SubsampleListing";
import ImageField from "../components/Fields/Image";
import Source from "./Fields/Source";
import Expiry from "./Fields/Expiry";
import AttachmentsField from "../components/Fields/Attachments/Attachments";
import IdentifiersField from "../components/Fields/Identifiers/Identifiers";
import SampleModel from "../../stores/models/SampleModel";
import { capitaliseJustFirstChar } from "../../util/Util";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import OwnerField from "../components/Fields/Owner";
import AccessPermissions from "../components/Fields/AccessPermissions";
import {
  useFormSectionError,
  setFormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import LimitedAccessAlert from "../components/LimitedAccessAlert";
import type { Person } from "../../stores/definitions/Person";
import SubsampleDetails from "./Content/SubsampleDetails";
import Typography from "@mui/material/Typography";

const OverviewSection = observer(
  ({ activeResult }: { activeResult: SampleModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
        icon="sample"
        title="Overview"
        sectionName="overview"
        formSectionError={formSectionError}
        recordType="sample"
      >
        <NameField
          fieldOwner={activeResult}
          record={activeResult}
          onErrorStateChange={(e) =>
            setFormSectionError(formSectionError, "name", e)
          }
        />
        <OwnerField fieldOwner={activeResult} />
        {activeResult.readAccessLevel !== "public" && (
          <>
            <TemplateField />
            <ImageField fieldOwner={activeResult} />
          </>
        )}
      </StepperPanel>
    );
  }
);

const DetailsSection = observer(
  ({ activeResult }: { activeResult: SampleModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
        icon="sample"
        title="Details"
        sectionName="details"
        formSectionError={formSectionError}
        recordType="sample"
      >
        <Quantity
          sample={activeResult}
          onErrorStateChange={(value) =>
            setFormSectionError(formSectionError, "quantity", value)
          }
        />
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

const MoreFieldsSection = observer(
  ({ activeResult }: { activeResult: SampleModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <StepperPanel
        icon="sample"
        title="Custom Fields"
        sectionName="customFields"
        formSectionError={formSectionError}
        recordType="sample"
      >
        <Fields
          onErrorStateChange={(field, value) =>
            setFormSectionError(formSectionError, field, value)
          }
          sample={activeResult}
        />
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

function Form(): Node {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof SampleModel))
    throw new Error("ActiveResult must be a Sample");
  if (!activeResult.owner) throw new Error("Sample does not have an owner");
  const owner: Person = activeResult.owner;

  return (
    <Stepper
      titleText={activeResult.name}
      resetScrollPosition={activeResult}
      factory={activeResult.factory}
    >
      <LimitedAccessAlert
        readAccessLevel={activeResult.readAccessLevel}
        owner={owner}
        whatLabel="sample"
      />
      <OverviewSection activeResult={activeResult} />
      {activeResult.readAccessLevel !== "public" && (
        <>
          <DetailsSection activeResult={activeResult} />
          <StepperPanel
            icon="sample"
            title="Barcodes"
            sectionName="barcodes"
            recordType="sample"
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
            icon="sample"
            title="Identifiers"
            sectionName="identifiers"
            recordType="sample"
          >
            <IdentifiersField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="sample"
            title="Attachments"
            sectionName="attachments"
            recordType="sample"
          >
            <AttachmentsField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="sample"
            title="Access Permissions"
            sectionName="permissions"
            recordType="sample"
          >
            <AccessPermissions
              fieldOwner={activeResult}
              additionalExplanation="Sample permission settings affect all of its subsamples, and cannot be set for individual subsamples."
            />
          </StepperPanel>
          <MoreFieldsSection activeResult={activeResult} />
          {activeResult.state === "preview" ? (
            <StepperPanel
              icon="subsample"
              title={`${
                activeResult.subSamples.length
              } ${capitaliseJustFirstChar(
                activeResult.subSamples.length === 1
                  ? activeResult.subSampleAlias.alias
                  : activeResult.subSampleAlias.plural
              )}`}
              sectionName="subsamples"
              recordType="sample"
            >
              {/*
               * We say "one of the {plural}" here instead of "a {alias}"
               * because adding the logic to get the grammar of "a" versus
               * "an" right would be too much of a pain.
               */}
              <Typography variant="body1">
                Tap one of the {activeResult.subSampleAlias.plural} in the
                search section to preview it below.
              </Typography>
              <SubsampleListing sample={activeResult} />
              <SubsampleDetails search={activeResult.search} />
            </StepperPanel>
          ) : null}
        </>
      )}
    </Stepper>
  );
}

export default (observer(Form): ComponentType<{||}>);
