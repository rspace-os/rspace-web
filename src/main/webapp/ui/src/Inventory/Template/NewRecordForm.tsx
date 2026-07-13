import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import TemplateModel from "../../stores/models/TemplateModel";
import useStores from "../../stores/use-stores";
import AccessPermissions from "../components/Fields/AccessPermissions";
import Description from "../components/Fields/Description";
import ImageField from "../components/Fields/Image";
import NameField from "../components/Fields/Name";
import Tags from "../components/Fields/Tags";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import SynchroniseFormSections, { UnsynchroniseFormSections } from "../components/Stepper/SynchroniseFormSections";
import Expiry from "../Sample/Fields/Expiry";
import Source from "../Sample/Fields/Source";
import StorageTemperature from "../Sample/Fields/StorageTemperature";
import SubSampleAlias from "./Fields/Alias";
import Fields from "./Fields/CustomFields";
import QuantityUnits from "./Fields/QuantityUnits";

const OverviewSection = observer(({ activeResult }: { activeResult: TemplateModel }) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  /*
   * Name is a required field so it effectively starts in an error state.
   */
  React.useEffect(() => {
    setFormSectionError(formSectionError, "name", true);
  }, []);

  return (
    <StepperPanel
      title={t("formSections.overview")}
      sectionName="overview"
      formSectionError={formSectionError}
      recordType="sampleTemplate"
    >
      <NameField
        fieldOwner={activeResult}
        record={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "name", e)}
      />
      <ImageField fieldOwner={activeResult} alt={t("template.newImageAlt")} />
    </StepperPanel>
  );
});

const DetailsSection = observer(({ activeResult }: { activeResult: TemplateModel }) => {
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
      recordType="sampleTemplate"
    >
      <Expiry
        fieldOwner={activeResult}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "expiry", value)}
      />
      <Source fieldOwner={activeResult} />
      <StorageTemperature
        fieldOwner={activeResult}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "temperature", value)}
      />
      <SubSampleAlias
        fieldOwner={activeResult}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "alias", value)}
      />
      <QuantityUnits />
      <Description
        fieldOwner={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
      />
      <Tags fieldOwner={activeResult} />
    </StepperPanel>
  );
});

const FieldsSection = observer(({ activeResult }: { activeResult: TemplateModel }) => {
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
      recordType="sampleTemplate"
    >
      <Fields onErrorStateChange={(name, value) => setFormSectionError(formSectionError, name, value)} />
    </StepperPanel>
  );
});

export default function NewRecordForm(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel)) throw new Error("ActiveResult must be a Template");

  return (
    <SynchroniseFormSections>
      <Stepper
        helpLink={{
          link: helpDocsArticleUrl("createTemplate"),
          title: t("createNew.helpTitles.template"),
        }}
        titleText={t("createNew.newTemplate")}
        resetScrollPosition={Symbol("always reset scroll")}
      >
        <UnsynchroniseFormSections>
          <OverviewSection activeResult={activeResult} />
        </UnsynchroniseFormSections>
        <DetailsSection activeResult={activeResult} />
        <StepperPanel title={t("formSections.accessPermissions")} sectionName="permissions" recordType="sampleTemplate">
          <AccessPermissions fieldOwner={activeResult} additionalExplanation={t("template.permissionsExplanation")} />
        </StepperPanel>
        <FieldsSection activeResult={activeResult} />
      </Stepper>
    </SynchroniseFormSections>
  );
}
