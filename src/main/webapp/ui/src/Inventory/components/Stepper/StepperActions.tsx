import React from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import Box from "@mui/material/Box";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";

type StepperActionsArgs = {
  onSubmit: () => void;
};

function StepperActions({ onSubmit }: StepperActionsArgs): React.ReactNode {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult) throw new Error("ActiveResult must be a Record");

  return (
    <Box
      sx={(theme) => ({
        p: 1,
        display: "flex",
        justifyContent: "flex-end",
        position: "sticky",
        bottom: 0,
        zIndex: 1000,
        backgroundColor: theme.palette.background.alt,
        border: theme.borders.floatingActions,
        borderBottom: 0,
        borderTopLeftRadius: theme.spacing(0.5),
        borderTopRightRadius: theme.spacing(0.5),
      })}
    >
      <ValidatingSubmitButton
        onClick={onSubmit}
        validationResult={activeResult.submittable}
        loading={activeResult.loading}
        progress={activeResult.uploadProgress}
      >
        Save
      </ValidatingSubmitButton>
    </Box>
  );
}

export default observer(StepperActions);
