import Box from "@mui/material/Box";
import type React from "react";
import { useTranslation } from "react-i18next";
import FormField from "../../../components/Inputs/FormField";
import NoValue from "../../../components/NoValue";
import UserDetails from "../../../components/UserDetails";
import type { HasUneditableFields } from "../../../stores/definitions/Editable";
import type { Person } from "../../../stores/definitions/Person";

export default function Owner<
  Fields extends {
    owner: Person | null;
  },
  FieldOwner extends HasUneditableFields<Fields>,
>({ fieldOwner }: { fieldOwner: FieldOwner }): React.ReactNode {
  const { t } = useTranslation("inventory");
  const owner: Person | null = fieldOwner.fieldValues.owner;

  return (
    <FormField
      value={void 0}
      label={t("fields.owner.label")}
      renderInput={() => {
        if (!owner) return <NoValue label={fieldOwner.noValueLabel.owner ?? t("fields.owner.unknownOwner")} />;
        const { id, fullName } = owner;
        return (
          <Box>
            <UserDetails userId={id} fullName={fullName} position={["bottom", "right"]} />
          </Box>
        );
      }}
      disabled
    />
  );
}
