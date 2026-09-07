/** @prototype Storybook-only UI exploration; not production-ready. */
/**
 * Storybook-only Inventory chrome for the Sample Requests prototype, so the
 * stories can be read in the context of the wider system.
 *
 * Real production pieces: `Header` (the app bar, fed by MSW with the same
 * app-shell fixtures the browser tests use), `CreateNew`, `DrawerTab`,
 * `UiStore` (sidebar and panel state) and `Main`. Stand-ins: the sidebar's
 * item list (production `Sidebar` reads the MobX search store and
 * `window.location`, and has no Requests entry yet) and the store container.
 */
import { faFileExport } from "@fortawesome/free-solid-svg-icons/faFileExport";
import { faMicroscope } from "@fortawesome/free-solid-svg-icons/faMicroscope";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import OutboxIcon from "@mui/icons-material/Outbox";
import Box from "@mui/material/Box";
import Divider from "@mui/material/Divider";
import Drawer, { drawerClasses } from "@mui/material/Drawer";
import List from "@mui/material/List";
import { useTheme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import { HttpResponse, http } from "msw";
import { setupWorker } from "msw/browser";
import React from "react";
import { useTranslation } from "react-i18next";
import { OAUTH_TOKEN } from "@/__tests__/mocks/oauthTokenMocks";
import { appShellHandlers } from "@/__tests__/mswAppShellHandlers";
import IgsnIcon from "@/assets/graphics/RecordTypeGraphics/Icons/IgsnIcon";
import MyBenchIcon from "@/assets/graphics/RecordTypeGraphics/Icons/MyBench";
import DrawerTab from "@/components/DrawerTab";
import { LandmarksProvider } from "@/components/LandmarksContext";
import RecordTypeIcon from "@/components/RecordTypeIcon";
import CreateNew from "@/Inventory/components/CreateNew";
import PeopleField from "@/Inventory/components/Inputs/PeopleField";
import Header from "@/Inventory/components/Layout/Header";
import Main from "@/Inventory/Main";
import type { RequestableSample, Requester } from "@/Inventory/Requests/types";
import { personAttrs } from "@/stores/models/__tests__/PersonModel/mocking";
import { sampleAttrs } from "@/stores/models/__tests__/SampleModel/mocking";
import type PersonModel from "@/stores/models/PersonModel";
import type { RootStore, StoreContainer } from "@/stores/stores/RootStore";
import UiStore from "@/stores/stores/UiStore";
import { storesContext } from "@/stores/stores-context";
import useStores from "@/stores/use-stores";
import RsSet from "@/util/set";

/** Real user selector with only the prototype's requesters as its directory. */
export function RequesterFilterPrototype({
  requesters,
  selectedId,
  onSelection,
  label,
}: {
  requesters: ReadonlyArray<Requester>;
  selectedId: number | null;
  onSelection: (id: number | null) => void;
  label: string;
}) {
  const parentStores = useStores();
  // ponytail: display-only people fixtures; use PeopleStore/API data for a live directory.
  const people = requesters.map(
    (person) =>
      ({
        ...person,
        label: `${person.fullName} (${person.username})`,
        groupByLabel: person.fullName[0],
        isCurrentUser: false,
      }) as PersonModel,
  );
  const pickerStores = {
    ...parentStores,
    peopleStore: { currentUser: null, groupMembers: new RsSet(people) },
    searchStore: { search: { fetcher: {} } },
  } as unknown as StoreContainer;
  return (
    <storesContext.Provider value={pickerStores}>
      <PeopleField
        label={label}
        labelPlacement="outline"
        autoFocus={false}
        outsideGroup={false}
        recipient={people.find((person) => person.id === selectedId) ?? null}
        onSelection={(person) => onSelection(person?.id ?? null)}
      />
    </storesContext.Provider>
  );
}

/*
 * The API the app bar calls on render. `worker.start` registers the service
 * worker served from `.storybook/main.ts`'s staticDirs; it is started once and
 * the persona handlers swapped per story.
 */
const worker = setupWorker(...appShellHandlers());
export function mockSampleSearch(samples: ReadonlyArray<RequestableSample>) {
  const fixtures = samples.map((sample) =>
    sampleAttrs({
      id: Number(sample.globalId.slice(2)),
      globalId: sample.globalId,
      name: sample.name,
      owner: personAttrs({
        ...sample.owner,
        firstName: sample.owner.fullName.split(" ")[0],
        lastName: sample.owner.fullName.split(" ").slice(1).join(" "),
      }),
    }),
  );
  worker.use(
    http.get("/api/inventory/v1/samples/:id", ({ params }) => {
      const record = fixtures.find((sample) => sample.id === Number(params.id));
      return record ? HttpResponse.json(record) : new HttpResponse(null, { status: 404 });
    }),
    http.get(/\/api\/inventory\/v1\/(search|samples)$/, ({ request }) => {
      const params = new URL(request.url).searchParams;
      // ponytail: fixture search supports plain names/IDs, not Inventory's advanced query language.
      const query = (params.get("query") ?? "").replace(/\*$/, "").toLowerCase();
      const records =
        params.get("deletedItems") === "DELETED_ONLY"
          ? []
          : fixtures.filter((sample) => `${sample.globalId} ${sample.name}`.toLowerCase().includes(query));
      const field = params.get("orderBy") === "globalId" ? "globalId" : "name";
      records.sort(
        (a, b) => String(a[field]).localeCompare(String(b[field])) * (params.get("order") === "desc" ? -1 : 1),
      );
      const size = Number(params.get("pageSize") ?? 5);
      const page = Number(params.get("pageNumber") ?? 0);
      const results = records.slice(page * size, (page + 1) * size);
      return HttpResponse.json({ records: results, samples: results, totalHits: records.length });
    }),
  );
}
let started: Promise<unknown> | null = null;

// Stand-in for the deployment's banner image, which the dev server does not serve.
const BANNER = `data:image/svg+xml;utf8,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="96" height="28"><text x="0" y="20" font-family="Roboto, sans-serif" font-size="18" font-weight="600" fill="#264b58">RSpace</text></svg>',
)}`;

function personaHandlers(me: Requester) {
  const [firstName, ...rest] = me.fullName.split(" ");
  const lastName = rest.join(" ");
  const email = `${me.username}@example.org`;
  return [
    http.get("/userform/ajax/inventoryOauthToken", () => HttpResponse.json({ data: OAUTH_TOKEN })),
    http.get("/api/v1/userDetails/whoami", () =>
      HttpResponse.json({
        id: me.id,
        username: me.username,
        firstName,
        lastName,
        email,
        hasPiRole: false,
        hasSysAdminRole: false,
        workbenchId: me.id,
      }),
    ),
    http.get("/api/v1/userDetails/uiNavigationData", () =>
      HttpResponse.json({
        userDetails: {
          email,
          orcidId: null,
          orcidAvailable: false,
          fullName: me.fullName,
          username: me.username,
          profileImgSrc: null,
        },
        visibleTabs: { published: false, inventory: true, system: false, myLabGroups: true },
        extraHelpLinks: [],
        bannerImgSrc: BANNER,
        operatedAs: false,
        nextMaintenance: null,
      }),
    ),
    http.get("/integration/integrationInfo", () =>
      HttpResponse.json({ data: { name: "FIELDMARK", available: false, enabled: false, options: {} } }),
    ),
  ];
}

/** Story `loaders` entry: serve the app-shell API as `me`. */
export async function startApiAs(me: Requester): Promise<void> {
  started ??= worker.start({
    onUnhandledRequest: "bypass",
    quiet: true,
    serviceWorker: { url: new URL("mockServiceWorker.js", window.location.href).pathname },
  });
  await started;
  // resetHandlers(...) with arguments would replace the app-shell defaults; use() prepends instead
  worker.resetHandlers();
  worker.use(...personaHandlers(me));
}

/*
 * The real UiStore drives the sidebar and two-column layout. Its resize
 * listener reads the search store only when the layout switches column count.
 */
const fakeRoot = {
  searchStore: { search: { activeResult: null, filteredResults: [] } },
} as unknown as RootStore;
const stores = {
  uiStore: new UiStore(fakeRoot),
  searchStore: { savedSearches: [], savedBaskets: [], getBaskets: async () => {} },
} as unknown as StoreContainer;

export type SidebarSelection = "samples" | "requests";

const drawerWidth = 200;

const PrototypeSidebar = observer(
  ({ id, selected, requestsBadge }: { id: string; selected: SidebarSelection; requestsBadge?: number }) => {
    const { t } = useTranslation("inventory");
    const { uiStore } = useStores();
    const theme = useTheme();
    const open = uiStore.sidebarOpen;
    const iconColor = theme.palette.standardIcon.main;
    const recordIcon = (iconName: string) => (
      <RecordTypeIcon record={{ iconName, recordTypeLabel: "" }} color={iconColor} />
    );
    const items: ReadonlyArray<{ label: string; icon: React.ReactNode; selected?: boolean; badge?: number }> = [
      { label: t("layout.sidebar.myBench"), icon: <MyBenchIcon /> },
      { label: t("recordTypes.container.plural"), icon: recordIcon("container") },
      { label: t("recordTypes.sample.plural"), icon: recordIcon("sample"), selected: selected === "samples" },
      { label: t("recordTypes.subsample.plural"), icon: recordIcon("subsample") },
      { label: t("recordTypes.instrument.plural"), icon: <FontAwesomeIcon icon={faMicroscope} color={iconColor} /> },
      { label: t("recordTypes.sampleTemplate.plural"), icon: recordIcon("template") },
      { label: t("recordTypes.instrumentTemplate.plural"), icon: recordIcon("instrumentTemplate") },
      { label: t("layout.sidebar.igsnIds"), icon: <IgsnIcon sx={{ width: "16px", height: "16px" }} /> },
      {
        label: t("requests.sectionTitle"),
        icon: <OutboxIcon sx={{ color: iconColor }} />,
        selected: selected === "requests",
        badge: requestsBadge,
      },
    ];
    const width = open ? drawerWidth : theme.spacing(7.75);
    return (
      <Drawer
        open
        variant="persistent"
        id={id}
        sx={{
          width,
          flexShrink: 0,
          whiteSpace: "nowrap",
          overflowX: "hidden",
          [`& .${drawerClasses.paper}`]: { position: "relative", width, overflowX: "hidden" },
        }}
      >
        <Box aria-label={t("layout.sidebar.navigationLabel")}>
          <CreateNew onClick={() => {}} />
          <Divider />
          <List component="ul" aria-label={t("layout.sidebar.itemsListLabel")}>
            {items.map((item, index) => (
              <DrawerTab
                key={item.label}
                label={item.label}
                icon={item.icon}
                selected={item.selected ?? false}
                badge={item.badge}
                index={index}
                tabIndex={index === 0 ? 0 : -1}
                drawerOpen={open}
                onClick={() => {}}
              />
            ))}
          </List>
          <Divider />
          <List component="ul" aria-label={t("layout.sidebar.otherActionsLabel")}>
            <DrawerTab
              label={t("layout.sidebar.exportData")}
              icon={<FontAwesomeIcon icon={faFileExport} />}
              selected={false}
              index={items.length}
              tabIndex={-1}
              drawerOpen={open}
              onClick={() => {}}
            />
          </List>
        </Box>
      </Drawer>
    );
  },
);

/** The Inventory page frame from `SearchRouter`: app bar, sidebar, main. */
export default function InventoryChromePrototype({
  selected,
  requestsBadge,
  children,
}: {
  selected: SidebarSelection;
  requestsBadge?: number;
  children: React.ReactNode;
}): React.ReactNode {
  const sidebarId = React.useId();
  return (
    <storesContext.Provider value={stores}>
      <LandmarksProvider>
        <Box sx={{ height: "100vh", display: "flex", flexDirection: "column" }}>
          <Header sidebarId={sidebarId} />
          <Box sx={{ display: "flex", flex: 1, minHeight: 0, minWidth: 0 }}>
            <PrototypeSidebar id={sidebarId} selected={selected} requestsBadge={requestsBadge} />
            <Main sx={{ overflow: "auto", minWidth: 0 }}>{children}</Main>
          </Box>
        </Box>
      </LandmarksProvider>
    </storesContext.Provider>
  );
}
