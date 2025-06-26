// @flow
import CssBaseline from "@mui/material/CssBaseline";
import materialTheme from "@/theme";
import TitledBox from "@/Inventory/components/TitledBox";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import React, {Node, useEffect, useState} from "react";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import {ThemeProvider} from "@mui/material/styles";
import {withStyles} from "Styles";
import Badge from "@mui/material/Badge";
import Fab from "@mui/material/Fab";
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";
import {createSvgIcon, Icon} from "@mui/material";
import SvgIcon from "@mui/material/SvgIcon";
import {makeStyles} from "tss-react/mui";
import axios from "@/common/axios";
import {DataGrid} from "@mui/x-data-grid";
import {DataGridColumn} from "@/util/table";
import Link from "@mui/material/Link";
import MaterialsDialog
  from "@/eln-inventory-integration/MaterialsListing/MaterialsDialog";
import ExternalWorkflowDialog
  from "@/eln/eln-external-workflows/ExternalWorkflowDialog";

function ExternalWorkflowInvocations({
  isForNotebookPage = false,
  fieldId
}) {
  const [count,setCount] = useState(0);
  const [isVisible, setIsVisible] = useState(false);
  const [galaxyDataSummary, setGalaxyDataSummary] = useState([]);
  const [showDialog, setShowDialog] = useState(false);
  const BUTTON_TOP = isForNotebookPage ? 115: 100;
  const BUTTON_RIGHT = isForNotebookPage ? 15: -24;
  const BUTTON_BOTTOM = BUTTON_TOP-48;
  const { classes } = useStyles({ BUTTON_TOP, BUTTON_RIGHT,BUTTON_BOTTOM });
  const CustomBadge = withStyles< {| children: Node, count: number  |},{ root: string, badge: string }>(() => ({
    root: {
      position: "sticky",
      top: BUTTON_TOP,
      zIndex: 1, // so it appears above the TinyMCE Editor
      pointerEvents: "auto",
    },
    badge: {
      transform: "none",
    },
  }))(({classes, children, count}) => (
      <Badge badgeContent={count} color="primary" classes={classes}>
        {children}
      </Badge>
  ));

  useEffect( () => {
    async function fetchData() {
      await updateGalaxyDataSummary();
    }
    fetchData();
  }, []);

  const updateGalaxyDataSummary = async () => {
    const data = await getGalaxyData();
    if (data === null || data === "") {
      setGalaxyDataSummary([])
    } else {
      setGalaxyDataSummary(data);
      setCount(data.filter(d => d.galaxyInvocationName !== null).length);
    }
  }

  const getGalaxyData = async () => {
    return (await axios.get(
        "/apps/galaxy/getSummaryGalaxyDataForRSpaceField/" + fieldId)).data;
  }

  window.addEventListener("galaxy-used", function (e) {
    const eFieldId = e.detail.fieldId.substring(4);
    if (eFieldId === fieldId) {
      setIsVisible(true);
    }
  });

  return (

      <>  {(isVisible || galaxyDataSummary.length > 0) && (
          <>
            <div className={classes.launcherWrapper}>
              <CustomBadge count={count}>
                <Fab
                    onClick={async () => {
                      await updateGalaxyDataSummary();
                      setShowDialog(true);
                    }}
                    color="primary"
                    size="medium"
                    aria-label="Show computational workflows associated with this field"
                    aria-haspopup="menu"
                    className={classes.fab}
                >
                  <WorkFlowIcon width="100%" viewBox="0 0 225 225"
                                enableBackground="new 0 0 225 225"></WorkFlowIcon>
                </Fab>
              </CustomBadge>
            </div>
            <ExternalWorkflowDialog open={showDialog} setOpen={setShowDialog}
                                    galaxySummaryReport={galaxyDataSummary}/>
          </>
      )}
      </>
  );
}

const WorkFlowIcon = createSvgIcon(
    <path fill="#075869" opacity="1.000000" stroke="none"
          d="
M101.000000,1.000000
	C108.687576,1.000000 116.375153,1.000000 124.717369,1.359452
	C171.095520,6.333593 210.047485,39.471882 221.971649,83.940582
	C223.400848,89.270546 224.661316,94.645760 226.000000,100.000000
	C226.000000,109.020897 226.000000,118.041786 225.635391,127.668221
	C224.911423,129.722565 224.493378,131.159653 224.201218,132.621872
	C215.490448,176.217270 190.137009,205.475113 148.273178,220.252319
	C141.069992,222.794922 133.435349,224.115143 126.000000,226.000000
	C117.645767,226.000000 109.291534,226.000000 100.304413,225.642319
	C97.885559,224.914520 96.101891,224.532837 94.313278,224.175980
	C50.944901,215.523209 21.787474,190.316452 6.909880,148.742477
	C4.387102,141.692825 2.942648,134.257263 1.000000,127.000000
	C1.000000,117.979103 1.000000,108.958214 1.362861,99.333939
	C2.086218,97.282440 2.500093,95.845261 2.799470,94.384613
	C12.276712,48.145241 39.697891,18.336983 84.883896,4.880669
	C90.172165,3.305830 95.623993,2.280225 101.000000,1.000000
M93.186539,103.459419
	C89.927948,101.314034 86.936104,98.211632 83.357544,97.214233
	C74.816711,94.833755 66.508835,99.916702 63.665485,108.302597
	C60.966076,116.263977 64.812088,125.200829 72.500755,128.832748
	C80.214142,132.476364 89.044601,129.780273 94.007874,122.500916
	C94.974152,121.083733 96.677048,119.381516 98.165123,119.252533
	C102.948471,118.837967 107.790314,119.098267 112.924225,119.098267
	C110.712349,131.958344 104.254967,141.383545 93.100937,147.793503
	C92.423775,147.221893 91.932625,146.808228 91.442459,146.393417
	C84.856918,140.820221 76.805542,140.085281 70.302185,144.463943
	C64.262794,148.530212 61.411297,156.502426 63.538120,163.374893
	C65.793526,170.662888 72.492775,175.541092 80.069183,175.412323
	C88.010895,175.277344 94.008904,170.172043 96.319839,161.747757
	C96.686813,160.410019 97.270340,158.646194 98.309883,158.051453
	C113.329025,149.458862 121.819664,136.460037 124.568657,119.347855
	C133.985031,119.347855 143.223007,119.347855 152.906616,119.347855
	C152.906616,122.530731 152.757721,125.498093 152.980743,128.437225
	C153.068512,129.593826 153.733719,131.274261 154.597534,131.647278
	C155.490616,132.032928 157.290100,131.484329 158.095779,130.717148
	C163.040894,126.008347 167.976547,121.264229 172.514297,116.174316
	C173.425049,115.152771 173.384094,111.900917 172.455612,110.856628
	C168.035858,105.885567 163.223145,101.252968 158.386124,96.673317
	C157.459396,95.795883 155.456741,94.998535 154.564377,95.431007
	C153.626358,95.885605 153.087814,97.832565 152.973221,99.178352
	C152.738037,101.940659 152.902603,104.737000 152.902603,107.642136
	C143.128067,107.642136 133.879822,107.642136 124.501976,107.642136
	C121.936615,91.467186 114.094109,78.971382 100.396919,70.140747
	C98.473686,68.900826 96.798088,66.447418 96.139771,64.221024
	C93.535011,55.411915 85.097076,50.105724 76.041603,52.048367
	C67.324684,53.918377 61.623493,62.570835 63.110912,71.672737
	C64.463020,79.946648 72.733810,86.451485 81.294800,85.058006
	C85.496162,84.374146 89.406586,81.902916 94.315346,79.884171
	C104.132576,85.755318 110.803764,95.018051 112.923454,107.901062
	C108.393364,107.901062 104.210625,107.615295 100.087914,107.990013
	C96.689362,108.298897 94.737755,107.099915 93.186539,103.459419
z"/>
    ,
    'WorkFlow');

const useStyles = makeStyles()(
    (theme, {BUTTON_TOP, BUTTON_RIGHT, BUTTON_BOTTOM}) => ({
      launcherWrapper: {
        position: "absolute",
        top: BUTTON_TOP,
        right: BUTTON_RIGHT,
        bottom: BUTTON_BOTTOM,
        pointerEvents: "none",
        "@media print": {
          display: "none",
        },
      },
      growTransform: {transformOrigin: "center right"},
      primary: {color: theme.palette.primary.main},
      popper: {
        zIndex: 1, // so it appears above the TinyMCE Editor
        pointerEvents: "auto",
      },
      itemName: {fontWeight: "bold"},
      itemText: {
        whiteSpace: "nowrap",
        overflow: "hidden",
        textOverflow: "ellipsis",
        width: "240px",
      },
      fab: {
        zIndex: "initial",
      },
    }));
export default ExternalWorkflowInvocations;
