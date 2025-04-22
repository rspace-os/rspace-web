import React from "react";
import SidebarToggle from "./SidebarToggle";

/**
 * A very simple HTML page that includes a sidebar toggle for testing purposes.
 */
export function SimplePageWithSidebarToggle(
  props: Partial<React.ComponentProps<typeof SidebarToggle>>
) {
  return (
    <body>
      <header>
        <h1>A simple page</h1>
        <SidebarToggle
          sidebarOpen={true}
          setSidebarOpen={() => {}}
          sidebarId={"sidebar"}
          {...props}
        />
      </header>
      <main>
        <div id="sidebar"></div>
      </main>
    </body>
  );
}
