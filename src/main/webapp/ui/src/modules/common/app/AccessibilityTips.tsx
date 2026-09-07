import { AccessibilityIcon } from "lucide-react";
import { useSyncExternalStore } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { Button } from "@/modules/common/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/modules/common/ui/dialog";

const HIGH_CONTRAST_QUERY = "(prefers-contrast: more), (forced-colors: active)";

function subscribeToHighContrast(onChange: () => void) {
  const query = window.matchMedia(HIGH_CONTRAST_QUERY);
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}

function highContrastIsEnabled() {
  return window.matchMedia(HIGH_CONTRAST_QUERY).matches;
}

export default function AccessibilityTips() {
  const { t } = useTranslation("common");
  const highContrastEnabled = useSyncExternalStore(subscribeToHighContrast, highContrastIsEnabled, () => false);

  return (
    <Dialog>
      <DialogTrigger render={<Button variant="ghost" size="icon-sm" aria-label={t("accessibilityTips.buttonLabel")} />}>
        <AccessibilityIcon />
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("accessibilityTips.buttonLabel")}</DialogTitle>
          <DialogDescription>
            {highContrastEnabled
              ? t("accessibilityTips.highContrast.enabled")
              : t("accessibilityTips.highContrast.supported", {
                  elementType: t("accessibilityTips.elementTypes.page"),
                })}
          </DialogDescription>
        </DialogHeader>
        <div className="text-sm">
          <TransRichText
            i18nKey={
              highContrastEnabled
                ? "common:accessibilityTips.highContrast.body.disable"
                : "common:accessibilityTips.highContrast.body.enable"
            }
          />
        </div>
      </DialogContent>
    </Dialog>
  );
}
