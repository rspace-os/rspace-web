import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { Person } from "../../stores/definitions/Person";
import InstrumentTemplateModel from "../../stores/models/InstrumentTemplateModel";
import useStores from "../../stores/use-stores";
import AccessPermissions from "../components/Fields/AccessPermissions";
import AttachmentsField from "../components/Fields/Attachments/Attachments";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import DescriptionField from "../components/Fields/Description";
import ImageField from "../components/Fields/Image";
import NameField from "../components/Fields/Name";
import OwnerField from "../components/Fields/Owner";
import TagsField from "../components/Fields/Tags";
import HistoricalVersionAlert from "../components/HistoricalVersionAlert";
import LimitedAccessAlert from "../components/LimitedAccessAlert";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import CustomFields from "./Fields/CustomFields";
import InstrumentsList from "./Fields/InstrumentsList";

type OverviewSectionArgs = {
  activeResult: InstrumentTemplateModel;
};

const OverviewSection = observer(({ activeResult }: OverviewSectionArgs) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrumentTemplate"
      title={t("formSections.overview")}
      sectionName="overview"
      formSectionError={formSectionError}
      recordType="instrumentTemplate"
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
        <ImageField fieldOwner={activeResult} alt={t("instrumentTemplate.imageAlt")} />
      )}
    </StepperPanel>
  );
});

type DetailsSectionArgs = {
  activeResult: InstrumentTemplateModel;
};

const DetailsSection = observer(({ activeResult }: DetailsSectionArgs) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrumentTemplate"
      title={t("formSections.details")}
      sectionName="details"
      formSectionError={formSectionError}
      recordType="instrumentTemplate"
    >
      <DescriptionField
        fieldOwner={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
      />
      <TagsField fieldOwner={activeResult} />
    </StepperPanel>
  );
});

type ExtraFieldSectionArgs = {
  activeResult: InstrumentTemplateModel;
};

const CustomFieldSection = observer(({ activeResult }: ExtraFieldSectionArgs) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="instrumentTemplate"
      title={t("formSections.customFields")}
      sectionName="customFields"
      formSectionError={formSectionError}
      recordType="instrumentTemplate"
    >
      <CustomFields onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)} />
    </StepperPanel>
  );
});

function InstrumentTemplateForm(): ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentTemplateModel))
    throw new Error("ActiveResult must be an Instrument Template");
  if (!activeResult.owner) throw new Error("Instrument Template does not have an owner");
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
        whatLabel={t("recordTypes.instrumentTemplate.lower")}
        owner={owner}
      />
      <OverviewSection activeResult={activeResult} />
      {activeResult.readAccessLevel !== "public" && (
        <>
          <DetailsSection activeResult={activeResult} />
          <StepperPanel
            icon="instrumentTemplate"
            title={t("formSections.barcodes")}
            sectionName="barcodes"
            recordType="instrumentTemplate"
          >
            <BarcodesField fieldOwner={activeResult} factory={activeResult.factory} connectedItem={activeResult} />
          </StepperPanel>
        </>
      )}
      {activeResult.readAccessLevel === "full" && (
        <>
          <StepperPanel
            icon="instrumentTemplate"
            title={t("formSections.attachments")}
            sectionName="attachments"
            recordType="instrumentTemplate"
          >
            <AttachmentsField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="instrumentTemplate"
            title={t("formSections.accessPermissions")}
            sectionName="permissions"
            recordType="instrumentTemplate"
          >
            <AccessPermissions fieldOwner={activeResult} />
          </StepperPanel>
          <CustomFieldSection activeResult={activeResult} />
          {activeResult.state === "preview" && (
            <StepperPanel
              icon="instrumentTemplate"
              title={t("recordTypes.instrument.plural")}
              sectionName="instruments"
              recordType="instrumentTemplate"
            >
              <InstrumentsList />
            </StepperPanel>
          )}
        </>
      )}
    </Stepper>
  );
}

export default observer(InstrumentTemplateForm);
