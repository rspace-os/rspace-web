import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useSearch } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { ApiV2ProblemError, parseApiV2Problem } from "@/modules/booking/domain/booking";
import { useBookingLocalNotices, useBookingNotices } from "@/modules/booking/feedback/BookingNotices";
import { apiV2JsonHeaders } from "@/modules/common/api/apiV2Headers";
import { leaveResource } from "@/modules/common/resource-access/resourceAccess";
import { buttonVariants } from "@/modules/common/ui/button";
import type { BookingConfigurationLifecycleAction } from "./BookingConfigurationActionsMenu";
import { calendarSubscriptionQueryKey } from "./bookableItemCalendarSubscription";
import {
  archiveBookingConfiguration,
  BOOKING_CONFIGURATION_READ_FIELDS,
  type BookingConfiguration,
  type BookingConfigurationUpdateInput,
  bookingConfigurationTargetQueryKey,
  invalidateBookingCatalogueQueries,
  permanentlyDeleteBookingConfiguration,
  restoreBookingConfiguration,
} from "./bookingConfiguration";

export type BookableItemTab = "bookings" | "details" | "audit" | "access";

async function updateBookingConfiguration(
  id: number,
  version: number,
  input: BookingConfigurationUpdateInput,
  token: string,
): Promise<void> {
  const search = new URLSearchParams({
    depth: "1",
    "fields[booking-configurations]": BOOKING_CONFIGURATION_READ_FIELDS,
  });
  const response = await fetch(`/api/v2/booking-configurations/${id}?${search}`, {
    method: "PATCH",
    headers: apiV2JsonHeaders(token, { "If-Match": `"${version}"` }),
    body: JSON.stringify(input),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
}

type LoadedBookableItemPageActionsOptions = {
  configuration: BookingConfiguration;
  globalId: string;
  tab: BookableItemTab;
  token: string;
};

export function useLoadedBookableItemPageActions({
  configuration,
  globalId,
  tab,
  token,
}: LoadedBookableItemPageActionsOptions) {
  const { t } = useTranslation("booking");
  const { t: commonT } = useTranslation("common");
  const target = configuration.target;
  const { edit = false } = useSearch({ from: "/booking/bookable-items/$globalId/{-$tab}/" });
  const navigate = useNavigate({ from: "/booking/bookable-items/$globalId/{-$tab}/" });
  const queryClient = useQueryClient();
  const notices = useBookingNotices();
  const localNotices = useBookingLocalNotices();
  const [saveAnnouncement, setSaveAnnouncement] = useState<"archived" | "restored" | null>(null);
  const [staleEdit, setStaleEdit] = useState(false);
  // The server version loaded after a conflict; the next Save of the kept draft targets it.
  const [conflictVersion, setConflictVersion] = useState<number | null>(null);
  const [rulesSaveBlocked, setRulesSaveBlocked] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [permanentDeleteOpen, setPermanentDeleteOpen] = useState(false);
  const [permanentDeleteConfirmation, setPermanentDeleteConfirmation] = useState("");
  const [leaveOpen, setLeaveOpen] = useState(false);
  const editButtonRef = useRef<HTMLButtonElement>(null);
  const saveButtonRef = useRef<HTMLButtonElement>(null);
  const actionsButtonRef = useRef<HTMLButtonElement>(null);
  const wasEditing = useRef(false);
  const canEdit = configuration.capabilities.canEditConfiguration;
  const active = configuration.state === "ACTIVE";
  const editing = active && canEdit && edit;
  const setEdit = (next: boolean) =>
    void navigate({
      search: next ? { edit: true } : {},
      replace: true,
    });

  const setTab = (next: BookableItemTab) =>
    void navigate({
      to: "/booking/bookable-items/$globalId/{-$tab}",
      params: { globalId, tab: next === "bookings" ? undefined : next },
      search: edit ? { edit: true } : {},
      replace: true,
      resetScroll: false,
    });

  const updateMutation = useMutation({
    mutationFn: ({ input, version }: { input: BookingConfigurationUpdateInput; version: number }) =>
      updateBookingConfiguration(configuration.id, version, input, token),
    onMutate: () => setSaveAnnouncement(null),
    onSuccess: async (_data, { input }) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        invalidateBookingCatalogueQueries(queryClient),
      ]);
      localNotices.notify({
        id: `bookable-item-${globalId}`,
        tone: "success",
        message: t("bookableItemDetails.update.saved"),
        actions: input.enabled ? (
          <Link
            to="/booking/calendar"
            search={{ target: globalId }}
            className={buttonVariants({ variant: "outline", size: "sm" })}
          >
            {t("bookableItems.feedback.viewCalendar")}
          </Link>
        ) : undefined,
      });
      setStaleEdit(false);
      setConflictVersion(null);
      setEdit(false);
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 412) {
        setStaleEdit(true);
        const queryKey = bookingConfigurationTargetQueryKey(globalId);
        await queryClient.refetchQueries({ queryKey });
        const latest = queryClient.getQueryData<BookingConfiguration>(queryKey);
        if (latest) setConflictVersion(latest.configurationVersion);
      }
    },
  });
  const archiveMutation = useMutation({
    onMutate: () => setSaveAnnouncement(null),
    mutationFn: () => archiveBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-catalogue"] }),
        queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(configuration.id) }),
      ]);
      setArchiveOpen(false);
      localNotices.clear();
      setSaveAnnouncement("archived");
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 412) {
        await queryClient.refetchQueries({
          queryKey: bookingConfigurationTargetQueryKey(globalId),
        });
      }
    },
  });
  const restoreMutation = useMutation({
    onMutate: () => setSaveAnnouncement(null),
    mutationFn: () => restoreBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "bookings"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-catalogue"] }),
        queryClient.invalidateQueries({ queryKey: calendarSubscriptionQueryKey(configuration.id) }),
      ]);
      setSaveAnnouncement("restored");
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && error.status === 412) {
        await queryClient.refetchQueries({
          queryKey: bookingConfigurationTargetQueryKey(globalId),
        });
      }
    },
  });
  const permanentDeleteMutation = useMutation({
    mutationFn: () =>
      permanentlyDeleteBookingConfiguration(configuration.id, configuration.configurationVersion, token),
    onSuccess: async () => {
      // The deleted configuration's instrument becomes an eligible target again.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configuration-targets"] }),
      ]);
      setPermanentDeleteOpen(false);
      notices.notify("bookable-items", {
        id: `bookable-item-${globalId}`,
        tone: "success",
        message: t("bookableItems.feedback.deleted", {
          item: target?.value.name ?? commonT("values.unknownItem"),
        }),
      });
      void navigate({ to: "/booking/config/bookable-items", ignoreBlocker: true });
    },
    onError: async (error) => {
      if (error instanceof ApiV2ProblemError && (error.status === 409 || error.status === 412)) {
        await queryClient.refetchQueries({
          queryKey: bookingConfigurationTargetQueryKey(globalId),
        });
      }
    },
  });
  const leaveMutation = useMutation({
    mutationFn: () => leaveResource("booking-configurations", configuration.id, token),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["api-v2", "booking-configurations"] }),
        invalidateBookingCatalogueQueries(queryClient),
      ]);
      void navigate({ to: "/booking", ignoreBlocker: true });
    },
  });

  useEffect(() => {
    if (wasEditing.current && !editing) editButtonRef.current?.focus();
    wasEditing.current = editing;
  }, [editing]);

  useEffect(() => {
    if (updateMutation.isError && !updateMutation.isPending) saveButtonRef.current?.focus();
  }, [updateMutation.isError, updateMutation.isPending]);

  useEffect(() => {
    // onSuccess runs before the mutation clears pending and re-enables this button.
    if (
      archiveMutation.isPending ||
      restoreMutation.isPending ||
      (!archiveMutation.isSuccess && !restoreMutation.isSuccess)
    ) {
      return;
    }
    const frame = requestAnimationFrame(() => actionsButtonRef.current?.focus());
    return () => cancelAnimationFrame(frame);
  }, [archiveMutation.isPending, archiveMutation.isSuccess, restoreMutation.isPending, restoreMutation.isSuccess]);

  useEffect(() => {
    if (!active && edit) setEdit(false);
  }, [active, edit]);

  useEffect(() => {
    if (tab === "audit" && !configuration.capabilities.canViewAudit) {
      setTab("bookings");
    }
  }, [configuration.capabilities.canViewAudit, tab]);

  const cancelEdit = () => {
    setSaveAnnouncement(null);
    setStaleEdit(false);
    setConflictVersion(null);
    setEdit(false);
  };

  const discardConflictedDraft = () => {
    updateMutation.reset();
    setStaleEdit(false);
    setConflictVersion(null);
  };

  const handleLifecycleAction = (action: BookingConfigurationLifecycleAction) => {
    if (action === "archive") setArchiveOpen(true);
    if (action === "restore") restoreMutation.mutate();
    if (action === "permanent-delete") {
      setPermanentDeleteConfirmation("");
      setPermanentDeleteOpen(true);
    }
  };

  return {
    saveAnnouncement,
    staleEdit,
    archiveOpen,
    setArchiveOpen,
    permanentDeleteOpen,
    setPermanentDeleteOpen,
    permanentDeleteConfirmation,
    setPermanentDeleteConfirmation,
    leaveOpen,
    setLeaveOpen,
    editButtonRef,
    saveButtonRef,
    actionsButtonRef,
    canEdit,
    active,
    editing,
    setEdit,
    setTab,
    updateMutation,
    archiveMutation,
    restoreMutation,
    permanentDeleteMutation,
    leaveMutation,
    cancelEdit,
    handleLifecycleAction,
    rulesSaveBlocked,
    setRulesSaveBlocked,
    conflictVersion,
    discardConflictedDraft,
    localNotices,
  };
}
