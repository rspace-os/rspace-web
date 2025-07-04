import React from "react";
import { observer } from "mobx-react-lite";
import LocationsImageField from "../LocationsImageField";
import GridDimensionsAndLayout from "./GridDimensionsAndLayout";
import ContainerModel from "../../../../stores/models/ContainerModel";
import RadioField, {
  type RadioOption,
  OptionHeading,
  OptionExplanation,
} from "../../../../components/Inputs/RadioField";
import { type ContainerType } from "../../../../stores/definitions/Container";
import FormField from "../../../../components/Inputs/FormField";

const OPTIONS: Array<RadioOption<ContainerType>> = [
  {
    value: "LIST",
    label: (
      <>
        <OptionHeading>List</OptionHeading>
        <OptionExplanation>
          Generic container for storing unordered content.
        </OptionExplanation>
      </>
    ),
  },
  {
    value: "GRID",
    label: (
      <>
        <OptionHeading>Grid</OptionHeading>
        <OptionExplanation>
          Two-dimensional container with rows and columns e.g. a well plate.
        </OptionExplanation>
      </>
    ),
  },
  {
    value: "IMAGE",
    label: (
      <>
        <OptionHeading>Visual</OptionHeading>
        <OptionExplanation>
          Container showing exact locations on an image background.
        </OptionExplanation>
      </>
    ),
  },
];

type OrganizationArgs = {
  container: ContainerModel;
};

function Organization({ container }: OrganizationArgs): React.ReactNode {
  const handleChange = ({
    target: { value },
  }: {
    target: { name: string; value: ContainerType | null };
  }) => {
    if (container && value) {
      container.setOrganization(value);
    }
  };

  return (
    <>
      <FormField
        label="Type"
        disabled={!container.isFieldEditable("organization")}
        value={container.cType}
        doNotAttachIdToLabel
        asFieldset
        renderInput={({ id: _id, error: _error, ...props }) => (
          <RadioField
            name="organization"
            onChange={handleChange}
            options={OPTIONS}
            {...props}
          />
        )}
      />
      {container.cType === "GRID" && container.state !== "preview" && (
        <GridDimensionsAndLayout container={container} />
      )}
      {container.cType === "IMAGE" &&
        container.isFieldEditable("locationsImage") && <LocationsImageField />}
    </>
  );
}

export default observer(Organization);
