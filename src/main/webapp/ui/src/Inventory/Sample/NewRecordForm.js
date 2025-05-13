// @flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../stores/use-stores";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { inventoryRecordTypeLabels } from "../../stores/definitions/BaseRecord";
import SampleModel from "../../stores/models/SampleModel";
import docLinks from "../../assets/DocLinks";
import { capitaliseJustFirstChar } from "../../util/Util";
import Fields from "./Fields/TemplateFields/Fields";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import {
  useFormSectionError,
  setFormSectionError,
  type FormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import NumberOfSubSamples from "./Fields/NumberOfSubSamples";
import Quantity from "./Fields/Quantity";
import NameField from "../components/Fields/Name";
import TemplateField from "./Fields/Template/Template";
import ImageField from "../components/Fields/Image";
import Expiry from "./Fields/Expiry";
import Source from "./Fields/Source";
import StorageTemperature from "./Fields/StorageTemperature";
import Description from "../components/Fields/Description";
import AttachmentsField from "../components/Fields/Attachments/Attachments";
import Tags from "../components/Fields/Tags";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import IdentifiersField from "../components/Fields/Identifiers/Identifiers";
import AccessPermissions from "../components/Fields/AccessPermissions";
import SynchroniseFormSections, {
  UnsynchroniseFormSections,
} from "../components/Stepper/SynchroniseFormSections";

const OverviewSection = observer(
  ({ activeResult }: { activeResult: SampleModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    /*
     * Name is a required field so it effectively starts in an error state.
     */
    React.useEffect(() => {
      setFormSectionError(formSectionError, "name", true);
      /* eslint-disable-next-line react-hooks/exhaustive-deps --
       * - formSectionError will not meaningfully change
       */
    }, []);

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
        <TemplateField />
        <ImageField
          fieldOwner={activeResult}
          alt="What the new sample looks like"
        />
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

/*
 * The rendering of subsample alias is pulled out here into a separate
 * component so that the whole form isn't re-rendered when the alias is changed
 * by setting the template. This extra re-rendering is both excessive and
 * caused the form to jump to the top as a unique value is passed to
 * resetScrollPosition with each re-rendering.
 */
const SubSamplesStepperPanel = observer(
  ({
    activeResult,
    children,
    formSectionError,
  }: {
    activeResult: SampleModel,
    children: Node,
    formSectionError: FormSectionError,
  }) => {
    return (
      <StepperPanel
        icon="sample"
        title={capitaliseJustFirstChar(activeResult.subSampleAlias.plural)}
        formSectionError={formSectionError}
        sectionName="subsamples"
        recordType="sample"
      >
        {children}
      </StepperPanel>
    );
  }
);

const SubSamplesSection = observer(
  ({ activeResult }: { activeResult: SampleModel }) => {
    const formSectionError = useFormSectionError({
      editing: activeResult.editing,
      globalId: activeResult.globalId,
    });

    return (
      <SubSamplesStepperPanel
        activeResult={activeResult}
        formSectionError={formSectionError}
      >
        <NumberOfSubSamples
          sample={activeResult}
          onErrorStateChange={(value) =>
            setFormSectionError(formSectionError, "numberOfSubsamples", value)
          }
        />
        <Quantity
          sample={activeResult}
          onErrorStateChange={(value) =>
            setFormSectionError(formSectionError, "quantity", value)
          }
        />
      </SubSamplesStepperPanel>
    );
  }
);

function NewRecordForm(): Node {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof SampleModel))
    throw new Error("ActiveResult must be a Sample");

  return (
    <SynchroniseFormSections>
      <Stepper
        helpLink={{
          link: docLinks.createSample,
          title: "Info on creating new samples.",
        }}
        titleText={`New ${inventoryRecordTypeLabels.sample}`}
        resetScrollPosition={Symbol("always reset scroll")}
      >
        <UnsynchroniseFormSections>
          <OverviewSection activeResult={activeResult} />
        </UnsynchroniseFormSections>
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
        <SubSamplesSection activeResult={activeResult} />
      </Stepper>
    </SynchroniseFormSections>
  );
}

export default (observer(NewRecordForm): ComponentType<{||}>);
