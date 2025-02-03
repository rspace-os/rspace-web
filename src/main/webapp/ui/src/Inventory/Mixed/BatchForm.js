//@flow

import React, {
  type Node,
  type ComponentType,
  useState,
  useEffect,
} from "react";
import useStores from "../../stores/use-stores";
import BatchEditingItemsTable from "../components/BatchEditing/BatchEditingItemsTable";
import FormWrapper from "../components/BatchEditing/FormWrapper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { MixedResultCollection } from "../../stores/models/ResultCollection";
import BatchName from "../components/Fields/BatchName";
import Image from "../components/Fields/Image";
import Description from "../components/Fields/Description";
import Tags from "../components/Fields/Tags";
import { observer } from "mobx-react-lite";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import AlwaysNewFactory from "../../stores/models/Factory/AlwaysNewFactory";
import {
  useFormSectionError,
  setFormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import AccessPermissions from "../components/Fields/AccessPermissions";
import SubSampleModel from "../../stores/models/SubSampleModel";
import Result from "../../stores/models/Result";
import RsSet from "../../util/set";

function OverviewSection({
  collection,
  recordsCount,
}: {
  collection: MixedResultCollection,
  recordsCount: number,
}) {
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      title="Overview"
      formSectionError={formSectionError}
      sectionName="overview"
      recordType="mixed"
    >
      <Image fieldOwner={collection} alt="What all of the items look like" />
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

function DetailsSection({ collection }: { collection: MixedResultCollection }) {
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      title="Details"
      formSectionError={formSectionError}
      sectionName="details"
      recordType="mixed"
    >
      <Description
        fieldOwner={collection}
        onErrorStateChange={(value) =>
          setFormSectionError(formSectionError, "description", value)
        }
      />
      <Tags fieldOwner={collection} />
    </StepperPanel>
  );
}

type BatchFormArgs = {|
  records: RsSet<Result>,
|};

function BatchForm({ records }: BatchFormArgs): Node {
  const { searchStore } = useStores();

  const [collection, setCollection] = useState(
    new MixedResultCollection(records)
  );
  useEffect(() => {
    setCollection(new MixedResultCollection(records));
  }, [records]);

  return (
    <FormWrapper
      recordType="mixed"
      titleText={`Batch editing ${records.size} items`}
      editableObject={searchStore.search.batchEditableInstance}
    >
      <StepperPanel
        title="Information"
        sectionName="information"
        recordType="mixed"
      >
        <BatchEditingItemsTable records={records} label="Items being edited" />
      </StepperPanel>
      <OverviewSection collection={collection} recordsCount={records.size} />
      <DetailsSection collection={collection} />
      <StepperPanel title="Barcodes" sectionName="barcodes" recordType="mixed">
        <BarcodesField
          fieldOwner={collection}
          factory={new AlwaysNewFactory()}
        />
      </StepperPanel>
      {!records.some((r) => r instanceof SubSampleModel) && (
        <StepperPanel
          title="Access Permissions"
          sectionName="permissions"
          recordType="mixed"
        >
          <AccessPermissions fieldOwner={collection} hideOwnersGroups />
        </StepperPanel>
      )}
    </FormWrapper>
  );
}

export default (observer(BatchForm): ComponentType<BatchFormArgs>);
