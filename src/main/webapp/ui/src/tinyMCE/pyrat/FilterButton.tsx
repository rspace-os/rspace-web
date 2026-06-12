import IconButton from "@mui/material/IconButton";
import Typography from "@mui/material/Typography";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import ExpandCollapseIcon from "../../components/ExpandCollapseIcon";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
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
