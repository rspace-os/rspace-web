import React, { useState, useEffect, useMemo } from "react";
import axios from "axios";
import Grid from "@mui/material/Grid";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import CircularProgress from "@mui/material/CircularProgress";
import materialTheme from "../../theme";
import { AnimalType, AnimalState, ErrorReason, Order, Sex } from "./Enums";
import ErrorView from "./ErrorView";
import ResultsTable from "./ResultsTable";
import ColumnVisibilitySettingsButton from "./ColumnVisibilitySettingsButton";
import ColumnVisibilitySettings from "./ColumnVisibilitySettings";
import useLocalStorage from "../../util/useLocalStorage";
import FilterButton from "./FilterButton";
import Filter from "./Filter";
import { createRoot } from "react-dom/client";
import { getHeader } from "../../util/axios";
import { parseInteger } from "../../util/parsers";
import * as FetchingData from "../../util/fetchingData";
import * as Parsers from "../../util/parsers";
import Result from "../../util/result";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemButton from "@mui/material/ListItemButton";
import ListItemText from "@mui/material/ListItemText";
import Typography from "@mui/material/Typography";
import Divider from "@mui/material/Divider";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import createAccentedTheme from "../../accentedTheme";
import AppBar from "../../components/AppBar";
import docLinks from "../../assets/DocLinks";

const COLOR = {
  main: {
    hue: 196,
    saturation: 46,
    lightness: 70,
  },
  darker: {
    hue: 196,
    saturation: 93,
    lightness: 33,
  },
  contrastText: {
    hue: 196,
    saturation: 35,
    lightness: 26,
  },
  background: {
    hue: 196,
    saturation: 25,
    lightness: 71,
  },
  backgroundContrastText: {
    hue: 196,
    saturation: 11,
    lightness: 24,
  },
};

function useAuthenticatedServers() {
  const [servers, setServers] = React.useState([]);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState(null);

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
            .map((servers) =>
              Object.entries(servers).filter(
                ([k]) => k !== "PYRAT_CONFIGURED_SERVERS"
              )
            )
            .flatMap((servers) =>
              Result.all(
                ...servers.map(([key, config]) => {
                  try {
                    const server = Parsers.isObject(config)
                      .flatMap(Parsers.isNotNull)
                      .elseThrow();
                    const alias = Parsers.getValueWithKey("PYRAT_ALIAS")(server)
                      .flatMap(Parsers.isString)
                      .elseThrow();
                    const url = Parsers.getValueWithKey("PYRAT_URL")(server)
                      .flatMap(Parsers.isString)
                      .elseThrow();
                    return Result.Ok({ alias, url });
                  } catch {
                    return Result.Error([
                      new Error(
                        "Could not parse out pyrat authenticated server"
                      ),
                    ]);
                  }
                })
              )
            )
            .elseThrow()
        );
      })
      .catch((error) => {
        setError(error);
        console.error("Failed to fetch servers", error);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return { tag: "loading" };
  if (error) return { tag: "error", error };
  return { tag: "success", value: servers };
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

let VISIBLE_HEADER_CELLS = [];
let PYRAT_URL = null;
let PYRAT_ALIAS = null;

function PyratListing({ serverAlias, setSelectedAnimals }) {
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
      renderFunc: ({ license_id, license_number }) => [
        license_id,
        { label: license_number, value: license_id },
      ],
    },
    responsible_id: {
      label: "Responsible",
      value: "",
      query: `users?serverAlias=${serverAlias}&k=userid&k=fullname&s=username:asc&fullname=`,
      enumObj: {},
      renderFunc: ({ userid, fullname }) => [
        userid,
        { label: fullname, value: userid },
      ],
    },
    project_id: {
      label: "Project",
      value: "",
      query: `projects?serverAlias=${serverAlias}&k=id&k=name&s=id:asc&status=active&status=inactive&name=`,
      enumObj: {},
      renderFunc: ({ id, name }) => [id, { label: name, value: id }],
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

  const [animals, setAnimals] = useState([]);
  const [fetchDone, setFetchDone] = useState(false);
  const [errorReason, setErrorReason] = useState(ErrorReason.None);

  const [showSettings, setShowSettings] = useLocalStorage(
    "pyratShowSettings",
    false
  );
  const [showFilter, setShowFilter] = useLocalStorage("pyratShowFilter", false);
  const [visibleColumnIds, setVisibleColumnIds] = useLocalStorage(
    "pyratVisibleColumns",
    TABLE_HEADER_CELLS.map((cell) => cell.id)
  );

  const [selectedAnimalIds, setSelectedAnimalIds] = useState([]);
  const [order, setOrder] = useLocalStorage("pyratSearchOrder", Order.desc);
  const [orderBy, setOrderBy] = useLocalStorage(
    "pyratSearchOrderBy",
    "eartag_or_id"
  );

  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useLocalStorage("pyratRowsPerPage", 10);
  const [count, setCount] = useState(0);

  const handleChangePage = (newPage) => {
    setPage(newPage);
    fetchAnimals();
  };

  const handleRowsPerPageChange = (pageSize) => {
    setRowsPerPage(pageSize);
    handleChangePage(0);
  };

  useEffect(() => {
    pyrat
      .get("version?serverAlias=" + serverAlias)
      .then((response) => {
        if (response.data.api_version !== SUPPORTED_PYRAT_API_VERSION) {
          setErrorReason(ErrorReason.APIVersion);
        }
      })
      .catch((error) => {
        handlePyratError(error);
      });
  }, []);

  useEffect(() => {
    pyrat
      .get(
        `locations?serverAlias=${serverAlias}&s=full_name:asc&k=building_id&k=full_name&type=building&=status=available`
      )
      .then((response) => {
        if (response.data) {
          const enumObj = response.data.reduce(
            (acc, building) => {
              acc[building.full_name] = building.building_id;
              return acc;
            },
            { None: "" }
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
      .catch((error) => {
        handlePyratError(error);
      });
  }, []);

  function fetchAnimals() {
    setFetchDone(false);
    setAnimals([]);

    const collection =
      AnimalType.Animal === filterSpecial.animal_type.value
        ? "animals"
        : "pups";

    pyrat
      .get(`${collection}?` + makeQueryString)
      .then((response) => {
        if (response.data) {
          const animals = response.data;

          // Not done at render time as "animals" is reused for inserting TinyMCE table
          animals.forEach((animal) => {
            // projects contain a lot of metadata that should not be displayed
            if (animal.projects) {
              animal.projects = animal.projects
                .map((project) => project.project_label)
                .join(", ");
            }

            if (animal.mutations) {
              animal.mutations = animal.mutations
                .map(
                  (mutation) =>
                    mutation.mutationname + " " + mutation.mutationgrade
                )
                .join(", ");
            }
          });

          setAnimals(animals);

          setCount(
            getHeader(response, "x-total-count")
              .flatMap(parseInteger)
              .orElseGet(([error]) => {
                throw new Error("Pagination header missing", { cause: error });
              })
          );
        }
      })
      .catch((error) => {
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
    const params = [
      filterSpecial.animal_state.value
        .map((state) => `&state=${state}`)
        .join(""),
    ];

    for (const [key, { value }] of Object.entries(filterMultiReq)) {
      if (value && value.value) {
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
    () =>
      TABLE_HEADER_CELLS.filter((cell) => visibleColumnIds.includes(cell.id)),
    [visibleColumnIds]
  );

  React.useEffect(() => {
    setSelectedAnimals(
      animals.filter((animal) =>
        selectedAnimalIds.includes(animal.eartag_or_id)
      )
    );
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - setSelectedAnimals will not meaningfully change
     */
  }, [animals, selectedAnimalIds]);

  function handlePyratError(error) {
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

  function handleOptionsFilterChange(filterKey, input) {
    if (new Set(Object.keys(filterMultiReq)).has(filterKey)) {
      (async () => {
        try {
          const response = await pyrat.get(
            `${filterMultiReq[filterKey].query}${input}`
          );

          if (response.data) {
            const enumObj = Object.fromEntries(
              response.data.map(filterMultiReq[filterKey].renderFunc)
            );
            setFilterMultiReq({
              ...filterMultiReq,
              [filterKey]: { ...filterMultiReq[filterKey], enumObj },
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
      <Grid
        container
        item
        xs={12}
        justifyContent="flex-start"
        alignItems="center"
      >
        <FilterButton showFilter={showFilter} setShowFilter={setShowFilter} />
        <ColumnVisibilitySettingsButton
          showSettings={showSettings}
          setShowSettings={setShowSettings}
        />
      </Grid>
      {showFilter && (
        <>
          <Grid item xs={12}>
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
        </>
      )}
      {showSettings && (
        <Grid item xs={12}>
          <ColumnVisibilitySettings
            visibleColumnIds={visibleColumnIds}
            setVisibleColumnIds={setVisibleColumnIds}
            allTableHeaderCells={TABLE_HEADER_CELLS}
          />
        </Grid>
      )}
      <Grid item xs={12}>
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
      <Grid item xs={12} align="center">
        {!fetchDone && <CircularProgress />}
      </Grid>
    </Grid>
  );
}

function Pyrat({ editor, open, onClose }) {
  const [serverAlias, setServerAlias] = React.useState(null);
  const servers = useAuthenticatedServers();
  const [selectedAnimals, setSelectedAnimals] = React.useState([]);

  FetchingData.getSuccessValue(servers).do((servers) => {
    if (servers.length === 1) {
      PYRAT_URL = servers[0].url;
      PYRAT_ALIAS = servers[0].alias;
    }
  });

  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
      <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
        <AppBar
          variant="dialog"
          currentPage="PyRAT"
          helpPage={{
            docLink: docLinks.pyrat,
            title: "PyRAT help",
          }}
        />
        <DialogTitle>Insert from PyRAT</DialogTitle>
        <DialogContent>
          {FetchingData.match(servers, {
            loading: () => <CircularProgress />,
            error: (error) => (
              <Typography color="error">{error.message}</Typography>
            ),
            success: (servers) => {
              if (servers.length === 1)
                return (
                  <PyratListing
                    serverAlias={servers[0].alias}
                    setSelectedAnimals={setSelectedAnimals}
                  />
                );
              if (serverAlias)
                return (
                  <PyratListing
                    serverAlias={serverAlias}
                    setSelectedAnimals={setSelectedAnimals}
                  />
                );
              return (
                <>
                  <Typography variant="body1" gutterBottom>
                    Pick one of your authenticated servers
                  </Typography>
                  <List>
                    <Divider />
                    {servers.map((server) => (
                      <>
                        <ListItem disablePadding key={server.alias}>
                          <ListItemButton
                            onClick={() => {
                              setServerAlias(server.alias);
                              PYRAT_URL = server.url;
                              PYRAT_ALIAS = server.alias;
                            }}
                          >
                            <ListItemText
                              primary={server.alias}
                              secondary={server.url}
                            />
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
          <Button onClick={() => onClose()}>Cancel</Button>
          <Button
            disabled={selectedAnimals.length === 0}
            color="callToAction"
            variant="contained"
            onClick={() => {
              editor.execCommand(
                "mceInsertContent",
                false,
                createTinyMceTable(selectedAnimals).outerHTML
              );
              onClose();
            }}
          >
            Insert
          </Button>
        </DialogActions>
      </Dialog>
    </ThemeProvider>
  );
}

class PyratPlugin {
  constructor(editor) {
    function* renderPyrat(domContainer) {
      const root = createRoot(domContainer);
      while (true) {
        const newProps = yield;
        root.render(
          <StyledEngineProvider injectFirst>
            <ThemeProvider theme={materialTheme}>
              <Pyrat
                editor={editor}
                open={false}
                onClose={() => {}}
                {...newProps}
              />
            </ThemeProvider>
          </StyledEngineProvider>
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

function createTinyMceTable(selectedAnimals) {
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
  linkCell.appendChild(
    document.createTextNode(new Date().toLocaleTimeString())
  );
  linkCell.setAttribute("colspan", VISIBLE_HEADER_CELLS.length);
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

  selectedAnimals.forEach((animal) => {
    const row = document.createElement("tr");

    VISIBLE_HEADER_CELLS.forEach((headerCell) => {
      const cell = document.createElement("td");

      const textContent = animal[headerCell.id];
      if (textContent) cell.textContent = textContent;

      row.appendChild(cell);
    });

    pyratTable.appendChild(row);
  });

  return pyratTable;
}
