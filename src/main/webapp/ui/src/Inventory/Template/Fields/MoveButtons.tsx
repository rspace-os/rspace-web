import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import VerticalAlignBottomIcon from "@mui/icons-material/VerticalAlignBottom";
import VerticalAlignTopIcon from "@mui/icons-material/VerticalAlignTop";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Tooltip from "@mui/material/Tooltip";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import InputWrapper from "../../../components/Inputs/InputWrapper";

type MoveButtonsArgs = {
  index: number;
  onClick: (number: number) => void;
};

type CustomButtonArgs = {
  label: string;
  onClick: () => void;
  icon: ReactNode;
};

function CustomButton({ label, onClick, icon }: CustomButtonArgs) {
  return (
    <Tooltip key={label} title={label} enterDelay={200}>
      <ToggleButton
        onClick={onClick}
        sx={(theme) => ({
          flexGrow: 1,
          color: theme.palette.primary.main,
        })}
        value={label}
      >
        {icon}
      </ToggleButton>
    </Tooltip>
  );
}

export default function MoveButtons({ index, onClick }: MoveButtonsArgs): ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <InputWrapper label={t("fields.templateFields.move.label")}>
      <ToggleButtonGroup sx={{ width: "100%" }}>
        <CustomButton
          label={t("fields.templateFields.move.toTop")}
          onClick={() => onClick(0)}
          icon={<VerticalAlignTopIcon />}
        />
        <CustomButton
          label={t("fields.templateFields.move.up")}
          onClick={() => onClick(index - 1)}
          icon={<ArrowUpwardIcon />}
        />
        <CustomButton
          label={t("fields.templateFields.move.down")}
          onClick={() => onClick(index + 1)}
          icon={<ArrowDownwardIcon />}
        />
        <CustomButton
          label={t("fields.templateFields.move.toBottom")}
          onClick={() => onClick(-1)}
          icon={<VerticalAlignBottomIcon />}
        />
      </ToggleButtonGroup>
    </InputWrapper>
  );
}
