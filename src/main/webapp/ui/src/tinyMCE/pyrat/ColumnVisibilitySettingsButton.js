import IconButton from "@mui/material/IconButton";
import Typography from "@mui/material/Typography";
import ExpandCollapseIcon from "../../components/ExpandCollapseIcon";

export default function ColumnVisibilitySettingsButton({ showSettings, setShowSettings }) {
    return (
        <>
            <Typography style={{ marginLeft: "15px" }} component="span" variant="body1" color="textPrimary">
                Column Visibility
            </Typography>
            <IconButton
                title={showSettings ? "Hide column visibility settings" : "Show column visibility settings"}
                onClick={() => setShowSettings(!showSettings)}
            >
                <ExpandCollapseIcon open={showSettings} />
            </IconButton>
        </>
    );
}
