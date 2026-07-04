import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import { helpDocsArticleUrl } from "@/modules/common/i18n/TransRichText";
import SampleModel from "../../stores/models/SampleModel";
import useStores from "../../stores/use-stores";
import { capitaliseJustFirstChar } from "../../util/Util";
import AccessPermissions from "../components/Fields/AccessPermissions";
import AttachmentsField from "../components/Fields/Attachments/Attachments";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import Description from "../components/Fields/Description";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import IdentifiersField from "../components/Fields/Identifiers/Identifiers";
import ImageField from "../components/Fields/Image";
import NameField from "../components/Fields/Name";
import Tags from "../components/Fields/Tags";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import {
  type FormSectionError,
  setFormSectionError,
  useFormSectionError,
} from "../components/Stepper/StepperPanelHeader";
import SynchroniseFormSections, { UnsynchroniseFormSections } from "../components/Stepper/SynchroniseFormSections";
import Expiry from "./Fields/Expiry";
import NumberOfSubSamples from "./Fields/NumberOfSubSamples";
import Quantity from "./Fields/Quantity";
import Source from "./Fields/Source";
import StorageTemperature from "./Fields/StorageTemperature";
import TemplateField from "./Fields/Template/Template";
import Fields from "./Fields/TemplateFields/Fields";

const OverviewSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
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
      icon="sample"
      title={t("formSections.overview")}
      sectionName="overview"
      formSectionError={formSectionError}
      recordType="sample"
    >
      <NameField
        fieldOwner={activeResult}
        record={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "name", e)}
      />
      <TemplateField />
      <ImageField fieldOwner={activeResult} alt={t("sample.newImageAlt")} />
    </StepperPanel>
  );
});

const DetailsSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="sample"
      title={t("formSections.details")}
      sectionName="details"
      formSectionError={formSectionError}
      recordType="sample"
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
      <Description
        fieldOwner={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
      />
      <Tags fieldOwner={activeResult} />
    </StepperPanel>
  );
});

const MoreFieldsSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="sample"
      title={t("formSections.customFields")}
      sectionName="customFields"
      formSectionError={formSectionError}
      recordType="sample"
    >
      <Fields
        onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
        sample={activeResult}
      />
      <ExtraFields
        onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
        result={activeResult}
      />
    </StepperPanel>
  );
});

/*
 * The rendering of subsample alias is pulled out here into a separate
 * component so that the whole form isn't re-rendered when the alias is changed
 * by setting the template. This extra re-rendering is both excessive and
 * caused the form to jump to the top as a unique value is passed to
 * resetScrollPosition with each re-rendering.
 */
const SubSamplesStepperPanel = observer(
  ({
    activeResult,
    children,
    formSectionError,
  }: {
    activeResult: SampleModel;
    children: React.ReactNode;
    formSectionError: FormSectionError;
  }) => {
    return (
      <StepperPanel
        icon="sample"
        title={capitaliseJustFirstChar(activeResult.subSampleAlias.plural)}
        formSectionError={formSectionError}
        sectionName="subsamples"
        recordType="sample"
      >
        {children}
      </StepperPanel>
    );
  },
);

const SubSamplesSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <SubSamplesStepperPanel activeResult={activeResult} formSectionError={formSectionError}>
      <NumberOfSubSamples
        sample={activeResult}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "numberOfSubsamples", value)}
      />
      <Quantity
        sample={activeResult}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "quantity", value)}
      />
    </SubSamplesStepperPanel>
  );
});

function NewRecordForm(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof SampleModel)) throw new Error("ActiveResult must be a Sample");

  return (
    <SynchroniseFormSections>
      <Stepper
        helpLink={{
          link: helpDocsArticleUrl("gb3r1lgm5g-create-a-sample"),
          title: t("createNew.helpTitles.sample"),
        }}
        titleText={t("createNew.newSample")}
        resetScrollPosition={Symbol("always reset scroll")}
      >
        <UnsynchroniseFormSections>
          <OverviewSection activeResult={activeResult} />
        </UnsynchroniseFormSections>
        <DetailsSection activeResult={activeResult} />
        <StepperPanel icon="sample" title={t("formSections.barcodes")} sectionName="barcodes" recordType="sample">
          <BarcodesField fieldOwner={activeResult} factory={activeResult.factory} connectedItem={activeResult} />
        </StepperPanel>
        <StepperPanel icon="sample" title={t("formSections.identifiers")} sectionName="identifiers" recordType="sample">
          <IdentifiersField fieldOwner={activeResult} />
        </StepperPanel>
        <StepperPanel icon="sample" title={t("formSections.attachments")} sectionName="attachments" recordType="sample">
          <AttachmentsField fieldOwner={activeResult} />
        </StepperPanel>
        <StepperPanel
          icon="sample"
          title={t("formSections.accessPermissions")}
          sectionName="permissions"
          recordType="sample"
        >
          <AccessPermissions fieldOwner={activeResult} additionalExplanation={t("sample.permissionsExplanation")} />
        </StepperPanel>
        <MoreFieldsSection activeResult={activeResult} />
        <SubSamplesSection activeResult={activeResult} />
      </Stepper>
    </SynchroniseFormSections>
  );
}

export default observer(NewRecordForm);
