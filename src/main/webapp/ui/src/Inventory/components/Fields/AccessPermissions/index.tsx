import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import docLinks from "../../../../assets/DocLinks";
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
      {notEditable && <Alert severity="info">You need to be in Edit mode to edit permissions.</Alert>}
      <FormControl
        aria-label="Access Permission Setting"
        label=""
        explanation={
          <>
            Specify who will have full view and edit access to this item using the options below. See the documentation
            for information on{" "}
            <a href={docLinks.permissions} target="_blank" rel="noreferrer">
              access permissions
            </a>
            , including under what circumstances some infomation may be more widely shared.
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
                        <OptionHeading>Owner&apos;s groups</OptionHeading>
                        <OptionExplanation>
                          Accessible to only those who are in a lab or collaboration group with the owner.{" "}
                          {!hideOwnersGroups && <>This table lists the groups that the owner is a member of.</>}
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
                        <OptionHeading>Explicit access list</OptionHeading>
                        <OptionExplanation>
                          Accessible to only those who are in a lab or collaboration group that is listed in the table
                          below, which can be any group in the system.
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
                    <OptionHeading>Only the Owner</OptionHeading>
                    <OptionExplanation>Accessible to the item&apos;s owner, and the PI.</OptionExplanation>
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
