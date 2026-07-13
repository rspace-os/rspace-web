import Button from "@mui/material/Button";
import type React from "react";
import { useTranslation } from "react-i18next";

/**
 * A simple button, styled for use in the gallery listing to load more items.
 */
export default function LoadMoreButton({ onClick }: { onClick: () => Promise<void> }): React.ReactNode {
  const { t } = useTranslation("gallery");

  return (
    <Button
      onClick={() => void onClick()}
      sx={{
        marginBottom: "16px",
        marginTop: "8px",
        marginRight: "auto",
        paddingLeft: "32px",
        paddingRight: "32px",
      }}
    >
      {t("loadMore")}
    </Button>
  );
}
