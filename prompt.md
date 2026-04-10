bei /map setup finish <name> im chat soll, wenn man auf den befehl draufklickt, der befehl in seinen chat kopiert werden, dass man den ersten teil nicht selbst schreiben muss.!!!!!!
es soll außerdem statt /map setup finish /map setup finish <name> dastehen bitte.
custom length ist NICHT die infinite distance! Entferne das aus den settings!
bei custom length gibt man bitte /map setup --customlength ein.
da muss man dann die startinsel EINZELN und die endinsel EINZEL auswählen, damit sich die endinsel auch bewegen kann!!! man setzt dann auch eine standarddistanz für die entfernung. und dann kann man in dem settingsmenü bitte einstellen wie weit die insel enfert ist (am besten mit shift linksklick für -10 blöcke und linksklick für -1 block und bei rechtsklick halt +1 block und bei shift rechtsklick +10 blöcke. irgendwie soll man es auch resetten können (wird auch resetted wenn man dem server, oder die map verlässt und neu beitritt; DIE INSEL MUSS SICH UNBEDINGT IMMER IN ECHTZEIT VERSCHIEBEN!!!!))
im replay soll bitte der gleiche name wie in echt sein wann man das replay aufgenommen hat vom spieler. also mit prefix, farbe, skin, cape, etc. 1:1 und nicht nur &5<playername>
das konfigurierbar machen mit dem insel hopping:
das mit "Showing page 2 of 3. (21 results). » «" soll bitte nur ganz unten auf der helpmessage angezeigt werden und nicht oben. außerdem nur ein pfeil, der entweder &a (nach vorne) ist oder &c (zurück), jenachdem ist der pfeil auch vor dem text (zurück) oder hinter dem text (vor)
wenn man von seiner insel zu einer anderen springt (nicht übers island menu, sondern einfach in minecraft sich bewegt) und diese insel direkt neben einem nicht leer ist, dann soll man bitte auf diese insel wechseln. blöcke werden auch entfernt und so etc. wenn die insel aber schon jemandem gehört, dann wird man zurück zu seiner eigenen teleportiert wie bei einem normalen reset. und wenn man am rand ist, dann geht das auch nur in eine richtung natürlich
mach bitte auch konfigurierbar, ob das hologramm für die cps beim setlzing und ob wenn man mit den cps mit dem setzling andere spieler sehen können oder nur der spieler, der gerade klickt.
mach bitte wirklich alles was geht, konfigurierbar, jedes item, jeder preis im shop, alles!!!!
der mapselector ist manchmal einen block im boden anstatt wie ein normaler spieler auf dem block zu stehen und geht erst wieder ganz normal hinauf, wenn man insel wechselt! bitte fixen, hat davor funktioniert!
die zeit eines runs soll bitte niemals 3,001 sein sondern immer 3,000 oder 3,050 aber nicht 0,001 weil das kann man gar nicht messen.
bei dem shop mit den blocks soll bitte noch ein "Back to Shop" button sein wie überall sonst.
außerdem sollen beim blockshop immer beide pfeile für go back und weiter da sein. wenn man einmal nicht auf die nächste seite kann, steht halt irgendwie da dass es keien nächste seite gibt.
bitte für jedes shopitem und alles permissions machen, dass man wirklich für alles und jedes permissions geben kann, auch für blöcke, resetanimationen, resetsounds, spitzhacke, stats reset, unlimited blocks, etc.
füge bitte zu den resetsounds noch mehr sounds hinzu, die bitte nur kurz und nicht lang brauchen (max. 0.8s)
in dem shopmenü, wo man die spitzhacken auswählen kann, kann man immernoch alles einfach so verschieben und es passiert nichts und man kann auch nichts auswählen oder kaufen. es ist auch keine one click pick (diamantaxt, die alle blöcke mit einem klick abbaut) da, die bitte im pickaxe shop sein soll. das muss unbedingt gefixed werden!
du musst hier sehr sehr viel löschen und von grund auf neu und besser schreiben bitte!


Prompt 1:
Task: Comprehensive Refactoring of the Plugin Core, Permission System, and Shop Logic.

Context: We are rebuilding parts of the system to ensure 100% configurability and a robust permission-based architecture.
Files: src/ (Focus on Shop, Permissions, Config, and Item handlers)

Requirements:
1. Global Configurability: Move every item, price, message, and sound into a central configuration. Ensure that no values are hardcoded.
2. Permission System: Implement a granular permission system. Every feature (blocks, reset-animations, reset-sounds, pickaxes, stats-reset, unlimited blocks, etc.) must check for a specific permission before use.
3. Shop UI Overhaul:
    - Fix the Pickaxe Shop: Prevent players from moving items in the inventory.
    - Implement a "One Click Pick" (Diamond Axe) that instabreaks all blocks.
    - Add a "Back to Shop" button to every sub-menu.
    - Standardize pagination: Arrows for "Next/Back" must always be visible. If a page doesn't exist, display a "No more pages" message or a disabled state.
4. Timer Precision: Adjust the run timer logic. Ensure times are rounded/snapped to intervals (e.g., 3.000 or 3.050). Measurements like 0.001 must be removed as they are physically impossible to track accurately.
5. Coding Style: Write idiomatic, clean Java code. Avoid typical AI tropes. Use meaningful variable names and follow standard Minecraft plugin development patterns (human-like structure).

Model Recommendation: Claude 4.6 Opus | Effort: Max


Prompt 2:
Task: Implementation of Advanced Island Mechanics and Custom Distance Logic.

Requirements:
1. Custom Length System:
    - Remove "Infinite Distance" from settings; replace it with "--customlength".
    - Workflow: Player selects Start Island and End Island individually.
    - Real-time movement: When the distance is adjusted in the settings menu (Left-click: -1, Shift-Left: -10, Right-click: +1, Shift-Right: +10), the end island must move physically in the world in real-time.
    - Reset Logic: Reset island position when a player leaves the server or the map.
2. Island Hopping:
    - If a player walks from their island to an adjacent non-empty island, trigger a switch.
    - Handle block clearing and state transfer.
    - If the target island is occupied, teleport the player back (standard reset).
    - Respect boundary limits (one-way hopping at the edge).
3. Configurable Visuals:
    - Make CPS holograms (for seedlings) toggleable: Global visibility vs. Private (only clicking player sees it).

Model Recommendation: Claude 4.6 Opus | Effort: Max

prompt 2 continue
claude --resume 39e2a3c2-b388-451d-b7ca-bf61782d3a95

Prompt 3:
Task: UI Polishing, Command Enhancement, and Replay Fidelity.

Requirements:
1. Interactive Commands:
    - Update "/map setup finish <name>": When clicked in chat, it should suggest/copy the command into the player's chat bar.
    - Display the full command "/map setup finish <name>" instead of just the prompt.
2. Help Message UI:
    - Move pagination ("Showing page X of Y") to the very bottom.
    - Use single arrows for navigation: &a (Forward, placed after text) or &c (Back, placed before text) depending on the context.
3. 1:1 Replay System:
    - Ensure the player in the replay is an exact clone of the original player at the time of recording.
    - This includes: Prefix, Colors, Skin, Cape, and Name formatting (No more hardcoded &5 prefix).
4. Bug Fix:
    - MapSelector NPC: Fix the issue where the NPC spawns one block inside the ground. It should stand perfectly on the block surface.
5. Audio:
    - Add more Reset-Sounds to the config. All sounds must be short (max 0.8s).

Model Recommendation: Claude 4.6 Sonnet | Effort: Medium/High


