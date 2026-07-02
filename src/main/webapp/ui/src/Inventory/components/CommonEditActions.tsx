import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import ValidatingSubmitButton from "../../components/ValidatingSubmitButton";
import type { Editable } from "../../stores/definitions/Editable";
import useStores from "../../stores/use-stores";

type CommonActionsArgs = {
  editableObject: Editable;
};

function CommonActions({ editableObject }: CommonActionsArgs): React.ReactNode {
  const { uiStore } = useStores();

  return (
    <Box
      sx={(theme) => ({
        p: 1,
        display: "flex",
        justifyContent: "space-between",
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
      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
        {editableObject.submittable.orElseGet((errors) =>
          errors.map((error, i) => (
            <Typography key={i} variant="body2" color="warning.main" role="alert">
              {error.message}
            </Typography>
          )),
        )}
        <ValidatingSubmitButton
          onClick={() => void editableObject.update()}
          validationResult={editableObject.submittable}
          loading={editableObject.loading}
          progress={editableObject.uploadProgress}
          disabled={!editableObject.submittable.isOk}
        >
          Save
        </ValidatingSubmitButton>
      </Box>
    </Box>
  );
}

export default observer(CommonActions);
