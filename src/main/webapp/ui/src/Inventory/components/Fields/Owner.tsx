import React from "react";
import { type HasUneditableFields } from "../../../stores/definitions/Editable";
import UserDetails from "../UserDetails";
import { type Person } from "../../../stores/definitions/Person";
import NoValue from "../../../components/NoValue";
import FormField from "../../../components/Inputs/FormField";
import Box from "@mui/material/Box";

export default function Owner<
  Fields extends {
    owner: Person | null;
  },
  FieldOwner extends HasUneditableFields<Fields>
>({ fieldOwner }: { fieldOwner: FieldOwner }): React.ReactNode {
  const owner: Person | null = fieldOwner.fieldValues.owner;

  const Content = () => {
    if (!owner)
      return (
        <NoValue label={fieldOwner.noValueLabel.owner ?? "Unknown Owner"} />
      );
    const { id, fullName } = owner;
    return (
      <Box>
        <UserDetails
          userId={id}
          fullName={fullName}
          position={["bottom", "right"]}
        />
      </Box>
    );
  };

  return (
    <FormField
      value={void 0}
      label="Owner"
      renderInput={() => <Content />}
      disabled
    />
  );
}
