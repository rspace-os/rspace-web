import type { TFunction } from "i18next";

/**
 * The shared, non-UI shape of a sample request and its two list filters - kept out of
 * RequestsList.tsx (a component file) so that modules with no business rendering this list (e.g.
 * queries.ts) don't need to import from one, and so Vite's fast refresh - which handles a module
 * that only exports components best - isn't tripped up by this one also exporting plain data.
 */
export type ApiSampleRequestListItem = {
  id: number;
  status: string;
  created: string;
  note: string | null;
  requester: { id: number; username: string; firstName: string | null; lastName: string | null };
  sample: { id: number; globalId: string; name: string; owner: { id: number } };
};

export type RequestsFilter = "all" | "sent" | "received";
export type StatusFilter = "all" | "active" | "past";

/**
 * Shared by every place a SampleRequest's response names a user by id/username/firstName/
 * lastName: the requester, and each status change's createdBy. The id is always present, whether
 * or not that account still exists; a null firstName and lastName together mean the account has
 * since been deleted - there is then no current name to show, so username is the one field
 * guaranteed to still meaningfully identify who it was.
 *
 * Takes `t` rather than calling useTranslation itself so it can be shared by any caller already
 * resolving the "inventory" namespace (as every current caller does) regardless of which other
 * namespaces they also requested - i18next's generated TFunction type is otherwise invariant in
 * its namespace list, which would make this reject some of those callers for no real reason.
 */
export function userDisplayName(
  person: { id: number; username: string; firstName: string | null; lastName: string | null },
  t: TFunction<readonly ["inventory", "common"]>,
): string {
  return person.firstName === null && person.lastName === null
    ? t("inventory:requestsManagement.userDeleted", { username: person.username })
    : `${person.firstName} ${person.lastName}`;
}
