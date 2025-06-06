import React, { type ReactNode } from "react";
import { observer } from "mobx-react-lite";
import RadioField, {
  type RadioOption,
} from "../../../components/Inputs/RadioField";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import { type SampleSource } from "../../../stores/definitions/Sample";
import BatchFormField from "../../components/Inputs/BatchFormField";

const OPTIONS: Array<RadioOption<"LAB_CREATED" | "VENDOR_SUPPLIED" | "OTHER">> =
  [
    {
      value: "LAB_CREATED",
      label: "Lab Created",
    },
    {
      value: "VENDOR_SUPPLIED",
      label: "Vendor Supplied",
    },
    {
      value: "OTHER",
      label: "Other",
    },
  ];

function Source<
  Fields extends {
    sampleSource: SampleSource;
  },
  FieldOwner extends HasEditableFields<Fields>
>({ fieldOwner }: { fieldOwner: FieldOwner }): ReactNode {
  const handleChange = ({
    target: { value },
  }: {
    target: {
      name: string;
      value: "LAB_CREATED" | "VENDOR_SUPPLIED" | "OTHER" | null;
    };
  }) => {
    if (value) {
      fieldOwner.setFieldsDirty({
        sampleSource: value,
      });
    }
  };

  return (
    <BatchFormField
      label="Source"
      value={fieldOwner.fieldValues.sampleSource}
      disabled={!fieldOwner.isFieldEditable("sampleSource")}
      asFieldset
      noValueLabel={fieldOwner.noValueLabel.sampleSource}
      canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
      setDisabled={(d) => {
        fieldOwner.setFieldEditable("sampleSource", d);
      }}
      doNotAttachIdToLabel
      renderInput={({ id: _id, error: _error, ...props }) => (
        <RadioField
          {...props}
          name="source"
          onChange={handleChange}
          options={OPTIONS}
        />
      )}
    />
  );
}

export default observer(Source);
