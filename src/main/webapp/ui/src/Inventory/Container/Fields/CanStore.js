//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import ContainerModel from "../../../stores/models/ContainerModel";
import ChoiceField, {
  type ChoiceOption,
} from "../../../components/Inputs/ChoiceField";
import FormField from "../../../components/Inputs/FormField";
import {
  mapPermissioned,
  orElseIfNoAccess,
} from "../../../stores/definitions/PermissionedData";

type CanStoreArgs = {|
  onErrorStateChange: (boolean) => void,
  container: ContainerModel,
|};

function CanStore({ onErrorStateChange, container }: CanStoreArgs): Node {
  const handleChange = ({
    target: { value },
  }: {
    target: {
      name: string,
      value: $ReadOnlyArray<"sample" | "container">,
      ...
    },
    ...
  }) => {
    container.setAttributesDirty({
      canStoreContainers: value.includes("container"),
      canStoreSamples: value.includes("sample"),
    });
    onErrorStateChange(
      [
        ...(container.canStoreContainers ? ["container"] : []),
        ...(container.canStoreSamples ? ["sample"] : []),
      ].length > 0
    );
  };

  const options: Array<ChoiceOption<"sample" | "container">> = [
    {
      value: "sample",
      label: "Subsamples",
      disabled: orElseIfNoAccess(
        mapPermissioned(
          container.contentSummary,
          ({ subSampleCount }) => subSampleCount > 0
        ),
        true
      ),
      editing: false,
    },
    {
      value: "container",
      label: "Containers",
      disabled: orElseIfNoAccess(
        mapPermissioned(
          container.contentSummary,
          ({ containerCount }) => containerCount > 0
        ),
        true
      ),
      editing: false,
    },
  ];

  const value = [
    ...(container.canStoreContainers ? ["container"] : []),
    ...(container.canStoreSamples ? ["sample"] : []),
  ];

  const valid = value.length > 0;
  const errorMessage = valid ? null : "Select at least one.";

  const editable = container.isFieldEditable("canStore");
  return (
    <FormField
      label="Can Store"
      helperText={errorMessage}
      error={!valid}
      disabled={!editable}
      value={value}
      doNotAttachIdToLabel
      asFieldset
      renderInput={({ id: _id, error: _error, ...props }) => (
        <ChoiceField
          {...props}
          name="canstore"
          onChange={handleChange}
          options={options}
        />
      )}
    />
  );
}

export default (observer(CanStore): ComponentType<CanStoreArgs>);
