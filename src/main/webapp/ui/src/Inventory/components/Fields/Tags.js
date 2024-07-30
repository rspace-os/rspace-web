//@flow

import React, { type Node, useContext } from "react";
import { observer } from "mobx-react-lite";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import AddTag from "../../../components/Tags/AddTag";
import { type Tag } from "../../../stores/definitions/Tag";
import Grid from "@mui/material/Grid";
import TagListing from "../../../components/Tags/TagListing";
import * as ArrayUtils from "../../../util/ArrayUtils";
import NavigateContext from "../../../stores/contexts/Navigate";
import BatchFormField from "../Inputs/BatchFormField";

const MAX_TOTAL = 8000;
const MIN_EACH = 2;

function Tags<
  Fields: {
    tags: Array<Tag>,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({ fieldOwner }: {| fieldOwner: FieldOwner |}): Node {
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const errorMessage = () => {
    if (
      fieldOwner.fieldValues.tags.map((t) => t.value).join(",").length >
      MAX_TOTAL
    )
      return `Tags must be no longer than ${MAX_TOTAL} characters.`;
    if (fieldOwner.fieldValues.tags.some((t) => t.value.length < MIN_EACH))
      return `Each tag cannot be less than ${MIN_EACH} characters.`;
    return null;
  };

  return (
    <BatchFormField
      label="Tags"
      /*
       * One of the reasons for converting the list of tags into a string is
       * that if the list is empty then `!value` is true, and so `noValueLabel`
       * is used. If we left it as an array then `!value` would always be
       * false. Additionally `maxLength` now takes into account the commas.
       */
      value={fieldOwner.fieldValues.tags.join(",")}
      error={Boolean(errorMessage())}
      disabled={!fieldOwner.isFieldEditable("tags")}
      maxLength={MAX_TOTAL}
      helperText={errorMessage()}
      noValueLabel={fieldOwner.noValueLabel.tags ?? "None"}
      canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
      setDisabled={(d) => {
        fieldOwner.setFieldEditable("tags", d);
      }}
      renderInput={({ disabled }) => (
        // id prop is ignored because there is no singular HTMLInputElement to attach it to
        <TagListing
          onClick={(tag) => {
            navigate(`/inventory/search?query=l: (tags:"${tag.value}")`);
          }}
          tags={fieldOwner.fieldValues.tags}
          {...(fieldOwner.isFieldEditable("tags")
            ? {
                onDelete: (index) => {
                  fieldOwner.setFieldsDirty({
                    tags: ArrayUtils.splice(
                      fieldOwner.fieldValues.tags,
                      index,
                      1
                    ),
                  });
                },
              }
            : {})}
          endAdornment={
            !disabled && (
              <Grid item>
                <AddTag
                  onSelection={(tag: Tag) => {
                    if (fieldOwner.fieldValues.tags.includes(tag)) {
                      console.warn(
                        "Preventing the same tag from being added twice"
                      );
                      return;
                    }
                    fieldOwner.setFieldsDirty({
                      tags: [...fieldOwner.fieldValues.tags, tag],
                    });
                  }}
                  value={fieldOwner.fieldValues.tags}
                />
              </Grid>
            )
          }
        />
      )}
    />
  );
}

export default (observer(Tags): typeof Tags);
