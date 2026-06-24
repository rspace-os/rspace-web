import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import ChoiceField, { type ChoiceOption } from "../../../components/Inputs/ChoiceField";
import { mapPermissioned, orElseIfNoAccess } from "../../../stores/definitions/PermissionedData";
import type ContainerModel from "../../../stores/models/ContainerModel";
import FormField from "../../components/Inputs/FormField";

type CanStoreArgs = {
  onErrorStateChange: (hasSelection: boolean) => void;
  container: ContainerModel;
};

function CanStore({ onErrorStateChange, container }: CanStoreArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const handleChange = ({
    target: { value },
  }: {
    target: {
      name: string;
      value: ReadonlyArray<"sample" | "container">;
    };
  }) => {
    container.setAttributesDirty({
      canStoreContainers: value.includes("container"),
      canStoreSamples: value.includes("sample"),
    });
    onErrorStateChange(
      [...(container.canStoreContainers ? ["container"] : []), ...(container.canStoreSamples ? ["sample"] : [])]
        .length > 0,
    );
  };

  const options: Array<ChoiceOption<"sample" | "container">> = [
    {
      value: "sample",
      label: t("container.fields.canStore.subsamples"),
      disabled: orElseIfNoAccess(
        mapPermissioned(container.contentSummary, ({ subSampleCount }) => subSampleCount > 0),
        true,
      ),
      editing: false,
    },
    {
      value: "container",
      label: t("container.fields.canStore.containers"),
      disabled: orElseIfNoAccess(
        mapPermissioned(container.contentSummary, ({ containerCount }) => containerCount > 0),
        true,
      ),
      editing: false,
    },
  ];

  const value: ReadonlyArray<"sample" | "container"> = [
    ...(container.canStoreContainers ? ["container" as const] : []),
    ...(container.canStoreSamples ? ["sample" as const] : []),
  ];

  const valid = value.length > 0;
  const errorMessage = valid ? null : t("container.fields.canStore.selectAtLeastOne");

  const editable = container.isFieldEditable("canStore");
  return (
    <FormField
      label={t("container.fields.canStore.label")}
      helperText={errorMessage}
      error={!valid}
      disabled={!editable}
      value={value}
      doNotAttachIdToLabel
      asFieldset
      renderInput={({ id: _id, error: _error, ...props }) => (
        <ChoiceField {...props} name="canstore" onChange={handleChange} options={options} />
      )}
    />
  );
}

export default observer(CanStore);
