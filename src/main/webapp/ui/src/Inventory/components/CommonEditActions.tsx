import useStores from "../../stores/use-stores";
import Button from "@mui/material/Button";
import { makeStyles } from "tss-react/mui";
import { type Editable } from "../../stores/definitions/Editable";
import { observer } from "mobx-react-lite";
import React from "react";
import { doNotAwait } from "../../util/Util";
import ValidatingSubmitButton from "../../components/ValidatingSubmitButton";

const useStyles = makeStyles()((theme) => ({
  actions: {
    padding: theme.spacing(1),
    display: "flex",
    justifyContent: "space-between",
    position: "sticky",
    bottom: 0,
    zIndex: 1000,
    backgroundColor: theme.palette.background.alt,
    border: theme.borders.floatingActions,
    borderBottom: "0px",
    borderTopLeftRadius: theme.spacing(0.5),
    borderTopRightRadius: theme.spacing(0.5),
  },
}));

type CommonActionsArgs = {
  editableObject: Editable;
};

function CommonActions({ editableObject }: CommonActionsArgs): React.ReactNode {
  const { classes } = useStyles();
  const { uiStore } = useStores();

  return (
    <div className={classes.actions}>
      <Button
        size="medium"
        onClick={() => {
          void uiStore.confirmDiscardAnyChanges().then(() => {
            void editableObject.cancel();
          });
        }}
        disabled={editableObject.loading}
      >
        Cancel
      </Button>
      <ValidatingSubmitButton
        onClick={doNotAwait(() => editableObject.update())}
        validationResult={editableObject.submittable}
        loading={editableObject.loading}
        progress={editableObject.uploadProgress}
      >
        Save
      </ValidatingSubmitButton>
    </div>
  );
}

export default observer(CommonActions);
