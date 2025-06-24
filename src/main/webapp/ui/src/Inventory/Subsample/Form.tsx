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
import Notes from "./Fields/Notes/Notes";
import Quantity from "./Fields/Quantity";
import { observer } from "mobx-react-lite";
import React, { type ReactNode } from "react";
import SubSampleModel from "../../stores/models/SubSampleModel";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import OwnerField from "../components/Fields/Owner";
import SampleField from "../components/Fields/Sample";
import LocationField from "../components/Fields/Location";
import {
  useFormSectionError,
  setFormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import LimitedAccessAlert from "../components/LimitedAccessAlert";
import Typography from "@mui/material/Typography";
import { RecordLink } from "../components/RecordLink";
import Source from "../Sample/Fields/Source";
import StorageTemperature from "../Sample/Fields/StorageTemperature";
import Expiry from "../Sample/Fields/Expiry";
import TotalQuantity from "../Sample/Fields/Quantity";
import { Heading, HeadingContext } from "../../components/DynamicHeadingLevel";
import Fields from "../Sample/Fields/TemplateFields/Fields";
import { useTheme } from "@mui/material/styles";
import RecordTypeIcon from "../../components/RecordTypeIcon";
import { type Person } from "../../stores/definitions/Person";

type OverviewSectionArgs = {
  activeResult: SubSampleModel;
};

const OverviewSection = observer(({ activeResult }: OverviewSectionArgs) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="subsample"
      title="Overview"
      sectionName="overview"
      formSectionError={formSectionError}
      recordType="subSample"
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
          <SampleField fieldOwner={activeResult} />
          <LocationField fieldOwner={activeResult} />
          <ImageField
            fieldOwner={activeResult}
            alt="What the subsample looks like"
          />
        </>
      )}
    </StepperPanel>
  );
});

type DetailsSectionArgs = {
  activeResult: SubSampleModel;
};

const DetailsSection = observer(({ activeResult }: DetailsSectionArgs) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="subsample"
      title="Details"
      sectionName="details"
      formSectionError={formSectionError}
      recordType="subSample"
    >
      <Quantity
        fieldOwner={activeResult}
        quantityCategory={activeResult.quantityCategory}
        onErrorStateChange={(value) =>
          setFormSectionError(formSectionError, "quantity", value)
        }
        parentSample={activeResult.sample}
      />
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

type SampleFieldsSectionArgs = {
  activeResult: SubSampleModel;
};

/**
 * The fields of the parent sample are shown in read-only view in both preview
 * and edit mode so that users may quickly retrieve information about the whole
 * sample, especially when editing the subsample. These fields are read-only by
 * virtue of the fact that the operation that puts the subsample
 * (`activeResult`) into edit mode has no effect on its `sample` property.
 */
const SampleFieldsSection = observer(
  ({ activeResult }: SampleFieldsSectionArgs) => {
    const theme = useTheme();
    return (
      <StepperPanel
        icon="sample"
        thickBorder
        title="Sample Fields"
        sectionName="sampleFields"
        /*
         * `formSectionError` is not necessary because these fields will always
         * be read-only, hence also why all of the `onErrorStateChange` props do
         * nothing
         */
        recordType="subSample"
      >
        <Heading
          variant="h6"
          sx={{
            borderBottom: theme.borders.card,
            lineHeight: 1.4,
            pl: 0.5,
          }}
        >
          <RecordTypeIcon
            record={{
              recordTypeLabel: "SAMPLE",
              iconName: "sample",
            }}
            style={{
              transform: "scale(0.8)",
            }}
          />{" "}
          Parent Sample
        </Heading>
        <Typography variant="body2">
          These fields belong to <RecordLink record={activeResult.sample} />,
          the parent sample of this {activeResult.alias.alias}. To edit these
          fields, please edit the sample directly.
        </Typography>
        <Heading
          variant="h6"
          sx={{
            borderBottom: theme.borders.card,
            lineHeight: 1.4,
            pl: 0.5,
          }}
        >
          <RecordTypeIcon
            record={{
              recordTypeLabel: "SAMPLE",
              iconName: "sample",
            }}
            style={{
              transform: "scale(0.8)",
            }}
          />{" "}
          Details
        </Heading>
        <HeadingContext>
          <TotalQuantity
            sample={activeResult.sample}
            onErrorStateChange={() => {}}
          />
          <Expiry
            fieldOwner={activeResult.sample}
            onErrorStateChange={() => {}}
          />
          <Source fieldOwner={activeResult.sample} />
          <StorageTemperature
            fieldOwner={activeResult.sample}
            onErrorStateChange={() => {}}
          />
          <DescriptionField
            fieldOwner={activeResult.sample}
            onErrorStateChange={() => {}}
          />
          <TagsField fieldOwner={activeResult.sample} />
        </HeadingContext>

        <Heading
          variant="h6"
          sx={{
            borderBottom: theme.borders.card,
            lineHeight: 1.4,
            pl: 0.5,
          }}
        >
          <RecordTypeIcon
            record={{
              recordTypeLabel: "SAMPLE",
              iconName: "sample",
            }}
            style={{
              transform: "scale(0.8)",
            }}
          />{" "}
          Custom Fields
        </Heading>
        <HeadingContext>
          <Fields sample={activeResult.sample} onErrorStateChange={() => {}} />
          <ExtraFields
            onErrorStateChange={() => {}}
            result={activeResult.sample}
          />
        </HeadingContext>
      </StepperPanel>
    );
  }
);

type ExtaFieldSectionArgs = {
  activeResult: SubSampleModel;
};

const ExtaFieldSection = observer(({ activeResult }: ExtaFieldSectionArgs) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="subsample"
      title="Custom Fields"
      sectionName="customFields"
      formSectionError={formSectionError}
      recordType="subSample"
    >
      <ExtraFields
        onErrorStateChange={(field, value) =>
          setFormSectionError(formSectionError, field, value)
        }
        result={activeResult}
      />
    </StepperPanel>
  );
});

type NotesSectionArgs = {
  activeResult: SubSampleModel;
};

const NotesSection = observer(({ activeResult }: NotesSectionArgs) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="subsample"
      title="Notes"
      sectionName="notes"
      formSectionError={formSectionError}
      recordType="subSample"
    >
      <Notes
        record={activeResult}
        onErrorStateChange={(value) =>
          setFormSectionError(formSectionError, "notes", value)
        }
        hideLabel
      />
    </StepperPanel>
  );
});

function SubSampleForm(): ReactNode {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof SubSampleModel))
    throw new Error("ActiveResult must be a SubSample");
  if (!activeResult.owner) throw new Error("Subsample does not have an owner");
  const owner: Person = activeResult.owner;

  return (
    <Stepper
      titleText={activeResult.name}
      resetScrollPosition={activeResult}
      factory={activeResult.factory}
    >
      <LimitedAccessAlert
        readAccessLevel={activeResult.readAccessLevel}
        whatLabel="subsample, or its parent sample"
        owner={owner}
      />
      {activeResult.readAccessLevel === "full" && (
        <SampleFieldsSection activeResult={activeResult} />
      )}
      <OverviewSection activeResult={activeResult} />
      {activeResult.readAccessLevel !== "public" && (
        <>
          <DetailsSection activeResult={activeResult} />
          <StepperPanel
            icon="subsample"
            title="Barcodes"
            sectionName="barcodes"
            recordType="subSample"
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
            icon="subsample"
            title="Identifiers"
            sectionName="identifiers"
            recordType="subSample"
          >
            <IdentifiersField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="subsample"
            title="Attachments"
            sectionName="attachments"
            recordType="subSample"
          >
            <AttachmentsField fieldOwner={activeResult} />
          </StepperPanel>
          <ExtaFieldSection activeResult={activeResult} />
          <NotesSection activeResult={activeResult} />
        </>
      )}
    </Stepper>
  );
}

export default observer(SubSampleForm);
