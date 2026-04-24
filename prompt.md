Ändere den /fb list command zu dem /map list command.
Bitte entferne das Unbreaking I enchantment von dem "None" reset effekt. mache das item statt einer barriere zu bedrock bitte. Mache überall bei den Cosmetics das "None" zu einer
beim coins multiplier werden nicht alle coins wirklich multipliziert. alle coins außer bei /coins add sollen multiplied werden bitte.
der booster heißt immer 2xx, 3xx, 4xx etc. anstatt 2x, 3x, 4x, etc. Bitte mach nur ein x dabei.
die spawn location, wenn man eine map aufsetzt muss bitte immer in richtung osten sein. wenn man es wirklich überspringen will, dann muss man extra /map setup continue --force eingeben. das --force wird bitte nicht tabcompletet.
die nachrichten sollen bitte immer auf englisch sein und dem &c, &f und &7 design folgen, z.B. so: "&c● /support claim &f<id> &8» &fClaim a ticket." verbessere auch bestehende nachrichten (UNBEDINGT BOOSTER-NACHRICHTEN) damit.
schau bitte nochmal sehr stark auf den timer:
Das gesamte plugin soll bitte sinn ergben und denk gut nach ob es das wirklich macht.
die ganzen pickaxes sollen bitte echte itemnamen haben und nicht nur "&bPickaxe", sondern echte minecraft itemnamen, also den namen bitte einfach nicht ändern. die one click pick soll bitte "&6One click pick" heißen und nicht den vanilla namen haben. wenn man die one click pick ausgewählt hat, soll man nur mit der one click pick blöcke in einem schlag abbauen können, aber nicht mit seiner hand oder seinen blöcken
mach bitte den slot für die replays auf den hotbarslot 7(6 wenn man von 0 beginnt zu zählen) und den slot für die island selection auf slot 5 (4), der slot für den shop muss dann halt auf slot 6 (5) fallen.
man soll auch die größe jedes menüs, guis, etc. selbst einstellen können bitte falls man mit dem nicht zufrieden ist wie es standard ist.
konfiguration ist bei diesem plugin das wichtigste! es soll jede nachricht, gui, item, itemname, kosten, etc. konfigurierbar sein bitte.!!!!!
bei dem replay mach bitte, dass wenn man auf replay neustarten drückt (smaragd), dass man auf der gleichen position bleibt auf der man ist und nur das replay neu startet bitte. wenn das replay fertig ist, soll man es bitte auch mit dem lime dye neustarten können anstatt immer den smaragd verwenden zu müssen. deswegen entferne den smaragd bitte. wenn das replay fertig ist, soll man es mit dem lime dye neu starten können.
wenn man beim island selector auf einer insel ist, die z.b. auf seite 2 ist, dann soll, wenn man den island selector öffenet man auf seite 2 sein und nicht immer auf seite 1.
Bei dem clickspeed setzling hologramm sollst du bitte machen, dass das hologramm erst verschwindet wenn man 1,5 sekunden 0 cps hat. wenn es aktuell schon auf 1,5 sekunden ist, dann mach es auf 1 sekunde.
bei custom length soll es als maximum für die map nur 1700 blöcke geben, nicht mehr. minimum ist das die insel höhe 0 erreicht und maximum höhe ist für die insel nicht da.
das items im custom length menü sollen bitte andere sein: x-länge: stick; y-länge: blaze rod; reset to default: bedrock
die controls beim verschieben sollen umgekehrt sein: linksklick für größere, rechtsklick für kleinere distanz.
das boostermenü auch neu machen, dass es einfach besser aussieht mit dem shop und dem inventar. einfach eine selection wenn man drauf klickt zwischen shop und inventar machen und es nicht ki generiert und dem schema folgend aussehend lassen.
beim boostermenü z.B. auch nicht eine reihe mit allen boostern machen und der rest des menüs ist leer. auch keine "Speed II" attribute oder anderes auf den potions anzeigen
die /map distance ist aktuell immer das doppelte vom echten wert. wenn ich es auf 2 einstelle, ist es eigentlich 4. wenn ich es  aber auf 3 einstelle, ist es 5. das ist sehr unlogisch!!
diagonal support mit extra select bei /map setup --diagonal. du musst dir da viel denken, da die map ja diagonal ist und so. vielleicht muss man dann mehr punkte beim setup auswählen?
bitte mache beim setup bei der mapselection so, dass partikel die aktuelle selection beim schritt 1 anzeigen (also einfach ein quader runderherum, (Bei diagonalen maps irgendwie mehr punkte machen etc.?). danach werden sie wieder entfernt bitte.
wenn man block wechselt, dann kann man erst ab dem nächsten reset blöcke platzieren, bitte fixen.
man soll bitte auch coins 
man soll bei /map distance mit /map distance --force (--force nicht tabcompleten) auch die distance unter 0 blöcke, bzw. auch -1 blöcke, oder -2 etc. setzen können.
die island themes funktionieren nicht. sie werden einfach nicht gepastet. es muss auch beachtet werden, dass die breite von der template map gleich der breite der custom design map entsprechen muss bitte.
man soll auch practice blocks mit der one click pick abbauen können bitte. (egal zu was der pracitce block in der config gesetzt ist bitte)
im /build mode soll man nicht zurückgesetzt werden und auch nicht inseln wechseln. man soll überall frei auf der map herumfliegen können ohne zurückgesetzt zu werden bis man den modus deativiert bitte.
es soll nicht "Time not saved" bei der practice time dastehen, sondern einfach nur die zeit und sonst nichts.
die zeit in der actionbar funktioniert so halb, sie soll gleich wie die echte zeit dann sein bitte. aber die echte zeit ist immer mit 0.09 oder 0.04 hinten. es soll bitte immer 0.05 oder 0.00 sein. und bitte immer 3 kommastellen, also z.b. 7,250
für <map> bei /adddesign <map> <map> tabcompletion hinzufügen
bei /map setup auch tabcompletion hinzufügen
überall wo es irgendwie geht BEI SUBCOMMANDS UND SUBSUBCOMMANDS UNBEDINGT!!! tabcompletion hinzfügen.
für <map> bei /adddesign <map> <map> tabcompletion hinzufügen
bei /map setup auch tabcompletion hinzufügen
überall wo es irgendwie geht BEI SUBCOMMANDS UND SUBSUBCOMMANDS UNBEDINGT!!! tabcompletion hinzfügen.
entferne die walls und zäune vom block selection menü.
es soll niemals irgendwie eine zeit mit x,xx9 hinten stehen. da soll immer und überall eine 0 sein. ACTIONBAR besonders!
wenn man ein anderes island design hat als sein jetziges, dann soll erstens bitte der spawnpunkt und die finish plates, und alles andere auf das design der anderen insel angepasst werden.
wenn man von seiner insel zu einer anderen springt (nicht übers island menu, sondern einfach in minecraft sich bewegt) und diese insel direkt neben einem nicht leer ist, dann soll man bitte auf diese insel wechseln. blöcke werden auch entfernt und so etc. wenn die insel aber schon jemandem gehört, dann wird man zurück zu seiner eigenen teleportiert wie bei einem normalen reset. und wenn man am rand von der map ist, dann geht das auch nur in eine richtung natürlich (einfach island switch mit selbst bewegen statt dem menü; ABER BEIDES SOLL FUNKTIONIEREN!!! DIESES FEATURE IST STANDARD AN, ABER KONFIGURIERBAR IN DER CONFIG) !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
in der actionbar ist die zeit immer um 0,050 sekunden weniger als dann wirklich recorded wird als zeit für die stats.
der timer ist manchmal 3,499 statt 3,5. bitte fixen. bei 0,00X soll immer bei X eine null stehen.
entferne den /booster list command.
ohne argumente soll /booster das booster menü öffnen bitte.
man soll bitte items in seinem inventar nicht verschieben können.
füge den alias /coins für /coin hinzu.
mach bitte, dass man einstellen kann, ob man für fails (resets ohne finish) auch coins bekommt. (standard an, aber nur sehr sehr wenige)
man soll für einen success maximal 20 coins bekommen, wenn man sehr sehr sehr schnell ist. sonst normal sind so 10-15 coins pro run. wenn man langsam ist, bekommmt man nur so 1-5 coins. es soll nicht random sein, sondern pro zeit anders sein. mach das vielleicht auch mit der durchschinttszeit von jedem auf dem server, wieviel coins man bekommt. aktuell bekommt man mehr coins, desto langsamer man ist, das ist falsch. aktuell bekommt man entweder 20 coins oder nur 2 coins, jenachdem ob man normal lang braucht oder 1 minute braucht. das soll bitte von der durchschnittszeit des serves zählen (also durschnittstopzeit); das beste ist 30 coins, schlechteste 5 coins für copmletion, für fails 1 coin
wenn der server crasht wegen zu vielen inseln bei /scale sollen nur die wirklich vollständig generierten inseln angezeigt werden im island selector und die nicht vollständig generierten inseln gelöscht werden. natürlcih soll der server auch nicht einfach so crashen bitte.
die booster sollen bitte 2x Coin Booster, 3x Coin Booster, etc. heißen und nicht supreme, Ultra, etc. bitte.
Build the End Island somewhere to the +X side of the Start Island. das soll bitte -X sein beim customlength setup. weil der spawn ist ja in richtung osten immer bitte.
das /map setup finish soll bei customlength und infinite bitte gleich sein wie bei normalen maps, auch mit command in den chat einfügen, gleichen chatnachrichten, etc.
bei der customlength soll man bitte beim stick nur sehen in den settings, dass man da drauf klicken muss, um zu den settings zu kommen und nicht die aktuelle länge oder wie es zum verstellen geht.
bei der customlength soll es bitte kein leaderboard geben.
alle customlength inseln sollen auch die enden anzeigen, egal ob spieler darauf sind oder nicht. wenn keine darauf sind, zeigt es bitte die standard map an. es soll auch die distance speichern, wenn man z.b. den server verlässt und dann wieder neu herstellen wenn man wieder customlength beitritt. wenn man modus wechselt dann wird es bitte wieder zu standard geändert und erst wenn man wieder kommt zu seiner eigenen distanz bitte.
bitte mach auch, dass man keine standard island designs in der custom length und infinite verwenden kann. (man muss die mit --customlength bzw. --infinite aufgesetzten designs nehmen bitte und nicht die normalen.)
bei der infinite length gibt es ein leaderboard bitte, aber nur mit der distanz, wie weit man gekommen ist bis man gestorben ist und nicht mit der zeit.
alles konfigurierbar machen, alles soll sinn ergeben mit dem bestehenden plugin, alles auf englisch!!!!!!!!