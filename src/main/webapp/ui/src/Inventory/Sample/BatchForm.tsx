import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import AlwaysNewFactory from "../../stores/models/Factory/AlwaysNewFactory";
import type SampleModel from "../../stores/models/SampleModel";
import { SampleCollection } from "../../stores/models/SampleModel";
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
import Expiry from "./Fields/Expiry";
import Source from "./Fields/Source";
import StorageTemperature from "./Fields/StorageTemperature";

type OverviewSectionArgs = {
  collection: SampleCollection;
  recordsCount: number;
};

function OverviewSection({ collection, recordsCount }: OverviewSectionArgs) {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      icon="sample"
      title={t("formSections.overview")}
      formSectionError={formSectionError}
      sectionName="overview"
      recordType="sample"
    >
      <Image fieldOwner={collection} alt={t("sample.batch.imageAlt", { count: collection.size })} />
      {collection.isFieldEditable("image") && (
        <Box sx={{ mt: 1 }}>
          <Alert severity="info">{t("bulkAlerts.largeImageWarning")}</Alert>
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

type DetailsSectionArgs = {
  collection: SampleCollection;
};

function DetailsSection({ collection }: DetailsSectionArgs) {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      icon="sample"
      title={t("formSections.details")}
      formSectionError={formSectionError}
      sectionName="details"
      recordType="sample"
    >
      <Expiry
        fieldOwner={collection}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "expiry", value)}
      />
      <Source fieldOwner={collection} />
      <StorageTemperature
        fieldOwner={collection}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "temperature", value)}
      />
      <Description
        fieldOwner={collection}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
      />
      <Tags fieldOwner={collection} />
    </StepperPanel>
  );
}

type BatchFormArgs = {
  records: RsSet<SampleModel>;
};

function BatchForm({ records }: BatchFormArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { searchStore } = useStores();

  const [collection, setCollection] = useState(new SampleCollection(records));
  useEffect(() => {
    setCollection(new SampleCollection(records));
  }, [records]);

  return (
    <FormWrapper
      recordType="sample"
      titleText={t("sample.batch.title", { count: records.size })}
      editableObject={searchStore.search.batchEditableInstance}
    >
      <StepperPanel icon="sample" title={t("formSections.information")} sectionName="information" recordType="sample">
        <BatchEditingItemsTable records={records} label={t("formSections.samplesBeingEdited")} />
      </StepperPanel>
      <OverviewSection collection={collection} recordsCount={records.size} />
      <DetailsSection collection={collection} />
      <StepperPanel title={t("formSections.barcodes")} sectionName="barcodes" recordType="sample" icon="sample">
        <BarcodesField fieldOwner={collection} factory={new AlwaysNewFactory()} />
      </StepperPanel>
      <StepperPanel
        icon="sample"
        title={t("formSections.accessPermissions")}
        sectionName="permissions"
        recordType="sample"
      >
        <AccessPermissions
          fieldOwner={collection}
          hideOwnersGroups
          additionalExplanation={t("sample.permissionsExplanation")}
        />
      </StepperPanel>
    </FormWrapper>
  );
}

export default observer(BatchForm);
