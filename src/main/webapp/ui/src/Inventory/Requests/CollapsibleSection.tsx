import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import Box from "@mui/material/Box";
import Collapse from "@mui/material/Collapse";
import Divider from "@mui/material/Divider";
import IconButton from "@mui/material/IconButton";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useId } from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "@/components/CustomTooltip";

/**
 * A collapsible section used throughout the Requests detail panel: a clickable header with a
 * title (optionally wrapped in a tooltip) and a rotating expand/collapse chevron, a divider, and
 * the collapsing content itself. The chevron's aria-label names this section specifically (e.g.
 * "Collapse Details"), rather than every section sharing one generic, indistinguishable name.
 */
export default function CollapsibleSection({
  title,
  tooltip,
  expanded,
  onToggle,
  children,
}: {
  title: string;
  tooltip?: string;
  expanded: boolean;
  onToggle: () => void;
  children: React.ReactNode;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const sectionId = useId();

  const heading = <Typography variant="subtitle1">{title}</Typography>;

  return (
    <>
      <Box
        sx={{
          p: 1,
          backgroundColor: theme.palette.grey[100],
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          cursor: "pointer",
        }}
        onClick={onToggle}
      >
        {tooltip ? <CustomTooltip title={tooltip}>{heading}</CustomTooltip> : heading}
        <IconButton
          size="small"
          aria-label={
            expanded
              ? t("formSections.collapseSectionNamed", { section: title })
              : t("formSections.expandSectionNamed", { section: title })
          }
          aria-expanded={expanded}
          aria-controls={sectionId}
          sx={{
            transform: expanded ? "rotate(180deg)" : "rotate(0deg)",
            transition: theme.transitions.create("transform"),
          }}
        >
          <ExpandMoreIcon />
        </IconButton>
      </Box>
      <Divider />
      <Collapse in={expanded} id={sectionId}>
        {children}
      </Collapse>
    </>
  );
}
