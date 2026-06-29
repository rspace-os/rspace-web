import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import Grid from "@mui/material/Grid";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import { useTranslation } from "react-i18next";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR } from "@/assets/branding/pyrat";
import docLinks from "@/assets/DocLinks";
import axios from "@/common/axios";
import AppBar from "@/components/AppBar";
import useLocalStorage from "@/hooks/browser/useLocalStorage";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import { getHeader } from "@/util/axios";
import * as FetchingData from "@/util/fetchingData";
import * as Parsers from "@/util/parsers";
import { parseInteger } from "@/util/parsers";
import Result from "@/util/result";
import ColumnVisibilitySettings from "./ColumnVisibilitySettings";
import ColumnVisibilitySettingsButton from "./ColumnVisibilitySettingsButton";
import { AnimalState, AnimalType, ErrorReason, Order, Sex } from "./Enums";
import ErrorView from "./ErrorView";
import Filter from "./Filter";
import FilterButton from "./FilterButton";
import ResultsTable from "./ResultsTable";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const tinymce: any;

function useAuthenticatedServers() {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [servers, setServers] = React.useState<any[]>([]);
  const [loading, setLoading] = React.useState(true);
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [error, setError] = React.useState<any>(null);

  useEffect(() => {
    axios
      .get("/integration/integrationInfo", {
        params: new URLSearchParams({ name: "PYRAT" }),
        responseType: "json",
      })
      .then(({ data }) => {
        setServers(
          Parsers.objectPath(["data", "options"], data)
            .flatMap(Parsers.isObject)
            .flatMap(Parsers.isNotNull)
            .map((servers) => Object.entries(servers).filter(([k]) => k !== "PYRAT_CONFIGURED_SERVERS"))
            .flatMap((servers) =>
              Result.all(
                ...servers.map(([, config]) => {
                  try {
                    const server = Parsers.isObject(config).flatMap(Parsers.isNotNull).elseThrow();
                    const alias = Parsers.getValueWithKey("PYRAT_ALIAS")(server).flatMap(Parsers.isString).elseThrow();
                    const url = Parsers.getValueWithKey("PYRAT_URL")(server).flatMap(Parsers.isString).elseThrow();
                    return Result.Ok({ alias, url });
                  } catch {
                    return Result.Error([new Error("Could not parse out pyrat authenticated server")]);
                  }
                }),
              ),
            )
            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
            .elseThrow() as any[],
        );
      })
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      .catch((error: any) => {
        setError(error);
        console.error("Failed to fetch servers", error);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return { tag: "loading" } as const;
  if (error) return { tag: "error", error } as const;
  return { tag: "success", value: servers } as const;
}

const SUPPORTED_PYRAT_API_VERSION = 3;

// Some of these are numeric, but the enhanced table alignment for numerics looks bad
const TABLE_HEADER_CELLS = [
  { id: "eartag_or_id", numeric: false, label: "ID" },
  { id: "sex", numeric: false, label: "Sex" },
  { id: "age_days", numeric: false, label: "Age (Days)" },
  { id: "strain_name", numeric: false, label: "Strain" },
  { id: "mutations", numeric: false, label: "Mutations", sortable: false },
  { id: "dateborn", numeric: false, label: "DOB" },
  { id: "datesacrificed", numeric: false, label: "Sacrificed On" },
  { id: "classification", numeric: false, label: "Classification" },
  { id: "licence_number", numeric: false, label: "License" },
  { id: "labid", numeric: false, label: "Lab ID" },
  { id: "building_name", numeric: false, label: "Building" },
  { id: "projects", numeric: false, label: "Project" },
  { id: "responsible_fullname", numeric: false, label: "Responsible" },
];

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
let VISIBLE_HEADER_CELLS: any[] = [];
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
let PYRAT_URL: any = null;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
let PYRAT_ALIAS: any = null;

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function PyratListing({ serverAlias, setSelectedAnimals }: { serverAlias: any; setSelectedAnimals: any }) {
  const pyrat = axios.create({
    baseURL: "/apps/pyrat",
    timeout: 15000,
  });

  // Counter is increased when filtering is required.
  // Counter instead of boolean, as useEffect functions below that depend on
  // this hook should only execute once (boolean switch is two changes)
  const [filterCounter, setFilterCounter] = useState(0);
  const [filter, setFilter] = useState({
    age_days_from: {
      label: "Age days from",
      type: "number",
      value: "",
    },
    age_days_to: {
      label: "Age days to",
      type: "number",
      value: "",
    },
    eartag: {
      label: "ID",
      type: "string",
      value: "",
    },
    labid: {
      label: "Lab ID",
      type: "string",
      value: "",
    },
  });
  // Multi request filter fields
  const [filterMultiReq, setFilterMultiReq] = useState({
    licence_id: {
      label: "License",
      value: "",
      query: `licenses?serverAlias=${serverAlias}&k=license_id&k=license_number&s=license_id:asc&license_number=`,
      enumObj: {},
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      renderFunc: ({ license_id, license_number }: { license_id: any; license_number: any }) => [
        license_id,
        { label: license_number, value: license_id },
      ],
    },
    responsible_id: {
      label: "Responsible",
      value: "",
      query: `users?serverAlias=${serverAlias}&k=userid&k=fullname&s=username:asc&fullname=`,
      enumObj: {},
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      renderFunc: ({ userid, fullname }: { userid: any; fullname: any }) => [
        userid,
        { label: fullname, value: userid },
      ],
    },
    project_id: {
      label: "Project",
      value: "",
      query: `projects?serverAlias=${serverAlias}&k=id&k=name&s=id:asc&status=active&status=inactive&name=`,
      enumObj: {},
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      renderFunc: ({ id, name }: { id: any; name: any }) => [id, { label: name, value: id }],
    },
  });
  // Other special filter fields
  const [filterSpecial, setFilterSpecial] = useState({
    animal_type: {
      label: "Animal Type",
      defaultValue: AnimalType.Animal,
      value: AnimalType.Animal,
      enumObj: AnimalType,
    },
    animal_state: {
      label: "Animal State",
      defaultValue: [AnimalState.Live],
      value: [AnimalState.Live],
      enumObj: AnimalState,
    },
    sex: {
      label: "Sex",
      defaultValue: Sex.None,
      value: Sex.None,
      enumObj: Sex,
    },
    building_id: {
      label: "Building",
      defaultValue: "",
      value: "",
      enumObj: {},
    },
    birth_date_from: {
      label: "Birth date from",
      defaultValue: null,
      value: null,
    },
    birth_date_to: {
      label: "Birth date to",
      defaultValue: null,
      value: null,
    },
    sacrifice_date_from: {
      label: "Sacrificed from",
      defaultValue: null,
      value: null,
    },
    sacrifice_date_to: {
      label: "Sacrificed to",
      defaultValue: null,
      value: null,
    },
  });

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [animals, setAnimals] = useState<any[]>([]);
  const [fetchDone, setFetchDone] = useState(false);
  const [errorReason, setErrorReason] = useState(ErrorReason.None);

  const [showSettings, setShowSettings] = useLocalStorage("pyratShowSettings", false);
  const [showFilter, setShowFilter] = useLocalStorage("pyratShowFilter", false);
  const [visibleColumnIds, setVisibleColumnIds] = useLocalStorage(
    "pyratVisibleColumns",
    TABLE_HEADER_CELLS.map((cell) => cell.id),
  );

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [selectedAnimalIds, setSelectedAnimalIds] = useState<any[]>([]);
  const [order, setOrder] = useLocalStorage("pyratSearchOrder", Order.desc);
  const [orderBy, setOrderBy] = useLocalStorage("pyratSearchOrderBy", "eartag_or_id");

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useLocalStorage("pyratRowsPerPage", 10);
  const [count, setCount] = useState(0);

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const handleChangePage = (newPage: any) => {
    setPage(newPage);
    fetchAnimals();
  };

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const handleRowsPerPageChange = (pageSize: any) => {
    setRowsPerPage(pageSize);
    handleChangePage(0);
  };

  useEffect(() => {
    pyrat
      .get(`version?serverAlias=${serverAlias}`)
      .then((response) => {
        if (response.data.api_version !== SUPPORTED_PYRAT_API_VERSION) {
          setErrorReason(ErrorReason.APIVersion);
        }
      })
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      .catch((error: any) => {
        handlePyratError(error);
      });
  }, []);

  useEffect(() => {
    pyrat
      .get(
        `locations?serverAlias=${serverAlias}&s=full_name:asc&k=building_id&k=full_name&type=building&=status=available`,
      )
      .then((response) => {
        if (response.data) {
          const enumObj = response.data.reduce(
            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
            (acc: any, building: any) => {
              acc[building.full_name] = building.building_id;
              return acc;
            },
            { None: "" },
          );

          setFilterSpecial({
            ...filterSpecial,
            building_id: {
              ...filterSpecial.building_id,
              enumObj,
            },
          });
        }
      })
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      .catch((error: any) => {
        handlePyratError(error);
      });
  }, []);

  function fetchAnimals() {
    setFetchDone(false);
    setAnimals([]);

    const collection = AnimalType.Animal === filterSpecial.animal_type.value ? "animals" : "pups";

    pyrat
      .get(`${collection}?${makeQueryString}`)
      .then((response) => {
        if (response.data) {
          const animals = response.data;

          // Not done at render time as "animals" is reused for inserting TinyMCE table
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          animals.forEach((animal: any) => {
            // projects contain a lot of metadata that should not be displayed
            if (animal.projects) {
              // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
              animal.projects = animal.projects.map((project: any) => project.project_label).join(", ");
            }

            if (animal.mutations) {
              animal.mutations = animal.mutations
                // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
                .map((mutation: any) => `${mutation.mutationname} ${mutation.mutationgrade}`)
                .join(", ");
            }
          });

          setAnimals(animals);

          setCount(
            // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
            getHeader(response as any, "x-total-count")
              .flatMap(parseInteger)
              .orElseGet(([error]) => {
                throw new Error("Pagination header missing", { cause: error });
              }),
          );
        }
      })
      // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
      .catch((error: any) => {
        handlePyratError(error);
      })
      .finally(() => {
        setFetchDone(true);
      });
  }

  useEffect(() => {
    setPage(0);
    fetchAnimals();
  }, [filterCounter, order, orderBy]);

  const makeQueryString = useMemo(() => {
    const params = [filterSpecial.animal_state.value.map((state) => `&state=${state}`).join("")];

    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    for (const [key, { value }] of Object.entries(filterMultiReq) as [string, any][]) {
      if (value?.value) {
        params.push(`&${key}=${value.value}`);
      }
    }

    TABLE_HEADER_CELLS.forEach((config) => {
      params.push(`&k=${config.id}`);
    });
    Object.entries(filter).forEach(([key, config]) => {
      if (config.value) {
        params.push(`&${key}=${config.value}`);
      }
    });
    Object.entries(filterSpecial).forEach(([key, config]) => {
      if (key !== "animal_type" && config.value) {
        params.push(`&${key}=${config.value}`);
      }
    });

    return `serverAlias=${serverAlias}&l=${rowsPerPage}&o=${
      page * rowsPerPage
    }&${params.join("")}&s=${orderBy}:${order}`;
  }, [filterCounter, order, orderBy, rowsPerPage, page]);

  VISIBLE_HEADER_CELLS = useMemo(
    () => TABLE_HEADER_CELLS.filter((cell) => visibleColumnIds.includes(cell.id)),
    [visibleColumnIds],
  );

  React.useEffect(() => {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    setSelectedAnimals(animals.filter((animal: any) => selectedAnimalIds.includes(animal.eartag_or_id)));
  }, [animals, selectedAnimalIds, setSelectedAnimals]);

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function handlePyratError(error: any) {
    if (error.message === "Network Error") {
      setErrorReason(ErrorReason.NetworkError);
    } else if (error.message.startsWith("timeout")) {
      setErrorReason(ErrorReason.Timeout);
    } else if (error.response) {
      if (error.response.status === 401) {
        setErrorReason(ErrorReason.Unauthorized);
      } else if (error.response.status === 400) {
        setErrorReason(ErrorReason.BadRequest);
      } else {
        setErrorReason(ErrorReason.Unknown);
      }
    } else {
      setErrorReason(ErrorReason.Unknown);
    }
  }

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  function handleOptionsFilterChange(filterKey: any, input: any) {
    if (new Set(Object.keys(filterMultiReq)).has(filterKey)) {
      (async () => {
        try {
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          const multiReq = filterMultiReq as Record<string, any>;
          const response = await pyrat.get(`${multiReq[filterKey].query}${input}`);

          if (response.data) {
            const enumObj = Object.fromEntries(response.data.map(multiReq[filterKey].renderFunc));
            setFilterMultiReq({
              ...filterMultiReq,
              [filterKey]: { ...multiReq[filterKey], enumObj },
            });
          }
        } catch (error) {
          handlePyratError(error);
        }
      })();
    }
  }

  if (errorReason !== ErrorReason.None) {
    return <ErrorView errorReason={errorReason} />;
  }
  return (
    <Grid container spacing={1}>
      <Grid container sx={{ justifyContent: "flex-start", alignItems: "center" }} size={12}>
        <FilterButton showFilter={showFilter} setShowFilter={setShowFilter} />
        <ColumnVisibilitySettingsButton showSettings={showSettings} setShowSettings={setShowSettings} />
      </Grid>
      {showFilter && (
        <Grid size={12}>
          <Filter
            filter={filter}
            setFilter={setFilter}
            filterMultiReq={filterMultiReq}
            setFilterMultiReq={setFilterMultiReq}
            filterSpecial={filterSpecial}
            setFilterSpecial={setFilterSpecial}
            filterCounter={filterCounter}
            setFilterCounter={setFilterCounter}
            onOptionsFilterChange={handleOptionsFilterChange}
          />
        </Grid>
      )}
      {showSettings && (
        <Grid size={12}>
          <ColumnVisibilitySettings
            visibleColumnIds={visibleColumnIds}
            setVisibleColumnIds={setVisibleColumnIds}
            allTableHeaderCells={TABLE_HEADER_CELLS}
          />
        </Grid>
      )}
      <Grid size={12}>
        <ResultsTable
          page={page}
          onPageChange={handleChangePage}
          visibleHeaderCells={VISIBLE_HEADER_CELLS}
          animals={animals}
          selectedAnimalIds={selectedAnimalIds}
          setSelectedAnimalIds={setSelectedAnimalIds}
          order={order}
          orderBy={orderBy}
          setOrder={setOrder}
          setOrderBy={setOrderBy}
          onRowsPerPageChange={handleRowsPerPageChange}
          rowsPerPage={rowsPerPage}
          count={count}
        />
      </Grid>
      <Grid sx={{ textAlign: "center" }} size={12}>
        {!fetchDone && <CircularProgress />}
      </Grid>
    </Grid>
  );
}

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function PyratDialog({ editor, open, onClose }: { editor: any; open: any; onClose: any }) {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [serverAlias, setServerAlias] = React.useState<any>(null);
  const servers = useAuthenticatedServers();
  const { t } = useTranslation(["apps", "common"]);
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  const [selectedAnimals, setSelectedAnimals] = React.useState<any[]>([]);

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  FetchingData.getSuccessValue(servers as any).do((servers: any) => {
    if (servers.length === 1) {
      PYRAT_URL = servers[0].url;
      PYRAT_ALIAS = servers[0].alias;
    }
  });

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
      <AppBar
        variant="dialog"
        currentPage="PyRAT"
        helpPage={{
          docLink: docLinks.pyrat,
          title: "PyRAT help",
        }}
        accessibilityTips={{}}
      />
      <DialogTitle>Insert from PyRAT</DialogTitle>
      <DialogContent>
        {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
        {FetchingData.match(servers as any, {
          loading: () => <CircularProgress />,
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          error: (error: any) => <Typography color="error">{error.message}</Typography>,
          // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
          success: (servers: any) => {
            if (servers.length === 1)
              return <PyratListing serverAlias={servers[0].alias} setSelectedAnimals={setSelectedAnimals} />;
            if (serverAlias) return <PyratListing serverAlias={serverAlias} setSelectedAnimals={setSelectedAnimals} />;
            return (
              <>
                <Typography variant="body1" gutterBottom>
                  Pick one of your authenticated servers
                </Typography>
                <List>
                  <Divider />
                  {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
                  {servers.map((server: any) => (
                    <>
                      <ListItem disablePadding key={server.alias}>
                        <ListItemButton
                          onClick={() => {
                            setServerAlias(server.alias);
                            PYRAT_URL = server.url;
                            PYRAT_ALIAS = server.alias;
                          }}
                        >
                          <ListItemText primary={server.alias} secondary={server.url} />
                        </ListItemButton>
                      </ListItem>
                      <Divider />
                    </>
                  ))}
                </List>
              </>
            );
          },
        })}
      </DialogContent>
      <DialogActions>
        <Button onClick={() => onClose()}>{t("common:actions.cancel")}</Button>
        <Button
          disabled={selectedAnimals.length === 0}
          color="callToAction"
          variant="contained"
          onClick={() => {
            editor.execCommand("mceInsertContent", false, createTinyMceTable(selectedAnimals).outerHTML);
            onClose();
          }}
        >
          {t("pyrat.insertButton")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

class PyratPlugin {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  constructor(editor: any) {
    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    function* renderPyrat(domContainer: any): Generator<void, void, any> {
      const root = createRoot(domContainer);
      while (true) {
        const newProps = yield;
        root.render(
          <I18nRoot namespaces={["apps", "common"]}>
            <StyledEngineProvider injectFirst enableCssLayer>
              <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
                <PyratDialog editor={editor} open={false} onClose={() => {}} {...newProps} />
              </ThemeProvider>
            </StyledEngineProvider>
          </I18nRoot>,
        );
      }
    }

    if (!document.getElementById("tinymce-pyrat")) {
      const div = document.createElement("div");
      div.id = "tinymce-pyrat";
      document.body.appendChild(div);
    }
    const pyratRenderer = renderPyrat(document.getElementById("tinymce-pyrat"));
    pyratRenderer.next({ open: false });

    // Add a button to the toolbar
    editor.ui.registry.addButton("pyrat", {
      tooltip: "Link to PyRAT",
      icon: "pyrat",
      onAction() {
        pyratRenderer.next({
          open: true,
          onClose: () => {
            pyratRenderer.next({ open: false });
          },
        });
      },
    });

    // Adds a menu item to the insert menu
    editor.ui.registry.addMenuItem("optPyrat", {
      text: "From PyRAT",
      icon: "pyrat",
      onAction() {
        pyratRenderer.next({
          open: true,
          onClose: () => {
            pyratRenderer.next({ open: false });
          },
        });
      },
    });

    // Adds an option to the slash-menu
    if (!window.insertActions) window.insertActions = new Map();
    window.insertActions.set("optPyrat", {
      text: "From PyRAT",
      icon: "pyrat",
      action: () => {
        pyratRenderer.next({
          open: true,
          onClose: () => {
            pyratRenderer.next({ open: false });
          },
        });
      },
    });
  }
}
tinymce.PluginManager.add("pyrat", PyratPlugin);

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
function createTinyMceTable(selectedAnimals: any[]) {
  const pyratTable = document.createElement("table");
  pyratTable.setAttribute("data-tableSource", "pyrat");
  pyratTable.style = "font-size: 0.7em";

  if (!PYRAT_URL) throw new Error("PYRAT_URL is not known");
  if (!PYRAT_ALIAS) throw new Error("PYRAT_ALIAS is not known");

  const link = PYRAT_URL.slice(0, PYRAT_URL.lastIndexOf("/api/"));

  const linkRow = document.createElement("tr");
  const linkCell = document.createElement("th");
  linkCell.appendChild(document.createTextNode("Imported from "));
  const anchor = document.createElement("a");
  anchor.href = link;
  anchor.appendChild(document.createTextNode(`${PYRAT_ALIAS} (${link})`));
  anchor.setAttribute("rel", "noreferrer");
  linkCell.appendChild(anchor);
  linkCell.appendChild(document.createTextNode(" on "));
  linkCell.appendChild(document.createTextNode(new Date().toDateString()));
  linkCell.appendChild(document.createTextNode(" "));
  linkCell.appendChild(document.createTextNode(new Date().toLocaleTimeString()));
  linkCell.setAttribute("colspan", String(VISIBLE_HEADER_CELLS.length));
  linkCell.style = "font-weight: 400";
  linkRow.appendChild(linkCell);
  pyratTable.appendChild(linkRow);

  const tableHeader = document.createElement("tr");
  VISIBLE_HEADER_CELLS.forEach((cell) => {
    const columnName = document.createElement("th");
    columnName.textContent = cell.label;
    tableHeader.appendChild(columnName);
  });
  pyratTable.appendChild(tableHeader);

  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  selectedAnimals.forEach((animal: any) => {
    const row = document.createElement("tr");

    // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
    VISIBLE_HEADER_CELLS.forEach((headerCell: any) => {
      const cell = document.createElement("td");

      const textContent = animal[headerCell.id];
      if (textContent) cell.textContent = textContent;

      row.appendChild(cell);
    });

    pyratTable.appendChild(row);
  });

  return pyratTable;
}
