import React, { useEffect } from "react";
import Grid from "@mui/material/Grid";
import TextField from "@mui/material/TextField";
import { type Person, type StandardValidations } from "./common";
import Tags, { type Tag } from "./Tags";

/*
 * This component collects the metadata required by Zenodo to make a deposit.
 */

type ZenodoRepoArgs = {
  // To modify the metadata relating to persons call updatePeople, for all
  // other metadata call handleChange. The form fields are uncontrolled so no
  // current value is passed in from the caller; modified values are simply
  // propagated up and the fields initialise to empty.
  updatePeople: (people: Array<Person>) => void;
  handleChange: (event: {
    target: { name: "subject" | "title" | "description"; value: string };
  }) => void;

  // Callers of this component should validate the modified values and use
  // this prop to signal as to whether the corresponding field should be in an
  // error state or not.
  inputValidations: StandardValidations;

  // Error states will only be shown when this flag is true, which is to say
  // that the user has attempted to submit the form. This means we don't show
  // any error state whilst they are in the process of inputting the metadata.
  submitAttempt: boolean;

  title: string;
  description: string;

  tags: Array<Tag>;
  onTagsChange: (event: {
    target: {
      value: Array<Tag>;
    };
  }) => void;
  fetchingTags: boolean;
};

/**
 * This components provides a form for the user to fill in the details of the
 * deposit that will be made with Zenodo.
 */
export default function ZenodoRepo({
  handleChange,
  inputValidations,
  submitAttempt,
  updatePeople,
  title,
  description,
  tags,
  onTagsChange,
  fetchingTags,
}: ZenodoRepoArgs): React.ReactNode {
  /*
   * Zenodo doesn't require all of the same information as all of the other
   * repositories, but the RSpace backend is set up to require author,
   * contact, and subject metadata. Rather than weaken the validations for
   * all repositories, for Zenodo we pass these dummy values which are then
   * ignored when the RSpace backend calls the Zenodo API.
   */
  useEffect(() => {
    updatePeople([
      {
        uniqueName: "DUMMY_VALUE",
        email: "DUMMY_VALUE@example.com",
        type: "Author",
      },
      {
        uniqueName: "DUMMY_VALUE",
        email: "DUMMY_VALUE@example.com",
        type: "Contact",
      },
    ]);
    handleChange({
      target: {
        name: "subject",
        value: "DUMMY_VALUE",
      },
    });
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - handleChange will not meaningfully change
     * - updatePeople will not meaningfully change
     */
  }, []);

  return (
    <Grid container style={{ width: "100%" }}>
      <Grid item xs={12}>
        <TextField
          name="title"
          error={submitAttempt && !inputValidations.title}
          label="Title *"
          onChange={({ target: { value } }) =>
            handleChange({ target: { name: "title", value } })
          }
          margin="normal"
          fullWidth
          value={title}
        />
      </Grid>
      <Grid item xs={12}>
        <TextField
          error={submitAttempt && !inputValidations.description}
          label="Description *"
          name="description"
          multiline
          maxRows="4"
          onChange={({ target: { value } }) =>
            handleChange({ target: { name: "description", value } })
          }
          margin="normal"
          fullWidth
          value={description}
        />
      </Grid>
      <Grid item xs={12} mt={1}>
        <Tags
          fieldOwner={{
            fieldValues: { tags },
            setFieldsDirty: ({ tags: newTags }: { tags?: Array<Tag> }) => {
              if (newTags) onTagsChange({ target: { value: newTags } });
            },
            setFieldEditable: () => {},
            noValueLabel: { tags: null },
            isFieldEditable: () => true,
            canChooseWhichToEdit: false,
          }}
          loading={fetchingTags}
        />
      </Grid>
    </Grid>
  );
}
