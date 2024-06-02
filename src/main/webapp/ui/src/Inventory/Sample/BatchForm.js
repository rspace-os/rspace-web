//@flow

import React, {
  type Node,
  useState,
  useEffect,
  type ComponentType,
} from "react";
import useStores from "../../stores/use-stores";
import RsSet from "../../util/set";
import BatchEditingItemsTable from "../components/BatchEditing/BatchEditingItemsTable";
import FormWrapper from "../components/BatchEditing/FormWrapper";
import StepperPanel from "../components/Stepper/StepperPanel";
import Source from "./Fields/Source";
import Expiry from "./Fields/Expiry";
import Image from "../components/Fields/Image";
import { observer } from "mobx-react-lite";
import SampleModel, { SampleCollection } from "../../stores/models/SampleModel";
import BatchName from "../components/Fields/BatchName";
import Description from "../components/Fields/Description";
import Tags from "../components/Fields/Tags";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import AlwaysNewFactory from "../../stores/models/Factory/AlwaysNewFactory";
import StorageTemperature from "./Fields/StorageTemperature";
import {
  useFormSectionError,
  setFormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import AccessPermissions from "../components/Fields/AccessPermissions";

function OverviewSection({
  collection,
  recordsCount,
}: {
  collection: SampleCollection,
  recordsCount: number,
}) {
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      icon="sample"
      title="Overview"
      formSectionError={formSectionError}
      sectionName="overview"
      recordType="sample"
    >
      <Image fieldOwner={collection} />
      {collection.isFieldEditable("image") && (
        <Box mt={1}>
          <Alert severity="info">
            Please note, on slower network connections uploading large images
            may trigger an error.
          </Alert>
        </Box>
      )}
      <BatchName
        fieldOwner={collection}
        allowAlphabeticalSuffix={recordsCount <= 26}
        onErrorStateChange={(value) =>
          setFormSectionError(formSectionError, "name", value)
        }
      />
    </StepperPanel>
  );
}

function DetailsSection({ collection }: { collection: SampleCollection }) {
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      icon="sample"
      title="Details"
      formSectionError={formSectionError}
      sectionName="details"
      recordType="sample"
    >
      <Expiry
        fieldOwner={collection}
        onErrorStateChange={(value) =>
          setFormSectionError(formSectionError, "expiry", value)
        }
      />
      <Source fieldOwner={collection} />
      <StorageTemperature
        fieldOwner={collection}
        onErrorStateChange={(value) =>
          setFormSectionError(formSectionError, "temperature", value)
        }
      />
      <Description
        fieldOwner={collection}
        onErrorStateChange={(e) =>
          setFormSectionError(formSectionError, "description", e)
        }
      />
      <Tags fieldOwner={collection} />
    </StepperPanel>
  );
}

type BatchFormArgs = {|
  records: RsSet<SampleModel>,
|};

function BatchForm({ records }: BatchFormArgs): Node {
  const { searchStore } = useStores();

  const [collection, setCollection] = useState(new SampleCollection(records));
  useEffect(() => {
    setCollection(new SampleCollection(records));
  }, [records]);

  return (
    <FormWrapper
      recordType="sample"
      titleText={`Batch editing ${records.size} samples`}
      editableObject={searchStore.search.batchEditableInstance}
    >
      <StepperPanel
        icon="sample"
        title="Information"
        sectionName="information"
        recordType="sample"
      >
        <BatchEditingItemsTable
          records={records}
          label="Samples being edited"
        />
      </StepperPanel>
      <OverviewSection collection={collection} recordsCount={records.size} />
      <DetailsSection collection={collection} />
      <StepperPanel
        title="Barcodes"
        sectionName="barcodes"
        recordType="sample"
        icon="sample"
      >
        <BarcodesField
          fieldOwner={collection}
          factory={new AlwaysNewFactory()}
        />
      </StepperPanel>
      <StepperPanel
        icon="sample"
        title="Access Permissions"
        sectionName="permissions"
        recordType="sample"
      >
        <AccessPermissions
          fieldOwner={collection}
          hideOwnersGroups
          additionalExplanation="Sample permission settings affect all of its subsamples, and cannot be set for individual subsamples."
        />
      </StepperPanel>
    </FormWrapper>
  );
}

export default (observer(BatchForm): ComponentType<BatchFormArgs>);
