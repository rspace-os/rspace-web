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
import { useDeploymentProperty } from "../../eln/useDeploymentProperty";
import * as FetchingData from "../../util/fetchingData";

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
let SELECTED_ANIMALS = [];
let PYRAT_URL = null;

function Pyrat() {
  const pyrat = axios.create({
    baseURL: "/apps/pyrat",
    timeout: 15000,
  });

  const pyratUrl = useDeploymentProperty("pyrat.url");
  useEffect(() => {
    FetchingData.getSuccessValue(pyratUrl).do((p) => {
      PYRAT_URL = new URL(p);
    });
  }, [pyratUrl]);

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
      query:
        "licenses?k=license_id&k=license_number&s=license_id:asc&license_number=",
      enumObj: {},
      renderFunc: ({ license_id, license_number }) => [
        license_id,
        { label: license_number, value: license_id },
      ],
    },
    responsible_id: {
      label: "Responsible",
      value: "",
      query: "users?k=userid&k=fullname&s=username:asc&fullname=",
      enumObj: {},
      renderFunc: ({ userid, fullname }) => [
        userid,
        { label: fullname, value: userid },
      ],
    },
    project_id: {
      label: "Project",
      value: "",
      query:
        "projects?k=id&k=name&s=id:asc&status=active&status=inactive&name=",
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

  useEffect(function assertPyratVersion() {
    pyrat
      .get("version")
      .then((response) => {
        if (response.data.api_version !== SUPPORTED_PYRAT_API_VERSION) {
          setErrorReason(ErrorReason.APIVersion);
        }
      })
      .catch((error) => {
        handlePyratError(error);
      });
  }, []);

  useEffect(function makeBuildingEnum() {
    pyrat
      .get(
        "locations?s=full_name:asc&k=building_id&k=full_name&type=building&=status=available"
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
              enumObj: enumObj,
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
          let animals = response.data;

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

    return `l=${rowsPerPage}&o=${page * rowsPerPage}&${params.join(
      ""
    )}&s=${orderBy}:${order}`;
  }, [filterCounter, order, orderBy, rowsPerPage, page]);

  VISIBLE_HEADER_CELLS = useMemo(
    () =>
      TABLE_HEADER_CELLS.filter((cell) => visibleColumnIds.includes(cell.id)),
    [visibleColumnIds]
  );

  SELECTED_ANIMALS = useMemo(() => {
    const selected_animals = animals.filter((animal) =>
      selectedAnimalIds.includes(animal.eartag_or_id)
    );

    window.parent.postMessage(
      {
        mceAction: selected_animals.length > 0 ? "enable" : "disable",
      },
      "*"
    );

    return selected_animals;
  }, [selectedAnimalIds]);

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
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <Grid container spacing={1}>
          <Grid
            container
            item
            xs={12}
            justifyContent="flex-start"
            alignItems="center"
          >
            <FilterButton
              showFilter={showFilter}
              setShowFilter={setShowFilter}
            />
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
      </ThemeProvider>
    </StyledEngineProvider>
  );
}

document.addEventListener("DOMContentLoaded", function () {
  const domContainer = document.getElementById("tinymce-pyrat");
  const root = createRoot(domContainer);
  root.render(<Pyrat />);
});

parent.tinymce.activeEditor.on("pyrat-insert", function () {
  if (parent && parent.tinymce) {
    const ed = parent.tinymce.activeEditor;

    if (SELECTED_ANIMALS.length > 0) {
      const pyratTable = createTinyMceTable();
      ed.execCommand("mceInsertContent", false, pyratTable.outerHTML);
    }
    ed.windowManager.close();
  }
});

function createTinyMceTable() {
  const pyratTable = document.createElement("table");
  pyratTable.setAttribute("data-tableSource", "pyrat");

  const linkRow = document.createElement("tr");
  const linkCell = document.createElement("th");
  linkCell.appendChild(document.createTextNode("Imported from "));
  const anchor = document.createElement("a");
  anchor.href = PYRAT_URL?.origin ?? "";
  anchor.appendChild(document.createTextNode(PYRAT_URL?.origin ?? ""));
  anchor.setAttribute("rel", "noreferrer");
  linkCell.appendChild(anchor);
  linkCell.appendChild(document.createTextNode(" on "));
  linkCell.appendChild(document.createTextNode(new Date().toDateString()));
  linkCell.setAttribute("colspan", VISIBLE_HEADER_CELLS.length);
  linkRow.appendChild(linkCell);
  pyratTable.appendChild(linkRow);

  const tableHeader = document.createElement("tr");
  VISIBLE_HEADER_CELLS.forEach((cell) => {
    const columnName = document.createElement("th");
    columnName.textContent = cell.label;
    tableHeader.appendChild(columnName);
  });
  pyratTable.appendChild(tableHeader);

  SELECTED_ANIMALS.forEach((animal) => {
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
