import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import AlwaysNewFactory from "../../stores/models/Factory/AlwaysNewFactory";
import type InventoryBaseRecord from "../../stores/models/InventoryBaseRecord";
import { MixedInventoryBaseRecordCollection } from "../../stores/models/InventoryBaseRecordCollection";
import SubSampleModel from "../../stores/models/SubSampleModel";
import useStores from "../../stores/use-stores";
import type RsSet from "../../util/set";
import BatchEditingItemsTable from "../components/BatchEditing/BatchEditingItemsTable";
import FormWrapper from "../components/BatchEditing/FormWrapper";
import AccessPermissions from "../components/Fields/AccessPermissions";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import BatchName from "../components/Fields/BatchName";
import Description from "../components/Fields/Description";
import Image from "../components/Fields/Image";
import Tags from "../components/Fields/Tags";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";

function OverviewSection({
  collection,
  recordsCount,
}: {
  collection: MixedInventoryBaseRecordCollection;
  recordsCount: number;
}) {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      title={t("formSections.overview")}
      formSectionError={formSectionError}
      sectionName="overview"
      recordType="mixed"
    >
      <Image fieldOwner={collection} alt="What all of the items look like" />
      {collection.isFieldEditable("image") && (
        <Box sx={{ mt: 1 }}>
          <Alert severity="info">
            Please note, on slower network connections uploading large images may trigger an error.
          </Alert>
        </Box>
      )}
      <BatchName
        fieldOwner={collection}
        allowAlphabeticalSuffix={recordsCount <= 26}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "name", value)}
      />
    </StepperPanel>
  );
}

function DetailsSection({ collection }: { collection: MixedInventoryBaseRecordCollection }) {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      title={t("formSections.details")}
      formSectionError={formSectionError}
      sectionName="details"
      recordType="mixed"
    >
      <Description
        fieldOwner={collection}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "description", value)}
      />
      <Tags fieldOwner={collection} />
    </StepperPanel>
  );
}

type BatchFormArgs = {
  records: RsSet<InventoryBaseRecord>;
};

function BatchForm({ records }: BatchFormArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { searchStore } = useStores();

  const [collection, setCollection] = useState(new MixedInventoryBaseRecordCollection(records));
  useEffect(() => {
    setCollection(new MixedInventoryBaseRecordCollection(records));
  }, [records]);

  return (
    <FormWrapper
      recordType="mixed"
      titleText={`Batch editing ${records.size} items`}
      editableObject={searchStore.search.batchEditableInstance}
    >
      <StepperPanel title={t("formSections.information")} sectionName="information" recordType="mixed">
        <BatchEditingItemsTable records={records} label={t("formSections.itemsBeingEdited")} />
      </StepperPanel>
      <OverviewSection collection={collection} recordsCount={records.size} />
      <DetailsSection collection={collection} />
      <StepperPanel title={t("formSections.barcodes")} sectionName="barcodes" recordType="mixed">
        <BarcodesField fieldOwner={collection} factory={new AlwaysNewFactory()} />
      </StepperPanel>
      {!records.some((r) => r instanceof SubSampleModel) && (
        <StepperPanel title={t("formSections.accessPermissions")} sectionName="permissions" recordType="mixed">
          <AccessPermissions fieldOwner={collection} hideOwnersGroups />
        </StepperPanel>
      )}
    </FormWrapper>
  );
}

export default observer(BatchForm);
