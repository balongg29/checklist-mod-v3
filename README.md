# Farm Checklist (client mod)

Minecraft 26.2, Fabric. Requires Fabric API and Java 25. Client-only: every player installs it themselves.

Press K (rebindable in Controls) to open your checklists.

- Picker: New Checklist, Import from Clipboard, and your saved checklists with a status square
  (gray = not started, yellow = in progress, green = complete), a checked/total count, and item icons.
- Checklist: click a row to check it off. Scan checks off what's in your inventory. Copy, Add Item, Complete/Reopen, Delete.
  Click the pencil on a row to change it to another item (e.g. oak trapdoor -> spruce trapdoor), change how many you Need,
  type how many you Have (use the up/down arrows, or the Up/Down keys; hold Shift for 10), or Remove the row. Scan never lowers a Have you typed.
- Add Item: a creative-inventory style grid. Tabs: All, Blocks, Items, Types. Types holds fence, wool, concrete, slab, stairs, wall,
  trapdoor, door, planks, carpet, glass, bed, banner and more: click a type, then click the wood / color / material you want.
- Import from Clipboard checks every line against the game's real item list. Lines that are not real items (like "temporary_blocks")
  are left out; a review screen shows them with a "Did you mean...?" guess you can accept with Use. Scaffolding and other real blocks stay.
- Scroll bars are wide and can be dragged.

Checklists are plain text files in .minecraft/farmlist/transcriptions/ (first line "# Title", then "item_id count"). Also accepted: "fence:spruce 64", "wall:cobblestone 64", "oak trapdoor x64".
A bare "wall 64" means a wall whose material you pick later with the pencil.
Checked state is saved in .minecraft/farmlist/progress/.

Build: ./gradlew build  (or let GitHub Actions build it; download the jar from the run's artifacts)
