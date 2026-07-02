import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Link from "@mui/material/Link";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import NavigateContext from "../../stores/contexts/Navigate";
import ContainerModel from "../../stores/models/ContainerModel";
import type InventoryBaseRecord from "../../stores/models/InventoryBaseRecord";

type HistoricalVersionAlertArgs = {
  record: InventoryBaseRecord;
};

/**
 * Shown above the form of a historical (read-only) version of an inventory
 * record, mirroring the template version banner: states which version is
 * being viewed and links back to the latest state of the record.
 */
function HistoricalVersionAlert({ record }: HistoricalVersionAlertArgs): React.ReactNode {
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const { t } = useTranslation("inventory");

  if (!record.historicalVersion) return null;
  if (!record.id) return null;

  const latestUrl = `/inventory/${record.recordType.toLowerCase()}/${record.id}`;
  const typeLabel = record.recordTypeLabel.toLowerCase() || "record";

  return (
    <Alert severity="info">
      <AlertTitle>{t("historicalVersion.title", { version: record.version, type: typeLabel })}</AlertTitle>
      <div>
        <TransRichText
          i18nKey="inventory:historicalVersion.readOnlyWithLink"
          components={{
            a: (
              <Link
                href={latestUrl}
                underline="always"
                // accentedTheme (which wraps the Inventory views) overrides links
                // inside an info Alert: MuiAlert.standardInfo repaints all typography
                // the dark alert colour and the MuiAppBar block strips the underline
                // (`text-decoration: unset !important`, replaced by a hover-only bar),
                // both with higher specificity than a plain sx rule. Self-double the
                // selector (`&&&`) to win the cascade and restore the standard
                // Inventory link look: the theme's linkColor (exposed as
                // primary.dark), bold, and a persistent underline.
                sx={(theme) => ({
                  "&&&": {
                    color: `${theme.palette.primary.dark} !important`,
                    fontWeight: 700,
                    textDecoration: "underline !important",
                  },
                })}
                onClick={(e: React.MouseEvent) => {
                  e.preventDefault();
                  navigate(latestUrl);
                }}
              />
            ),
          }}
        />
      </div>
      {record instanceof ContainerModel && <div>{t("historicalVersion.contentsNotShown")}</div>}
    </Alert>
  );
}

export default observer(HistoricalVersionAlert);
