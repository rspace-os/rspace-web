import IconButton from "@mui/material/IconButton";
import Typography from "@mui/material/Typography";
import { useTranslation } from "react-i18next";
import ExpandCollapseIcon from "../../components/ExpandCollapseIcon";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
export default function FilterButton({ showFilter, setShowFilter }: { showFilter: any; setShowFilter: any }) {
  const { t } = useTranslation("apps");

  return (
    <>
      <Typography sx={{ marginLeft: "15px" }} component="span" variant="body1" color="textPrimary">
        {t("pyrat.filter.label")}
      </Typography>
      <IconButton
        title={showFilter ? t("pyrat.filter.hideOptions") : t("pyrat.filter.showOptions")}
        onClick={() => setShowFilter(!showFilter)}
      >
        <ExpandCollapseIcon open={showFilter} />
      </IconButton>
    </>
  );
}
