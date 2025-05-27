import React from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import { makeStyles } from "tss-react/mui";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";

const useStyles = makeStyles()((theme) => ({
  actions: {
    padding: theme.spacing(1),
    display: "flex",
    justifyContent: "flex-end",
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

type StepperActionsArgs = {
  onSubmit: () => void;
};

function StepperActions({ onSubmit }: StepperActionsArgs): React.ReactNode {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult) throw new Error("ActiveResult must be a Record");
  const { classes } = useStyles();

  return (
    <div className={classes.actions}>
      <ValidatingSubmitButton
        onClick={onSubmit}
        validationResult={activeResult.submittable}
        loading={activeResult.loading}
        progress={activeResult.uploadProgress}
      >
        Save
      </ValidatingSubmitButton>
    </div>
  );
}

export default observer(StepperActions);
