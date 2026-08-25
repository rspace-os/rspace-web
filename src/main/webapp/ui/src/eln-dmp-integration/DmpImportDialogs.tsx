import CardMedia from "@mui/material/CardMedia";
import Divider from "@mui/material/Divider";
import { useQuery } from "@tanstack/react-query";
import React from "react";
import { useTranslation } from "react-i18next";
import { LOGO_COLOR as ARGOS_LOGO_COLOR } from "@/assets/branding/argos";
import ArgosIcon from "@/assets/branding/argos/logo.svg";
import { LOGO_COLOR as DMP_ASSISTANT_LOGO_COLOR } from "@/assets/branding/dmpassistant";
import DMPAssistantIcon from "@/assets/branding/dmpassistant/logo.svg";
import { LOGO_COLOR as DMPONLINE_LOGO_COLOR } from "@/assets/branding/dmponline";
import DMPOnlineIcon from "@/assets/branding/dmponline/logo.svg";
import { LOGO_COLOR as DMPTOOL_LOGO_COLOR } from "@/assets/branding/dmptool";
import DMPToolIcon from "@/assets/branding/dmptool/logo.svg";
import { LOGO_COLOR as DSW_LOGO_COLOR } from "@/assets/branding/dsw";
import DSWIcon from "@/assets/branding/dsw/logo.svg";
import AccentMenuItem from "@/components/AccentMenuItem";
import { type IntegrationStates, useIntegrationsEndpoint } from "@/eln/apps/useIntegrationsEndpoint";
import AnalyticsContext from "@/stores/contexts/Analytics";
import * as ArrayUtils from "@/util/ArrayUtils";
import ArgosDMPDialog from "./Argos/DMPDialog";
import DMPAssistantDMPDialog from "./DMPAssistant/DMPDialog";
import DMPOnlineDMPDialog from "./DMPOnline/DMPDialog";
import DMPToolDMPDialog from "./DMPTool/DMPDialog";
import DSWImportDialog from "./DSW/DSWImportDialog";

export type DswConfig = {
  DSW_APIKEY: string;
  DSW_URL: string;
  DSW_ALIAS: string;
};

const DMP_SOURCES = [
  {
    source: "argos",
    integration: "ARGOS",
    labelKey: "dmpIntegrations.argos",
    icon: ArgosIcon,
    logoColor: ARGOS_LOGO_COLOR,
    foregroundLightness: 28,
    Dialog: ArgosDMPDialog,
  },
  {
    source: "dmpAssistant",
    integration: "DMPASSISTANT",
    labelKey: "dmpIntegrations.dmpAssistant",
    icon: DMPAssistantIcon,
    logoColor: DMP_ASSISTANT_LOGO_COLOR,
    foregroundLightness: 30,
    Dialog: DMPAssistantDMPDialog,
  },
  {
    source: "dmponline",
    integration: "DMPONLINE",
    labelKey: "dmpIntegrations.dmponline",
    icon: DMPOnlineIcon,
    logoColor: DMPONLINE_LOGO_COLOR,
    foregroundLightness: 30,
    Dialog: DMPOnlineDMPDialog,
  },
  {
    source: "dmptool",
    integration: "DMPTOOL",
    labelKey: "dmpIntegrations.dmptool",
    icon: DMPToolIcon,
    logoColor: DMPTOOL_LOGO_COLOR,
    foregroundLightness: 30,
    Dialog: DMPToolDMPDialog,
  },
] as const satisfies ReadonlyArray<{
  source: string;
  integration: keyof IntegrationStates;
  labelKey: string;
  icon: string;
  logoColor: { hue: number; saturation: number; lightness: number };
  foregroundLightness: number;
  Dialog: React.ComponentType<{ open: boolean; setOpen: (open: boolean) => void; onImport?: () => void }>;
}>;

type DmpSource = (typeof DMP_SOURCES)[number]["source"];

export type DmpImportTarget = { source: DmpSource } | { source: "dsw"; connection: DswConfig };

export function DmpImportMenuSection({ onSelect }: { onSelect: (target: DmpImportTarget) => void }): React.ReactNode {
  const { allIntegrations } = useIntegrationsEndpoint();
  const { data: integrationStates } = useQuery({
    queryKey: ["integration", "allIntegrations"],
    queryFn: () => allIntegrations(),
    staleTime: 60_000,
  });
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { t } = useTranslation(["gallery", "apps"]);
  const enabledSources = DMP_SOURCES.filter(({ integration }) => integrationStates?.[integration].mode === "ENABLED");
  const showDsw = integrationStates?.DSW.mode === "ENABLED";
  const dswConnections = showDsw ? ArrayUtils.mapOptional((config) => config, integrationStates.DSW.credentials) : [];

  if (enabledSources.length === 0 && !showDsw) return null;
  return (
    <>
      <Divider textAlign="left" aria-label={t("gallery:sidebar.dmpsLabel")}>
        {t("gallery:sidebar.dmpImport")}
      </Divider>
      {enabledSources.map(({ source, labelKey, icon, logoColor, foregroundLightness }) => (
        <AccentMenuItem
          key={source}
          title={t(`apps:${labelKey}`)}
          avatar={<CardMedia image={icon} />}
          backgroundColor={logoColor}
          foregroundColor={{ ...logoColor, lightness: foregroundLightness }}
          onClick={() => onSelect({ source })}
          aria-haspopup="dialog"
          compact
        />
      ))}
      {dswConnections.map((connection) => (
        <AccentMenuItem
          key={connection.optionsId}
          title={connection.DSW_ALIAS}
          subheader={t("apps:dmpIntegrations.dsw")}
          avatar={<CardMedia image={DSWIcon} />}
          backgroundColor={DSW_LOGO_COLOR}
          foregroundColor={{ ...DSW_LOGO_COLOR, lightness: 30 }}
          onClick={() => {
            trackEvent("user:open:dsw_import:gallery");
            onSelect({ source: "dsw", connection });
          }}
          aria-haspopup="dialog"
          compact
        />
      ))}
    </>
  );
}

/** Retains the selected dialog until its exit transition completes. */
export default function DmpImportDialogs({
  target,
  onClose,
  onImport,
}: {
  target: DmpImportTarget | null;
  onClose: () => void;
  onImport: () => void;
}): React.ReactNode {
  const [lastTarget, setLastTarget] = React.useState<DmpImportTarget | null>(target);
  React.useEffect(() => {
    if (target) setLastTarget(target);
  }, [target]);

  const shown = target ?? lastTarget;
  if (!shown) return null;

  const setOpen = (open: boolean) => {
    if (!open) onClose();
  };
  if (shown.source === "dsw") {
    return <DSWImportDialog open={Boolean(target)} setOpen={setOpen} connection={shown.connection} />;
  }
  const SourceDialog = DMP_SOURCES.find(({ source }) => source === shown.source)?.Dialog;
  return SourceDialog ? <SourceDialog open={Boolean(target)} setOpen={setOpen} onImport={onImport} /> : null;
}
