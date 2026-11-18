import Button from "@mui/material/Button";
import React, { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { FEATURE_FLAGS } from "@/featureFlags/generatedFeatureFlags";
import { useIsFeatureFlagEnabled } from "@/featureFlags/queries";
import BondStrengthsDialog from "./BondStrengthsDialog";
import type { BondStrengthsDocumentContent } from "./insertIntoDocument";

type BondStrengthsActionProps = {
  chemId: number;
  revision?: number;
  onInsert?: (content: BondStrengthsDocumentContent) => void | Promise<void>;
};

/**
 * The feature flag check suspends while it gets a token and throws when it cannot get one. This
 * keeps such a failure to the button, so the chemistry card and its host stay on screen.
 */
class HideOnError extends React.Component<{ children: React.ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError(): { failed: boolean } {
    return { failed: true };
  }

  componentDidCatch(error: unknown): void {
    console.warn("Bond strengths are not available", error);
  }

  render(): React.ReactNode {
    return this.state.failed ? null : this.props.children;
  }
}

function ActionWhenEnabled({ chemId, revision, onInsert }: BondStrengthsActionProps) {
  const enabled = useIsFeatureFlagEnabled(FEATURE_FLAGS.bondStrengthsEnabled);
  const [open, setOpen] = React.useState(false);
  const { t } = useTranslation("workspace");

  if (!enabled) return null;

  return (
    <>
      <Button size="small" onClick={() => setOpen(true)} sx={{ m: 1 }}>
        {t("tinymce.bondStrengths.action")}
      </Button>
      <BondStrengthsDialog
        open={open}
        onClose={() => setOpen(false)}
        chemId={chemId}
        revision={revision}
        onInsert={onInsert}
      />
    </>
  );
}

/** Needs a React Query `QueryClientProvider` above it. Shows nothing unless the feature flag is on. */
export default function BondStrengthsAction(props: BondStrengthsActionProps) {
  return (
    <HideOnError>
      <Suspense fallback={null}>
        <ActionWhenEnabled {...props} />
      </Suspense>
    </HideOnError>
  );
}
