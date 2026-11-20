import { type ShouldBlockFn, useBlocker, useRouter } from "@tanstack/react-router";
import { useTranslation } from "react-i18next";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/modules/common/ui/alert-dialog";

type DirtyNavigationGuardProps = {
  dirty: boolean;
  /** Override when the editor survives some pathname changes, such as retained tabs. */
  shouldBlockNavigation?: ShouldBlockFn;
};

/** Blocks only navigation/unload that would unmount and discard a dirty editor. */
export function DirtyNavigationGuard(props: DirtyNavigationGuardProps) {
  const router = useRouter({ warn: false });
  return router ? <RouterDirtyNavigationGuard {...props} /> : null;
}

function RouterDirtyNavigationGuard({
  dirty,
  shouldBlockNavigation = ({ current, next }) => current.pathname !== next.pathname,
}: DirtyNavigationGuardProps) {
  const { t } = useTranslation("common");
  const blocker = useBlocker({
    shouldBlockFn: (navigation) => dirty && shouldBlockNavigation(navigation),
    withResolver: true,
    enableBeforeUnload: dirty,
  });
  const blocked = blocker.status === "blocked";
  return (
    <AlertDialog open={blocked} onOpenChange={(open) => !open && blocked && blocker.reset()}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{t("dirtyNavigation.title")}</AlertDialogTitle>
          <AlertDialogDescription>{t("dirtyNavigation.message")}</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel onClick={() => blocked && blocker.reset()}>{t("actions.cancel")}</AlertDialogCancel>
          <AlertDialogAction variant="destructive" onClick={() => blocked && blocker.proceed()}>
            {t("dirtyNavigation.leave")}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
