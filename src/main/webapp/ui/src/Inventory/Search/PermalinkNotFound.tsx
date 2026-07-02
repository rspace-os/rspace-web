import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import type { Permalink, PermalinkType } from "../../stores/definitions/Search";

const TYPE_LABEL_KEYS = {
  sample: "recordTypes.sample.lower",
  subsample: "recordTypes.subsample.lower",
  container: "recordTypes.container.lower",
  sampletemplate: "recordTypes.sampleTemplate.lower",
  instrument: "recordTypes.instrument.lower",
  instrumenttemplate: "recordTypes.instrumentTemplate.lower",
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
  const latestUrl = `/inventory/${permalink.type}/${permalink.id}`;
  const typeLabel = t(TYPE_LABEL_KEYS[permalink.type]);

  return (
    <Box sx={{ p: 2 }}>
      {permalink.version != null ? (
        <Alert severity="warning">
          <AlertTitle>{t("permalinkNotFound.versionedTitle", { typeLabel, version: permalink.version })}</AlertTitle>
          <TransRichText ns="inventory" i18nKey="permalinkNotFound.versionedBody" values={{ link: latestUrl }} />
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
