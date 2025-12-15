import React, { type ReactNode, useState, useEffect } from "react";
import useStores from "../../stores/use-stores";
import RsSet from "../../util/set";
import BatchEditingItemsTable from "../components/BatchEditing/BatchEditingItemsTable";
import FormWrapper from "../components/BatchEditing/FormWrapper";
import StepperPanel from "../components/Stepper/StepperPanel";
import ContainerModel, {
  ContainerCollection,
} from "../../stores/models/ContainerModel";
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

function OverviewSection({
  collection,
  recordsCount,
}: {
  collection: ContainerCollection;
  recordsCount: number;
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
      recordType="container"
    >
      <Image
        fieldOwner={collection}
        alt={`What all ${collection.size} containers look like`}
      />
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

function DetailsSection({ collection }: { collection: ContainerCollection }) {
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      title="Details"
      formSectionError={formSectionError}
      sectionName="details"
      recordType="container"
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

type BatchFormArgs = {
  records: RsSet<ContainerModel>;
};

function BatchForm({ records }: BatchFormArgs): ReactNode {
  const { searchStore } = useStores();

  const [collection, setCollection] = useState(
    new ContainerCollection(records)
  );
  useEffect(() => {
    setCollection(new ContainerCollection(records));
  }, [records]);

  return (
    <FormWrapper
      recordType="container"
      titleText={`Batch editing ${records.size} containers`}
      editableObject={searchStore.search.batchEditableInstance}
    >
      <StepperPanel
        title="Information"
        sectionName="information"
        recordType="container"
      >
        <BatchEditingItemsTable
          records={records}
          label="Containers being edited"
        />
      </StepperPanel>
      <OverviewSection collection={collection} recordsCount={records.size} />
      <DetailsSection collection={collection} />
      <StepperPanel
        title="Barcodes"
        sectionName="barcodes"
        recordType="container"
      >
        <BarcodesField
          fieldOwner={collection}
          factory={new AlwaysNewFactory()}
        />
      </StepperPanel>
      <StepperPanel
        title="Access Permissions"
        sectionName="permissions"
        recordType="container"
      >
        <AccessPermissions fieldOwner={collection} hideOwnersGroups />
      </StepperPanel>
    </FormWrapper>
  );
}

export default observer(BatchForm);
