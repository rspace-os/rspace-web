import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { Person } from "../../stores/definitions/Person";
import InstrumentModel from "../../stores/models/InstrumentModel";
import useStores from "../../stores/use-stores";
import AttachmentsField from "../components/Fields/Attachments/Attachments";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import DescriptionField from "../components/Fields/Description";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import IdentifiersField from "../components/Fields/Identifiers/Identifiers";
import ImageField from "../components/Fields/Image";
import LocationField from "../components/Fields/Location";
import NameField from "../components/Fields/Name";
import OwnerField from "../components/Fields/Owner";
import TagsField from "../components/Fields/Tags";
import HistoricalVersionAlert from "../components/HistoricalVersionAlert";
import LimitedAccessAlert from "../components/LimitedAccessAlert";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import InstrumentTemplateField from "./Fields/InstrumentTemplateField";
import TemplateFields from "./Fields/TemplateFields";

type OverviewSectionArgs = {
  activeResult: InstrumentModel;
};

const OverviewSection = observer(({ activeResult }: OverviewSectionArgs) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrument"
      title={t("formSections.overview")}
      sectionName="overview"
      formSectionError={formSectionError}
      recordType="instrument"
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
          <InstrumentTemplateField />
          <LocationField fieldOwner={activeResult} />
          <ImageField fieldOwner={activeResult} alt={t("instrument.imageAlt")} />
        </>
      )}
    </StepperPanel>
  );
});

type DetailsSectionArgs = {
  activeResult: InstrumentModel;
};

const DetailsSection = observer(({ activeResult }: DetailsSectionArgs) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrument"
      title={t("formSections.details")}
      sectionName="details"
      formSectionError={formSectionError}
      recordType="instrument"
    >
      <DescriptionField
        fieldOwner={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
      />
      <TagsField fieldOwner={activeResult} />
    </StepperPanel>
  );
});

type CustomFieldSectionArgs = {
  activeResult: InstrumentModel;
};

const CustomFieldSection = observer(({ activeResult }: CustomFieldSectionArgs) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrument"
      title={t("formSections.customFields")}
      sectionName="customFields"
      formSectionError={formSectionError}
      recordType="instrument"
    >
      <TemplateFields
        onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
        instrument={activeResult}
      />
      <ExtraFields
        onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
        result={activeResult}
      />
    </StepperPanel>
  );
});

function InstrumentForm(): ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentModel))
    throw new Error("ActiveResult must be an Instrument");
  if (!activeResult.owner) throw new Error("Instrument does not have an owner");
  const owner: Person = activeResult.owner;

  return (
    <Stepper
      stickyAlert={activeResult.historicalVersion ? <HistoricalVersionAlert record={activeResult} /> : null}
      titleText={activeResult.name}
      resetScrollPosition={activeResult}
      factory={activeResult.factory}
    >
      <LimitedAccessAlert
        readAccessLevel={activeResult.readAccessLevel}
        whatLabel={t("recordTypes.instrument.lower")}
        owner={owner}
      />
      <OverviewSection activeResult={activeResult} />
      {activeResult.readAccessLevel !== "public" && (
        <>
          <DetailsSection activeResult={activeResult} />
          <StepperPanel
            icon="instrument"
            title={t("formSections.barcodes")}
            sectionName="barcodes"
            recordType="instrument"
          >
            <BarcodesField fieldOwner={activeResult} factory={activeResult.factory} connectedItem={activeResult} />
          </StepperPanel>
        </>
      )}
      {activeResult.readAccessLevel === "full" && (
        <>
          <StepperPanel
            icon="instrument"
            title={t("formSections.identifiers")}
            sectionName="identifiers"
            recordType="instrument"
          >
            <IdentifiersField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="instrument"
            title={t("formSections.attachments")}
            sectionName="attachments"
            recordType="instrument"
          >
            <AttachmentsField fieldOwner={activeResult} />
          </StepperPanel>
          <CustomFieldSection activeResult={activeResult} />
        </>
      )}
    </Stepper>
  );
}

export default observer(InstrumentForm);
