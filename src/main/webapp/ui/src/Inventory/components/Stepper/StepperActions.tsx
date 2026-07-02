import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import ValidatingSubmitButton from "../../../components/ValidatingSubmitButton";
import useStores from "../../../stores/use-stores";

type StepperActionsArgs = {
  onSubmit: () => void;
};

function StepperActions({ onSubmit }: StepperActionsArgs): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
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
      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
        {activeResult.submittable.orElseGet((errors) =>
          errors.map((error, i) => (
            <Typography key={i} variant="body2" color="warning.main" role="alert">
              {error.message}
            </Typography>
          )),
        )}
        <ValidatingSubmitButton
          onClick={onSubmit}
          validationResult={activeResult.submittable}
          loading={activeResult.loading}
          progress={activeResult.uploadProgress}
          disabled={!activeResult.submittable.isOk}
        >
          {t("common:actions.save")}
        </ValidatingSubmitButton>
      </Box>
    </Box>
  );
}

export default observer(StepperActions);
