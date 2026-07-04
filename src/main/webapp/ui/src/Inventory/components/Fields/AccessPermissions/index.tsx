import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import ChooseToEdit from "../../../../components/Inputs/ChooseToEdit";
import FormControl from "../../../../components/Inputs/FormControl";
import { OptionExplanation, OptionHeading } from "../../../../components/Inputs/RadioField";
import type { HasEditableFields } from "../../../../stores/definitions/Editable";
import type { Group, SharedWithGroup } from "../../../../stores/definitions/Group";
import type { SharingMode } from "../../../../stores/definitions/InventoryRecord";
import InventoryBaseRecordCollection from "../../../../stores/models/InventoryBaseRecordCollection";
import AccessListTable from "./AccessListTable";
import OwnersGroupsTable from "./OwnersGroupsTable";

type Fields = {
  sharingMode: SharingMode;
  sharedWith: Array<SharedWithGroup> | null;
};

type AccessPermissionsArgs<FieldOwner extends HasEditableFields<Fields>> = {
  fieldOwner: FieldOwner;
  hideOwnersGroups?: boolean;
  additionalExplanation?: string;
};

function AccessPermissions<FieldOwner extends HasEditableFields<Fields>>({
  fieldOwner,
  hideOwnersGroups = false,
  additionalExplanation,
}: AccessPermissionsArgs<FieldOwner>): React.ReactNode {
  const { t } = useTranslation("inventory");
  const onCheckboxClick = (checkedGroup: Group) => {
    if (!fieldOwner.fieldValues.sharedWith) throw new Error("sharedWith must be set");
    const sharedWith = fieldOwner.fieldValues.sharedWith;
    fieldOwner.setFieldsDirty({
      sharedWith: sharedWith.map(({ group, shared, itemOwnerGroup }) => ({
        group,
        shared: group.id === checkedGroup.id ? !shared : shared,
        itemOwnerGroup,
      })),
    });
  };

  const onAdditionalGroup = (group: Group) => {
    if (!fieldOwner.fieldValues.sharedWith) throw new Error("sharedWith must be set");
    const sharedWith = fieldOwner.fieldValues.sharedWith;
    fieldOwner.setFieldsDirty({
      sharedWith: [...sharedWith, { group, shared: true, itemOwnerGroup: false }],
    });
  };

  const notEditable =
    !fieldOwner.isFieldEditable("sharingMode") && !(fieldOwner instanceof InventoryBaseRecordCollection);

  return (
    <>
      {notEditable && <Alert severity="info">{t("fields.accessPermissions.editFirst")}</Alert>}
      <FormControl
        aria-label={t("fields.accessPermissions.label")}
        label=""
        explanation={
          <>
            <TransRichText i18nKey="inventory:fields.accessPermissions.explanation" />
            <br />
            <p>{additionalExplanation}</p>
          </>
        }
        actions={
          fieldOwner.canChooseWhichToEdit ? (
            <ChooseToEdit
              checked={fieldOwner.isFieldEditable("sharingMode")}
              onChange={(checked) => {
                fieldOwner.setFieldEditable("sharingMode", checked);
                fieldOwner.setFieldEditable("sharedWith", checked);
              }}
            />
          ) : null
        }
      >
        <RadioGroup
          value={fieldOwner.fieldValues.sharingMode}
          onChange={({ target: { value } }) => {
            fieldOwner.setFieldsDirty({
              sharingMode: value,
            });
          }}
        >
          <Stack spacing={1}>
            {(fieldOwner.isFieldEditable("sharingMode") || fieldOwner.fieldValues.sharingMode === "OWNER_GROUPS") && (
              <FormControlLabel
                sx={{ alignItems: "flex-start" }}
                value="OWNER_GROUPS"
                control={<Radio color="primary" disabled={!fieldOwner.isFieldEditable("sharingMode")} />}
                label={
                  <Box sx={{ m: 1, mt: 0.5 }}>
                    <Stack spacing={1}>
                      <Box>
                        <OptionHeading>{t("fields.accessPermissions.ownerGroups.title")}</OptionHeading>
                        <OptionExplanation>
                          {t("fields.accessPermissions.ownerGroups.description", {
                            tableNote: hideOwnersGroups ? "" : t("fields.accessPermissions.ownerGroups.tableNote"),
                          })}
                        </OptionExplanation>
                      </Box>
                      {!hideOwnersGroups && (
                        <OwnersGroupsTable
                          groups={(fieldOwner.fieldValues.sharedWith ?? [])
                            .filter(({ itemOwnerGroup }) => itemOwnerGroup)
                            .map(({ group }) => group)}
                        />
                      )}
                    </Stack>
                  </Box>
                }
              />
            )}
            {(fieldOwner.isFieldEditable("sharingMode") || fieldOwner.fieldValues.sharingMode === "WHITELIST") && (
              <FormControlLabel
                sx={{ alignItems: "flex-start" }}
                value="WHITELIST"
                control={<Radio color="primary" disabled={!fieldOwner.isFieldEditable("sharingMode")} />}
                label={
                  <Box sx={{ m: 1, mt: 0.5 }}>
                    <Stack spacing={1}>
                      <Box>
                        <OptionHeading>{t("fields.accessPermissions.explicitAccess.title")}</OptionHeading>
                        <OptionExplanation>
                          {t("fields.accessPermissions.explicitAccess.description")}
                        </OptionExplanation>
                      </Box>
                      <AccessListTable
                        sharedWith={fieldOwner.fieldValues.sharedWith ?? []}
                        disabled={!fieldOwner.isFieldEditable("sharedWith")}
                        onCheckboxClick={(group) => onCheckboxClick(group)}
                        onAdditionalGroup={(group) => onAdditionalGroup(group)}
                      />
                    </Stack>
                  </Box>
                }
              />
            )}
            {(fieldOwner.isFieldEditable("sharingMode") || fieldOwner.fieldValues.sharingMode === "OWNER_ONLY") && (
              <FormControlLabel
                sx={{ alignItems: "flex-start" }}
                value="OWNER_ONLY"
                control={<Radio color="primary" disabled={!fieldOwner.isFieldEditable("sharingMode")} />}
                label={
                  <Box sx={{ m: 1, mt: 0.5 }}>
                    <OptionHeading>{t("fields.accessPermissions.ownerOnly.title")}</OptionHeading>
                    <OptionExplanation>{t("fields.accessPermissions.ownerOnly.description")}</OptionExplanation>
                  </Box>
                }
              />
            )}
          </Stack>
        </RadioGroup>
      </FormControl>
    </>
  );
}

export default observer(AccessPermissions) as typeof AccessPermissions;
