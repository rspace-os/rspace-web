package com.researchspace.model.dtos;

/**
 * PlantUML diagram: (needs PlantUML Eclipse plugin and Graphviz installed)
 *
 * @startuml title Persistence of Workspace Settings start partition RSpace {
 *     <p>:Incoming URL or page load; repeat partition Java {
 *     <p>if(settingsKey) then (yes) :Get settings from session; note left: back-link \n in session
 *     else (no) :Use request params; note right: Ajax or new request \n in session :Create new \n
 *     WorkspaceSettings \n key in session ; endif :WorkspaceSettings; if (isSearch) then (yes)
 *     :Search; else (no) :Folder listing; endif :Add results to session; :Serialise
 *     WorkspaceSettings to JSON; :Add JSON to model;
 *     <p>} partition JSP { :Write Javascript to parse JSON into WorkspaceSettings-js; } partition
 *     browser { :Execute Javascript to parse JSON into WorkspaceSettings-js; :Update UI based on
 *     WorkspaceSettings-js;
 *     <p>} repeat while ( search / filter / sort) } stop
 * @enduml
 */
public class WorkspaceSettingsUML {}
