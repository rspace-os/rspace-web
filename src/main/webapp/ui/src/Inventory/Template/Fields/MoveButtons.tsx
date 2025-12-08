import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import VerticalAlignBottomIcon from "@mui/icons-material/VerticalAlignBottom";
import VerticalAlignTopIcon from "@mui/icons-material/VerticalAlignTop";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Tooltip from "@mui/material/Tooltip";
import type { ReactNode } from "react";
import { makeStyles } from "tss-react/mui";
import InputWrapper from "../../../components/Inputs/InputWrapper";

type MoveButtonsArgs = {
    index: number;
    onClick: (number: number) => void;
};

const useStyles = makeStyles()((theme) => ({
    moveButtonGroup: {
        width: "100%",
    },
    moveButton: {
        flexGrow: 1,
        color: theme.palette.primary.main,
    },
}));

type CustomButtonArgs = {
    label: string;
    onClick: () => void;
    icon: ReactNode;
};

function CustomButton({ label, onClick, icon }: CustomButtonArgs) {
    const { classes } = useStyles();
    return (
        <Tooltip key={label} title={label} enterDelay={200}>
            <ToggleButton onClick={onClick} className={classes.moveButton} value={label}>
                {icon}
            </ToggleButton>
        </Tooltip>
    );
}

export default function MoveButtons({ index, onClick }: MoveButtonsArgs): ReactNode {
    const { classes } = useStyles();

    return (
        <InputWrapper label="Move">
            <ToggleButtonGroup className={classes.moveButtonGroup}>
                <CustomButton label="To Top" onClick={() => onClick(0)} icon={<VerticalAlignTopIcon />} />
                <CustomButton label="Up" onClick={() => onClick(index - 1)} icon={<ArrowUpwardIcon />} />
                <CustomButton label="Down" onClick={() => onClick(index + 1)} icon={<ArrowDownwardIcon />} />
                <CustomButton label="To Bottom" onClick={() => onClick(-1)} icon={<VerticalAlignBottomIcon />} />
            </ToggleButtonGroup>
        </InputWrapper>
    );
}
