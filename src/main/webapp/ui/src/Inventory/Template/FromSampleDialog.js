//@flow

import React, {
  type Node,
  type ComponentType,
  useState,
  useEffect,
  useContext,
} from "react";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import { match, doNotAwait } from "../../util/Util";
import docLinks from "../../assets/DocLinks";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import FormHelper from "../../components/Inputs/FormHelper";
import FormHelperText from "@mui/material/FormHelperText";
import StringField from "../../components/Inputs/StringField";
import SubmitSpinner from "../../components/SubmitSpinnerButton";
import SearchContext from "../../stores/contexts/Search";
import FieldModel from "../../stores/models/FieldModel";
import { type Id } from "../../stores/definitions/BaseRecord";
import SampleModel from "../../stores/models/SampleModel";
import useStores from "../../stores/use-stores";
import RsSet from "../../util/set";
import StateMachine from "../../util/stateMachine";
import Button from "@mui/material/Button";
import Checkbox from "@mui/material/Checkbox";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import NoValue from "../../components/NoValue";

const useStyles = makeStyles()(() => ({
  dialog: {
    height: "100%",
  },
  headCell: {
    fontWeight: 600,
  },
}));

type FromSampleDialogArgs = {|
  open: boolean,
  onCancel: () => void,
  onSubmit: () => void,
  sample: SampleModel,
|};

function FromSampleDialog({
  open,
  onCancel,
  onSubmit,
  sample,
}: FromSampleDialogArgs): Node {
  const { uiStore } = useStores();
  const { classes } = useStyles();
  const { search } = useContext(SearchContext);

  const [templateName, setTemplateName] = useState("");

  useEffect(() => {
    setTemplateName(`${sample.name} Template`);
  }, [sample.name]);

  const newState = () =>
    new StateMachine(
      {
        loading: new Set(["fieldSelection", "loading"]),
        fieldSelection: new Set(["submitting", "loading"]),
        submitting: new Set(["done", "failed"]),
        done: new Set(["submitting"]),
        failed: new Set(["submitting"]),
      },
      "loading",
      (x) => x
    );

  const [state] = useState(newState()); // unusual useState without setState function

  useEffect(() => {
    state.transitionTo("loading", () => {});
    if (!sample.infoLoaded) {
      void sample.fetchAdditionalInfo();
      return;
    }
    const fields = [...sample.fields, ...sample.extraFields];
    state.transitionTo("fieldSelection", () => ({
      fields,
      selected: new Set(fields.filter((f) => f.hasContent).map((f) => f.id)),
    }));
  }, [sample, sample.infoLoaded]);

  const allSelected = () => {
    state.assertCurrentState(
      new RsSet(["fieldSelection", "submitting", "done", "failed"])
    );
    return (
      [...state.data.fields].filter((f) => f.hasContent).length ===
      state.data.selected.size
    );
  };

  const someSelected = () => {
    state.assertCurrentState(
      new RsSet(["fieldSelection", "submitting", "done", "failed"])
    );
    return state.data.selected.size > 0;
  };

  const onSubmitHandler = async () => {
    state.transitionTo("submitting");
    try {
      await search.createTemplateFromSample(
        templateName,
        sample,
        (state.data.selected: RsSet<Id>)
      );
      state.transitionTo("done");
      onSubmit();
    } catch {
      state.transitionTo("failed");
    }
  };

  const templateNameErrorText = match<void, ?string>([
    [() => templateName === "", "Template must have a name."],
    [() => templateName.length > 255, "Template name is too long."],
    [() => true, null],
  ])();

  return (
    <Dialog
      classes={{
        paper: classes.dialog,
      }}
      open={open}
      onClose={onCancel}
      maxWidth="xl"
      fullScreen={uiStore.isVerySmall}
      data-testid="FromSampleDialog"
    >
      <DialogTitle>
        Create Template From Sample
        <HelpLinkIcon
          link={docLinks.createTemplateFromSample}
          title="Info on creating a template from a sample."
        />
      </DialogTitle>
      <DialogContent>
        <FormHelper
          label="Name"
          helperText={
            templateNameErrorText ?? "Name of the newly created template."
          }
          error={Boolean(templateNameErrorText)}
        >
          <StringField
            value={templateName}
            onChange={({ target }) => {
              setTemplateName(target.value);
            }}
            variant="outlined"
            onFocus={({ target }) => {
              target.select();
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter") void onSubmitHandler();
            }}
            data-testid="FromSampleNameField"
          />
          <FormHelperText component="div">
            {`${templateName.length} / 255`}
          </FormHelperText>
        </FormHelper>
        <FormHelper
          label="Default Values"
          helperText="Choose which fields should have a default value."
        >
          {!state.isCurrentState("loading") && (
            <>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell variant="head" padding="checkbox">
                        <Checkbox
                          indeterminate={someSelected() && !allSelected()}
                          checked={allSelected()}
                          onChange={({ target: { checked } }) => {
                            for (const field of [...state.data.fields].filter(
                              (f) => f.hasContent
                            ))
                              state.data.selected[checked ? "add" : "delete"](
                                field.id
                              );
                          }}
                        />
                      </TableCell>
                      <TableCell className={classes.headCell} width="70%">
                        Field
                      </TableCell>
                      <TableCell className={classes.headCell} width="30%">
                        Default Value
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {state.data.fields.map((f: FieldModel) => (
                      <TableRow key={f.id}>
                        <TableCell variant="head" padding="checkbox">
                          <Checkbox
                            checked={state.data.selected.has(f.id)}
                            onChange={({ target: { checked } }) =>
                              state.data.selected[checked ? "add" : "delete"](
                                f.id
                              )
                            }
                            color="default"
                            disabled={!f.hasContent}
                          />
                        </TableCell>
                        <TableCell>{f.name}</TableCell>
                        <TableCell
                          style={{
                            opacity: state.data.selected.has(f.id) ? 1.0 : 0.3,
                          }}
                        >
                          {f.renderContentAsString}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              {state.data.fields.length === 0 && (
                <NoValue label="No fields available." />
              )}
            </>
          )}
        </FormHelper>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={false}>
          Cancel
        </Button>
        <SubmitSpinner
          onClick={doNotAwait(onSubmitHandler)}
          disabled={
            templateName.length < 1 ||
            templateName.length > 255 ||
            state.isCurrentState(new RsSet(["submitting", "loading"]))
          }
          loading={state.isCurrentState("submitting")}
          label="Create Template"
        />
      </DialogActions>
    </Dialog>
  );
}

export default (observer(
  FromSampleDialog
): ComponentType<FromSampleDialogArgs>);
