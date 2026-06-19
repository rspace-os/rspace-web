import type React from "react";

function InstrumentHeader(): React.ReactNode {
  return (
    // biome-ignore lint/a11y/noSvgWithoutTitle: initial biome migration
    <svg
      version="1.1"
      x="0px"
      y="0px"
      style={{ height: 67, opacity: 0.7, position: "absolute", bottom: -8, width: 67, right: 113 }}
      width="200px"
      height="200px"
      viewBox="0 0 200 200"
      enableBackground="new 0 0 200 200"
    >
      <g>
        <g>
          <path
            d="M91.6562,69.7777c17.5062,17.5062,17.5062,45.8894,0,63.3957-6.745,6.745-15.1046,10.8912-23.8347,12.4386l3.9683,18.0375c12.0306-2.2823,23.5259-8.0793,32.8375-17.3909,24.67-24.67,24.67-64.668,0-89.3379l-12.9711,12.857Z"
            fill="#f2d1c2"
            stroke="#d38d6e"
            stroke-miterlimit="10"
            stroke-width="4"
          />
          <rect
            x="-.5479"
            y="118.3031"
            width="51.3955"
            height="9.0071"
            rx="4.5036"
            ry="4.5036"
            transform="translate(94.2036 18.1856) rotate(45)"
            fill="#fcf2ed"
            stroke="#d38d6e"
            stroke-miterlimit="10"
            stroke-width="4"
          />
          <rect
            x="17.4361"
            y="168.6787"
            width="79.9993"
            height="12.4395"
            rx="4.5036"
            ry="4.5036"
            fill="#f2d1c2"
            stroke="#d38d6e"
            stroke-miterlimit="10"
            stroke-width="4"
          />
          <rect
            x="38.9357"
            y="136.6787"
            width="37"
            height="31.6428"
            rx="4.5036"
            ry="4.5036"
            fill="#f2d1c2"
            stroke="#d38d6e"
            stroke-miterlimit="10"
            stroke-width="4"
          />
          <circle
            cx="57.4357"
            cy="152.6787"
            r="7"
            fill="#fcf2ed"
            stroke="#d38d6e"
            stroke-miterlimit="10"
            stroke-width="4"
          />
          <g>
            <rect
              x="68.8347"
              y="23.1858"
              width="32.3897"
              height="79.0914"
              rx="4.3166"
              ry="4.3166"
              transform="translate(69.2624 -41.7514) rotate(45)"
              fill="#fcf2ed"
              stroke="#d38d6e"
              stroke-miterlimit="10"
              stroke-width="4"
            />
            <rect
              x="23.6637"
              y="88.9705"
              width="56.1736"
              height="14.08"
              rx="4.5036"
              ry="4.5036"
              transform="translate(83.0471 -8.4723) rotate(45)"
              fill="#fcf2ed"
              stroke="#d38d6e"
              stroke-miterlimit="10"
              stroke-width="4"
            />
            <rect
              x="32.9098"
              y="99.8912"
              width="18.2301"
              height="11.6899"
              rx="2.9286"
              ry="2.9286"
              transform="translate(87.0755 1.2533) rotate(45)"
              fill="#f2d1c2"
              stroke="#d38d6e"
              stroke-miterlimit="10"
              stroke-width="4"
            />
            <rect
              x="111.2712"
              y="22.4943"
              width="13.9109"
              height="14.08"
              transform="translate(55.5117 -74.9485) rotate(45)"
              fill="#f2d1c2"
              stroke="#d38d6e"
              stroke-miterlimit="10"
              stroke-width="4"
            />
            <rect
              x="109.3988"
              y="12.19"
              width="38.2645"
              height="14.08"
              rx="3.0819"
              ry="3.0819"
              transform="translate(51.2435 -85.2528) rotate(45)"
              fill="#fcf2ed"
              stroke="#d38d6e"
              stroke-miterlimit="10"
              stroke-width="4"
            />
          </g>
          <g>
            <line
              x1="21.7031"
              y1="141.9113"
              x2="38.4171"
              y2="158.6253"
              fill="none"
              stroke="#d38d6e"
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="4"
            />
            <path
              d="M20.7311,145.0461L2.9017,127.2167c-1.1945-1.1945-1.2033-3.1222-.0197-4.3058h0c1.1836-1.1836,3.1113-1.1747,4.3058.0197l17.8294,17.8294c1.1945,1.1945,1.2033,3.1222.0197,4.3058h0c-1.1836,1.1836-3.1113,1.1747-4.3058-.0197Z"
              fill="#f2d1c2"
              stroke="#d38d6e"
              stroke-miterlimit="10"
              stroke-width="4"
            />
          </g>
          <rect
            x="54.569"
            y="64.0322"
            width="72.832"
            height="9.5355"
            rx="2.4477"
            ry="2.4477"
            transform="translate(-22.0001 84.4872) rotate(-45)"
            fill="#f4e4dc"
          />
          <rect
            x="132.8876"
            y="22.7282"
            width="8.2405"
            height="9.5355"
            rx="2.4477"
            ry="2.4477"
            transform="translate(20.6861 104.9326) rotate(-45)"
            fill="#f4e4dc"
          />
          <rect
            x="62.0395"
            y="105.709"
            width="8.3856"
            height="9.5355"
            rx="2.4477"
            ry="2.4477"
            transform="translate(-58.7199 79.1912) rotate(-45)"
            fill="#f4e4dc"
          />{" "}
        </g>
      </g>
    </svg>
  );
}

InstrumentHeader.displayName = "Instrument";
export default InstrumentHeader;
