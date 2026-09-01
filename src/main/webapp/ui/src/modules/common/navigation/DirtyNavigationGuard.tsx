import { useBlocker, useRouter } from "@tanstack/react-router";
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

/** Blocks only navigation/unload that would unmount and discard a dirty editor. */
export function DirtyNavigationGuard({ dirty }: { dirty: boolean }) {
  const router = useRouter({ warn: false });
  return router ? <RouterDirtyNavigationGuard dirty={dirty} /> : null;
}

function RouterDirtyNavigationGuard({ dirty }: { dirty: boolean }) {
  const { t } = useTranslation("common");
  const blocker = useBlocker({
    shouldBlockFn: ({ current, next }) => dirty && current.pathname !== next.pathname,
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
