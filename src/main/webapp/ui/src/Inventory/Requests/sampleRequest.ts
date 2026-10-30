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
  requester: { id: number; username: string; firstName: string; lastName: string };
  sample: { id: number; globalId: string; name: string; owner: { id: number } };
};

export type RequestsFilter = "all" | "sent" | "received";
export type StatusFilter = "all" | "active" | "past";
