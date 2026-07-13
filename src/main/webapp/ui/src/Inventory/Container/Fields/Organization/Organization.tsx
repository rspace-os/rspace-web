import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { ContainerType } from "@/stores/definitions/container/types";
import RadioField, {
  OptionExplanation,
  OptionHeading,
  type RadioOption,
} from "../../../../components/Inputs/RadioField";
import type ContainerModel from "../../../../stores/models/ContainerModel";
import FormField from "../../../components/Inputs/FormField";
import LocationsImageField from "../LocationsImageField";
import GridDimensionsAndLayout from "./GridDimensionsAndLayout";

type OrganizationArgs = {
  container: ContainerModel;
};

function Organization({ container }: OrganizationArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const options: Array<RadioOption<ContainerType>> = [
    {
      value: "LIST",
      label: (
        <>
          <OptionHeading>{t("container.fields.organization.options.list.label")}</OptionHeading>
          <OptionExplanation>{t("container.fields.organization.options.list.explanation")}</OptionExplanation>
        </>
      ),
    },
    {
      value: "GRID",
      label: (
        <>
          <OptionHeading>{t("container.fields.organization.options.grid.label")}</OptionHeading>
          <OptionExplanation>{t("container.fields.organization.options.grid.explanation")}</OptionExplanation>
        </>
      ),
    },
    {
      value: "IMAGE",
      label: (
        <>
          <OptionHeading>{t("container.fields.organization.options.visual.label")}</OptionHeading>
          <OptionExplanation>{t("container.fields.organization.options.visual.explanation")}</OptionExplanation>
        </>
      ),
    },
  ];
  const handleChange = ({ target: { value } }: { target: { name: string; value: ContainerType | null } }) => {
    if (container && value) {
      container.setOrganization(value);
    }
  };

  return (
    <>
      <FormField
        label={t("container.fields.organization.type")}
        disabled={!container.isFieldEditable("organization")}
        value={container.cType}
        doNotAttachIdToLabel
        asFieldset
        renderInput={({ id: _id, error: _error, ...props }) => (
          <RadioField name="organization" onChange={handleChange} options={options} {...props} />
        )}
      />
      {container.cType === "GRID" && container.state !== "preview" && <GridDimensionsAndLayout container={container} />}
      {container.cType === "IMAGE" && container.isFieldEditable("locationsImage") && <LocationsImageField />}
    </>
  );
}

export default observer(Organization);
