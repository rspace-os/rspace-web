import Chip from "@mui/material/Chip";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import AnalyticsContext from "../stores/contexts/Analytics";
import NavigateContext from "../stores/contexts/Navigate";
import type { LinkableRecord } from "../stores/definitions/LinkableRecord";
import RecordIcon from "./RecordTypeIcon";

type GlobalIdArgs = {
  record: LinkableRecord;
  size?: "medium" | "small";
  onClick?: () => void;
};

function GlobalId({ record, onClick }: GlobalIdArgs): React.ReactNode {
  const { useNavigate } = useContext(NavigateContext);
  const { trackEvent } = useContext(AnalyticsContext);
  const navigate = useNavigate();

  const handleClick = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (record.permalinkURL !== null && typeof record.permalinkURL !== "undefined") {
      navigate(record.permalinkURL);
      trackEvent("GlobalIdClicked");
      onClick?.();
    }
  };

  return (
    <Chip
      component="a"
      sx={(theme) => ({
        userSelect: "unset",
        pl: 1.5,
        height: theme.spacing(3),
        fontWeight: theme.typography.fontWeightRegular,
        "&:focus-visible": {
          outline: `2px solid ${theme.palette.primary.main}`,
        },
        transitionDuration: "500ms",
      })}
      /* this is how right-click copy works */
      {...(record.permalinkURL !== null ? { href: record.permalinkURL } : {})}
      label={record.globalId}
      icon={<RecordIcon record={record} aria-hidden={true} />}
      clickable
      onClick={handleClick}
      color="default"
    />
  );
}

export default observer(GlobalId);
