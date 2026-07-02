import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import { type ReactNode, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import AlwaysNewFactory from "../../stores/models/Factory/AlwaysNewFactory";
import type SubSampleModel from "../../stores/models/SubSampleModel";
import { SubSampleCollection } from "../../stores/models/SubSampleModel";
import useStores from "../../stores/use-stores";
import type RsSet from "../../util/set";
import BatchEditingItemsTable from "../components/BatchEditing/BatchEditingItemsTable";
import FormWrapper from "../components/BatchEditing/FormWrapper";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import BatchName from "../components/Fields/BatchName";
import Description from "../components/Fields/Description";
import Image from "../components/Fields/Image";
import Tags from "../components/Fields/Tags";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import Quantity from "./Fields/Quantity";

type OverviewSectionArgs = {
  setOfSubSamples: SubSampleCollection;
  recordsCount: number;
};

function OverviewSection({ setOfSubSamples, recordsCount }: OverviewSectionArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      icon="subsample"
      title={t("formSections.overview")}
      formSectionError={formSectionError}
      sectionName="overview"
      recordType="subSample"
    >
      <Image fieldOwner={setOfSubSamples} alt={t("subsample.batch.imageAlt", { count: setOfSubSamples.size })} />
      {setOfSubSamples.isFieldEditable("image") && (
        <Box sx={{ mt: 1 }}>
          <Alert severity="info">{t("subsample.batch.largeImageWarning")}</Alert>
        </Box>
      )}
      <BatchName
        fieldOwner={setOfSubSamples}
        allowAlphabeticalSuffix={recordsCount <= 26}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "name", value)}
      />
    </StepperPanel>
  );
}

type DetailsSectionArgs = {
  setOfSubSamples: SubSampleCollection;
};

function DetailsSection({ setOfSubSamples }: DetailsSectionArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: true,
    globalId: null,
  });

  return (
    <StepperPanel
      icon="subsample"
      title={t("formSections.details")}
      formSectionError={formSectionError}
      sectionName="details"
      recordType="subSample"
    >
      {setOfSubSamples.sameQuantityUnits ? (
        <Quantity
          fieldOwner={setOfSubSamples}
          quantityCategory={setOfSubSamples.quantityCategory}
          onErrorStateChange={(value) => setFormSectionError(formSectionError, "quantity", value)}
        />
      ) : (
        <Alert severity="warning">{t("subsample.batch.quantityMixedUnitsWarning")}</Alert>
      )}
      <Description
        fieldOwner={setOfSubSamples}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
      />
      <Tags fieldOwner={setOfSubSamples} />
    </StepperPanel>
  );
}

type BatchFormArgs = {
  records: RsSet<SubSampleModel>;
};

function BatchForm({ records }: BatchFormArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const { searchStore } = useStores();

  const [setOfSubSamples, setSetOfSubSamples] = useState(new SubSampleCollection(records));
  useEffect(() => {
    setSetOfSubSamples(new SubSampleCollection(records));
  }, [records]);

  return (
    <FormWrapper
      recordType="subSample"
      titleText={t("subsample.batch.title", { count: records.size })}
      editableObject={searchStore.search.batchEditableInstance}
    >
      <StepperPanel
        icon="subsample"
        title={t("formSections.information")}
        sectionName="information"
        recordType="subSample"
      >
        <BatchEditingItemsTable records={records} label={t("subsample.batch.itemsTableLabel")} />
      </StepperPanel>
      <OverviewSection setOfSubSamples={setOfSubSamples} recordsCount={records.size} />
      <DetailsSection setOfSubSamples={setOfSubSamples} />
      <StepperPanel icon="subsample" title={t("formSections.barcodes")} sectionName="barcodes" recordType="subSample">
        <BarcodesField fieldOwner={setOfSubSamples} factory={new AlwaysNewFactory()} />
      </StepperPanel>
    </FormWrapper>
  );
}

export default observer(BatchForm);
