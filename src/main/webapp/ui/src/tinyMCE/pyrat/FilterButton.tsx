import IconButton from "@mui/material/IconButton";
import Typography from "@mui/material/Typography";
import ExpandCollapseIcon from "../../components/ExpandCollapseIcon";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function FilterButton({ showFilter, setShowFilter }: { showFilter: any; setShowFilter: any }) {
  return (
    <>
      <Typography sx={{ marginLeft: "15px" }} component="span" variant="body1" color="textPrimary">
        Filter
      </Typography>
      <IconButton
        title={showFilter ? "Hide filtering options" : "Show filtering options"}
        onClick={() => setShowFilter(!showFilter)}
      >
        <ExpandCollapseIcon open={showFilter} />
      </IconButton>
    </>
  );
}
