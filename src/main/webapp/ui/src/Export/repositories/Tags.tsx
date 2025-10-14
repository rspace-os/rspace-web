import React from "react";
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
import { ThemeProvider } from "@mui/material";
import createAccentedTheme from "@/accentedTheme";
import { HeadingContext } from "@/components/DynamicHeadingLevel";
import { color, currentPage } from "@/util/pageBranding";

/**
 * The definition of a tag as used in the export flow.
 */
export type Tag = {
  value: string;
  vocabulary: string;
  uri: string;
  version: string;
};

function Tags<
  Fields extends { tags: Array<Tag> },
  FieldOwner extends HasEditableFields<Fields>,
>({
  fieldOwner,
  loading,
}: {
  fieldOwner: FieldOwner;
  loading: boolean;
}): React.ReactNode {
  /*
   * InputWrapper assumes that it is being used on a page with an accented theme,
   * but the export dialog isn't always on such a page, so we need to provide
   * the theme here. Similarly, it assumes that the heading level of the label
   * can be determined from the current heading context, but the export dialog
   * doesn't utilise heading contexts so we need to provide a level explicitly.
   * Ideally the entire export dialog would use heading contexts for its
   * subheadings and derive the accented theme from the current page.
   */
  return (
    <ThemeProvider theme={createAccentedTheme(color(currentPage()))}>
      <HeadingContext level={4}>
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
              onClick={() => fieldOwner.setFieldsDirty({ tags: [] })}
              sx={{ py: 0 }}
              disabled={fieldOwner.fieldValues.tags.length === 0}
            >
              Clear Tags
            </Button>
          }
        >
          <FormHelperText sx={{ ml: 0, mb: 2 }}>
            Add tags from controlled vocabularies to this export. The
            term&apos;s value and URI will be included in the deposit&apos;s
            metadata. For more info see{" "}
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
                        1,
                      ),
                    });
                  },
                }
              : {})}
            endAdornment={
              fieldOwner.isFieldEditable("tags") && (
                <Grid item>
                  <AddTag<{
                    enforce: true;
                    tag: {
                      value: string;
                      vocabulary: string;
                      uri: string;
                      version: string;
                    };
                  }>
                    enforceOntologies={true}
                    onSelection={(tag: Tag) => {
                      if (fieldOwner.fieldValues.tags.includes(tag)) {
                        console.warn(
                          "Preventing the same tag from being added twice",
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
      </HeadingContext>
    </ThemeProvider>
  );
}

/**
 * This component provides a form field for the user to fill in the
 * tags/controlled vocabulary terms for the deposit that will be made with the
 * chosen repository.
 */
export default observer(Tags);
