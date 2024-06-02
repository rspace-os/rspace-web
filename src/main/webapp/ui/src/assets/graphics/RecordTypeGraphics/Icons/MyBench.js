// @flow

import React, { type ComponentType } from "react";
import { withStyles } from "Styles";

type MyBenchIconArgs = {|
  size?: "small",
  color?: string,
|};

const MyBenchIcon: ComponentType<MyBenchIconArgs> = withStyles<
  MyBenchIconArgs,
  { svg: string, path: string }
>((theme, { size, color }) => ({
  svg: {
    marginTop: size === "small" ? theme.spacing(0.5) : 0,
    marginBottom: size === "small" ? 0 : theme.spacing(0.25),
    fill: color ?? theme.palette.standardIcon.main,
  },
  path: {
    fill: "#e3e3e3", // appear transparent
  },
}))(({ classes }) => (
  <svg
    version="1.1"
    id="Layer_1"
    xmlns="http://www.w3.org/2000/svg"
    x="0px"
    y="0px"
    width="24px"
    height="24px"
    viewBox="0 0 100 100"
    className={classes.svg}
  >
    <circle cx="50" cy="50" r="49.188" />
    <path
      className={classes.path}
      d="M83.915,35.98H16.086c-0.906,0-1.639,0.356-1.639,1.261v4.286c0,0.906,0.733,2.018,1.639,2.018h17.315
	l-7.826,24.646c-0.251,0.813,0.735,1.828,1.64,1.828h5.736c0.906,0,1.391-0.91,1.639-1.828l3.261-10.275h23.923l3.262,10.275
	c0.23,0.879,0.735,1.828,1.64,1.828h5.737c0.905,0,1.859-0.914,1.639-1.828l-7.824-24.646h17.687c0.904,0,1.638-1.112,1.638-2.018
	v-4.286C85.553,36.336,84.819,35.98,83.915,35.98z M59.135,49.596h-18.64l1.922-6.051h14.797L59.135,49.596z"
    />
  </svg>
));

MyBenchIcon.displayName = "MyBenchIcon";
export default MyBenchIcon;
