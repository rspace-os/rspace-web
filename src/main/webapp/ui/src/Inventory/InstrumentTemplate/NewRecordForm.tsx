import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import InstrumentTemplateModel from "../../stores/models/InstrumentTemplateModel";
import useStores from "../../stores/use-stores";
import AccessPermissions from "../components/Fields/AccessPermissions";
import DescriptionField from "../components/Fields/Description";
import ImageField from "../components/Fields/Image";
import NameField from "../components/Fields/Name";
import TagsField from "../components/Fields/Tags";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import SynchroniseFormSections, { UnsynchroniseFormSections } from "../components/Stepper/SynchroniseFormSections";
import CustomFields from "./Fields/CustomFields";

const OverviewSection = observer(({ activeResult }: { activeResult: InstrumentTemplateModel }) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  React.useEffect(() => {
    setFormSectionError(formSectionError, "name", true);
  }, []);

  return (
    <StepperPanel
      title={t("formSections.overview")}
      sectionName="overview"
      formSectionError={formSectionError}
      recordType="instrumentTemplate"
    >
      <NameField
        fieldOwner={activeResult}
        record={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "name", e)}
      />
      <ImageField fieldOwner={activeResult} alt={t("instrumentTemplate.newImageAlt")} />
    </StepperPanel>
  );
});

const DetailsSection = observer(({ activeResult }: { activeResult: InstrumentTemplateModel }) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
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

const CustomFieldsSection = observer(({ activeResult }: { activeResult: InstrumentTemplateModel }) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      title={t("formSections.customFields")}
      sectionName="customFields"
      formSectionError={formSectionError}
      recordType="instrumentTemplate"
    >
      <CustomFields onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)} />
    </StepperPanel>
  );
});

export default function NewRecordForm(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentTemplateModel))
    throw new Error("ActiveResult must be an Instrument Template");

  return (
    <SynchroniseFormSections>
      <Stepper
        helpLink={{
          link: helpDocsArticleUrl("5f0lakn5nl-create-an-instrument-template"),
          title: t("createNew.helpTitles.instrumentTemplate"),
        }}
        titleText={t("createNew.newInstrumentTemplate")}
        resetScrollPosition={Symbol("always reset scroll")}
      >
        <UnsynchroniseFormSections>
          <OverviewSection activeResult={activeResult} />
        </UnsynchroniseFormSections>
        <DetailsSection activeResult={activeResult} />
        <StepperPanel
          title={t("formSections.accessPermissions")}
          sectionName="permissions"
          recordType="instrumentTemplate"
        >
          <AccessPermissions fieldOwner={activeResult} />
        </StepperPanel>
        <CustomFieldsSection activeResult={activeResult} />
      </Stepper>
    </SynchroniseFormSections>
  );
}
