//@flow

import React, { type Node, type ElementProps } from "react";
import { withStyles } from "Styles";
import FormControl from "../../../../components/Inputs/FormControl";
import RadioGroup from "@mui/material/RadioGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import Box from "@mui/material/Box";
import Grid from "@mui/material/Grid";
import { type HasEditableFields } from "../../../../stores/definitions/Editable";
import { type SharingMode } from "../../../../stores/definitions/InventoryRecord";
import ResultCollection from "../../../../stores/models/InventoryBaseRecordCollection";
import { observer } from "mobx-react-lite";
import {
  type SharedWithGroup,
  type Group,
} from "../../../../stores/definitions/Group";
import OwnersGroupsTable from "./OwnersGroupsTable";
import AccessListTable from "./AccessListTable";
import ChooseToEdit from "../../../../components/Inputs/ChooseToEdit";
import docLinks from "../../../../assets/DocLinks";
import Alert from "@mui/material/Alert";
import {
  OptionHeading,
  OptionExplanation,
} from "../../../../components/Inputs/RadioField";

const CustomFormControlLabel = withStyles<
  ElementProps<typeof FormControlLabel>,
  { root: string }
>(() => ({
  root: {
    alignItems: "flex-start",
  },
}))(FormControlLabel);

function AccessPermissions<
  Fields: {
    sharingMode: SharingMode,
    sharedWith: ?Array<SharedWithGroup>,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({
  fieldOwner,
  hideOwnersGroups = false,
  additionalExplanation,
}: {|
  fieldOwner: FieldOwner,
  hideOwnersGroups?: boolean,
  additionalExplanation?: string,
|}): Node {
  const onCheckboxClick = (checkedGroup: Group) => {
    if (!fieldOwner.fieldValues.sharedWith)
      throw new Error("sharedWith must be set");
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
    if (!fieldOwner.fieldValues.sharedWith)
      throw new Error("sharedWith must be set");
    const sharedWith = fieldOwner.fieldValues.sharedWith;
    fieldOwner.setFieldsDirty({
      sharedWith: [
        ...sharedWith,
        { group, shared: true, itemOwnerGroup: false },
      ],
    });
  };

  const notEditable =
    !fieldOwner.isFieldEditable("sharingMode") &&
    !(fieldOwner instanceof ResultCollection);

  return (
    <>
      {notEditable && (
        <Alert severity="info">
          You need to be in Edit mode to edit permissions.
        </Alert>
      )}
      <FormControl
        aria-label="Access Permission Setting"
        label=""
        explanation={
          <>
            Specify who will have full view and edit access to this item using
            the options below. See the documentation for information on{" "}
            <a href={docLinks.permissions} target="_blank" rel="noreferrer">
              access permissions
            </a>
            , including under what circumstances some infomation may be more
            widely shared.
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
          <Grid container direction="column" spacing={1}>
            {(fieldOwner.isFieldEditable("sharingMode") ||
              fieldOwner.fieldValues.sharingMode === "OWNER_GROUPS") && (
              <Grid item>
                <CustomFormControlLabel
                  value="OWNER_GROUPS"
                  control={
                    <Radio
                      color="primary"
                      disabled={!fieldOwner.isFieldEditable("sharingMode")}
                    />
                  }
                  label={
                    <Box m={1} mt={0.5}>
                      <Grid container direction="column" spacing={1}>
                        <Grid item>
                          <OptionHeading>Owner&apos;s groups</OptionHeading>
                          <OptionExplanation>
                            Accessible to only those who are in a lab or
                            collaboration group with the owner.{" "}
                            {!hideOwnersGroups && (
                              <>
                                This table lists the groups that the owner is a
                                member of.
                              </>
                            )}
                          </OptionExplanation>
                        </Grid>
                        {!hideOwnersGroups && (
                          <Grid item>
                            <OwnersGroupsTable
                              groups={(fieldOwner.fieldValues.sharedWith ?? [])
                                .filter(({ itemOwnerGroup }) => itemOwnerGroup)
                                .map(({ group }) => group)}
                            />
                          </Grid>
                        )}
                      </Grid>
                    </Box>
                  }
                />
              </Grid>
            )}
            {(fieldOwner.isFieldEditable("sharingMode") ||
              fieldOwner.fieldValues.sharingMode === "WHITELIST") && (
              <Grid item>
                <CustomFormControlLabel
                  value="WHITELIST"
                  control={
                    <Radio
                      color="primary"
                      disabled={!fieldOwner.isFieldEditable("sharingMode")}
                    />
                  }
                  label={
                    <Box m={1} mt={0.5}>
                      <Grid container direction="column" spacing={1}>
                        <Grid item>
                          <OptionHeading>Explicit access list</OptionHeading>
                          <OptionExplanation>
                            Accessible to only those who are in a lab or
                            collaboration group that is listed in the table
                            below, which can be any group in the system.
                          </OptionExplanation>
                        </Grid>
                        <Grid item>
                          <AccessListTable
                            sharedWith={fieldOwner.fieldValues.sharedWith ?? []}
                            disabled={!fieldOwner.isFieldEditable("sharedWith")}
                            onCheckboxClick={(group) => onCheckboxClick(group)}
                            onAdditionalGroup={(group) =>
                              onAdditionalGroup(group)
                            }
                          />
                        </Grid>
                      </Grid>
                    </Box>
                  }
                />
              </Grid>
            )}
            {(fieldOwner.isFieldEditable("sharingMode") ||
              fieldOwner.fieldValues.sharingMode === "OWNER_ONLY") && (
              <Grid item>
                <CustomFormControlLabel
                  value="OWNER_ONLY"
                  control={
                    <Radio
                      color="primary"
                      disabled={!fieldOwner.isFieldEditable("sharingMode")}
                    />
                  }
                  label={
                    <Box m={1} mt={0.5}>
                      <Grid container direction="column" spacing={1}>
                        <Grid item>
                          <OptionHeading>Only the Owner</OptionHeading>
                          <OptionExplanation>
                            Accessible to the item&apos;s owner, and the PI.
                          </OptionExplanation>
                        </Grid>
                      </Grid>
                    </Box>
                  }
                />
              </Grid>
            )}
          </Grid>
        </RadioGroup>
      </FormControl>
    </>
  );
}

export default (observer(AccessPermissions): typeof AccessPermissions);
