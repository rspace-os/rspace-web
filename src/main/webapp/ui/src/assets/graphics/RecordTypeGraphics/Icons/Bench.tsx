import React, { type ComponentType } from "react";
import { withStyles } from "Styles";

type BenchIconArgs = {
  color: string;
};

const BenchIcon: ComponentType<BenchIconArgs> = withStyles<
  BenchIconArgs,
  { svg: string; wrapper: string }
>((theme, { color }) => ({
  svg: {
    fill: color ?? theme.palette.standardIcon.main,
    position: "absolute",
    bottom: -9,
  },
  wrapper: {
    width: 16,
    position: "relative",
    display: "block",
  },
}))(({ classes }) => (
  <span className={classes.wrapper}>
    <svg
      version="1.1"
      id="Layer_1"
      xmlns="http://www.w3.org/2000/svg"
      x="0px"
      y="0px"
      width="18px"
      height="18px"
      viewBox="0 0 100 100"
      className={classes.svg}
    >
      <path
        d="M95.083,28H5.417C4.22,28,3.25,28.47,3.25,29.667v5.666C3.25,36.53,4.22,38,5.417,38h22.891L17.963,70.584
      C17.63,71.656,18.934,73,20.13,73h7.584c1.196,0,1.838-1.203,2.166-2.416L34.192,57h31.625l4.313,13.584
      C70.433,71.744,71.101,73,72.297,73h7.584c1.196,0,2.458-1.208,2.166-2.416L71.703,38h23.38c1.197,0,2.167-1.47,2.167-2.667v-5.666
      C97.25,28.47,96.28,28,95.083,28z M62.326,46H37.685l2.54-8h19.562L62.326,46z"
      />
    </svg>
  </span>
));

BenchIcon.displayName = "BenchIcon";
export default BenchIcon;
