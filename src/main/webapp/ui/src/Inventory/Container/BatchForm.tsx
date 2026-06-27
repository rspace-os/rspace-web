import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import { type ReactNode, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import type ContainerModel from "../../stores/models/ContainerModel";
import { ContainerCollection } from "../../stores/models/ContainerModel";
import AlwaysNewFactory from "../../stores/models/Factory/AlwaysNewFactory";
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

function OverviewSection({ collection, recordsCount }: { collection: ContainerCollection; recordsCount: number }) {
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
      recordType="container"
    >
      <Image fieldOwner={collection} alt={`What all ${collection.size} containers look like`} />
      {collection.isFieldEditable("image") && (
        <Box sx={{ mt: 1 }}>
          <Alert severity="info">{t("container.batch.largeImageWarning")}</Alert>
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

function DetailsSection({ collection }: { collection: ContainerCollection }) {
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
      recordType="container"
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
  records: RsSet<ContainerModel>;
};

function BatchForm({ records }: BatchFormArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const { searchStore } = useStores();

  const [collection, setCollection] = useState(new ContainerCollection(records));
  useEffect(() => {
    setCollection(new ContainerCollection(records));
  }, [records]);

  return (
    <FormWrapper
      recordType="container"
      titleText={`Batch editing ${records.size} containers`}
      editableObject={searchStore.search.batchEditableInstance}
    >
      <StepperPanel title={t("formSections.information")} sectionName="information" recordType="container">
        <BatchEditingItemsTable records={records} label={t("formSections.containersBeingEdited")} />
      </StepperPanel>
      <OverviewSection collection={collection} recordsCount={records.size} />
      <DetailsSection collection={collection} />
      <StepperPanel title={t("formSections.barcodes")} sectionName="barcodes" recordType="container">
        <BarcodesField fieldOwner={collection} factory={new AlwaysNewFactory()} />
      </StepperPanel>
      <StepperPanel title={t("formSections.accessPermissions")} sectionName="permissions" recordType="container">
        <AccessPermissions fieldOwner={collection} hideOwnersGroups />
      </StepperPanel>
    </FormWrapper>
  );
}

export default observer(BatchForm);
