//@flow

import React, { type Node } from "react";
import { observer } from "mobx-react-lite";
import { type HasEditableFields } from "../../stores/definitions/Editable";
import InputWrapper from "../../components/Inputs/InputWrapper";
import AddTag from "../../components/Tags/AddTag";
import Grid from "@mui/material/Grid";
import * as ArrayUtils from "../../util/ArrayUtils";
import NoValue from "../../components/NoValue";
import FormHelperText from "@mui/material/FormHelperText";
import docLinks from "../../assets/DocLinks";
import Button from "@mui/material/Button";
import TagListing from "../../components/Tags/TagListing";
import { Optional } from "../../util/optional";

/**
 * The definition of a tag as used in the export flow.
 */
export type Tag = {|
  value: string,
  vocabulary: string,
  uri: string,
  version: string,
|};

function Tags<
  Fields: {
    tags: Array<Tag>,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({
  fieldOwner,
  loading,
}: {|
  fieldOwner: FieldOwner,
  loading: boolean,
|}): Node {
  return (
    <InputWrapper
      error={false}
      disabled={!fieldOwner.isFieldEditable("tags")}
      value={fieldOwner.fieldValues.tags.map((t) => t.value).join(",")}
      label="Tags and Controlled Vocabulary Terms"
      helperText={null}
      actions={
        <Button
          variant="outlined"
          size="small"
          onClick={() => fieldOwner.setFieldsDirty({ tags: ([]: Array<Tag>) })}
          sx={{ py: 0 }}
          disabled={fieldOwner.fieldValues.tags.length === 0}
        >
          Clear Tags
        </Button>
      }
    >
      <FormHelperText sx={{ ml: 0, mb: 2 }}>
        Add tags from controlled vocabularies to this export. The term&apos;s
        value and URI will be included in the deposit&apos;s metadata. For more
        info see{" "}
        <a
          href={docLinks.controlledVocabularies}
          target="_blank"
          rel="noreferrer"
        >
          Tagging Documents and using Controlled Vocabularies
        </a>
        .
      </FormHelperText>
      {fieldOwner.fieldValues.tags.length === 0 &&
        !fieldOwner.isFieldEditable("tags") && (
          <NoValue label={fieldOwner.noValueLabel.tags ?? "None"} />
        )}
      <TagListing
        tags={fieldOwner.fieldValues.tags.map((tag) => ({
          value: tag.value,
          uri: Optional.present(tag.uri),
          vocabulary: Optional.present(tag.vocabulary),
          version: Optional.present(tag.version),
        }))}
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
          fieldOwner.isFieldEditable("tags") && (
            <Grid item>
              <AddTag
                enforceOntologies={true}
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
                disabled={loading}
              />
            </Grid>
          )
        }
      />
    </InputWrapper>
  );
}

/**
 * This component provides a form field for the user to fill in the
 * tags/controlled vocabulary terms for the deposit that will be made with the
 * chosen repository.
 */
export default (observer(Tags): typeof Tags);
