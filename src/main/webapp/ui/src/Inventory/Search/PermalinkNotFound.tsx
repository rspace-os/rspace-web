import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import NavigateContext from "../../stores/contexts/Navigate";
import type { Permalink, PermalinkType } from "../../stores/definitions/Search";

const TYPE_LABEL_KEYS = {
  sample: "permalinkNotFound.typeLabels.sample",
  subsample: "permalinkNotFound.typeLabels.subsample",
  container: "permalinkNotFound.typeLabels.container",
  sampletemplate: "permalinkNotFound.typeLabels.sampleTemplate",
} as const satisfies Record<PermalinkType, string>;

type PermalinkNotFoundArgs = {
  permalink: Permalink;
};

/**
 * Shown in the right panel when a permalink points at a record, or a version
 * of a record, that cannot be found. Versioned permalinks get a specific
 * message with a link to the latest state of the record.
 */
function PermalinkNotFound({ permalink }: PermalinkNotFoundArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const latestUrl = `/inventory/${permalink.type}/${permalink.id}`;
  const typeLabel = t(TYPE_LABEL_KEYS[permalink.type]);

  return (
    <Box sx={{ p: 2 }}>
      {permalink.version != null ? (
        <Alert severity="warning">
          <AlertTitle>{t("permalinkNotFound.versionedTitle", { typeLabel, version: permalink.version })}</AlertTitle>
          {t("permalinkNotFound.versionedBody")}{" "}
          <Link
            href={latestUrl}
            onClick={(e: React.MouseEvent) => {
              e.preventDefault();
              navigate(latestUrl);
            }}
          >
            {t("permalinkNotFound.latestLink")}
          </Link>
          .
        </Alert>
      ) : (
        <Alert severity="warning">
          <AlertTitle>{t("permalinkNotFound.unversionedTitle", { typeLabel })}</AlertTitle>
          {t("permalinkNotFound.unversionedBody")}
        </Alert>
      )}
    </Box>
  );
}

export default observer(PermalinkNotFound);
