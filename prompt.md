
bei den replays soll der spielerskin bitte geladen wereden. aktuell ist es nur ein steve/alex skin. soll mit dem skin angezeigt werden, den man zum zeitpunkt des runs anhatte. da muss man dann einfach nur den skin von damals speichern und nicht neu aufrufen später. soll auzch für cracked server gehen. für den map selector npc, /lb, etc. soll es auch funktionieren bitte
14:58:34 WARN]: Couldn't look up profile properties for com.mojang.authlib.GameProfile@6da32c92[id=c4f3152b-1547-44ed-a770-cddbf4a2956c,name=certbot,properties={},legacy=false]
com.mojang.authlib.exceptions.AuthenticationUnavailableException: Cannot contact authentication server
at com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService.makeRequest(YggdrasilAuthenticationService.java:89) ~[authlib-1.5.25-relocated.jar:?]
at com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService.fillGameProfile(YggdrasilMinecraftSessionService.java:180) ~[authlib-1.5.25-relocated.jar:?]
at com.destroystokyo.paper.profile.PaperMinecraftSessionService.fillGameProfile(PaperMinecraftSessionService.java:37) ~[?:1.8.8-5.1.2-a0333d5]
at com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService.fillProfileProperties(YggdrasilMinecraftSessionService.java:173) ~[authlib-1.5.25-relocated.jar:?]
at com.destroystokyo.paper.profile.PaperMinecraftSessionService.fillProfileProperties(PaperMinecraftSessionService.java:30) ~[?:1.8.8-5.1.2-a0333d5]
at net.minecraft.server.v1_8_R3.TileEntitySkull$1.load(TileEntitySkull.java:76) ~[?:1.8.8-5.1.2-a0333d5]
at net.minecraft.server.v1_8_R3.TileEntitySkull$1.load(TileEntitySkull.java:43) ~[?:1.8.8-5.1.2-a0333d5]
at com.google.common.cache.LocalCache$LoadingValueReference.loadFuture(LocalCache.java:3628) ~[guava-20.0-relocated.jar:?]
at com.google.common.cache.LocalCache$Segment.loadSync(LocalCache.java:2336) ~[guava-20.0-relocated.jar:?]
at com.google.common.cache.LocalCache$Segment.lockedGetOrLoad(LocalCache.java:2295) ~[guava-20.0-relocated.jar:?]
at com.google.common.cache.LocalCache$Segment.get(LocalCache.java:2208) ~[guava-20.0-relocated.jar:?]
at com.google.common.cache.LocalCache.get(LocalCache.java:4053) ~[guava-20.0-relocated.jar:?]
at com.google.common.cache.LocalCache.getOrLoad(LocalCache.java:4057) ~[guava-20.0-relocated.jar:?]
at com.google.common.cache.LocalCache$LocalLoadingCache.get(LocalCache.java:4986) ~[guava-20.0-relocated.jar:?]
at com.google.common.cache.LocalCache$LocalLoadingCache.getUnchecked(LocalCache.java:4992) ~[guava-20.0-relocated.jar:?]
at net.minecraft.server.v1_8_R3.TileEntitySkull$3.call(TileEntitySkull.java:189) ~[?:1.8.8-5.1.2-a0333d5]
at net.minecraft.server.v1_8_R3.TileEntitySkull$3.call(TileEntitySkull.java:186) ~[?:1.8.8-5.1.2-a0333d5]
at net.minecraft.server.v1_8_R3.TileEntitySkull.b(TileEntitySkull.java:205) ~[?:1.8.8-5.1.2-a0333d5]
at org.bukkit.craftbukkit.v1_8_R3.inventory.CraftMetaSkull.applyToItem(CraftMetaSkull.java:95) ~[?:1.8.8-5.1.2-a0333d5]
at org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack.setItemMeta(CraftItemStack.java:405) ~[?:1.8.8-5.1.2-a0333d5]
at org.bukkit.craftbukkit.v1_8_R3.inventory.CraftItemStack.asNMSCopy(CraftItemStack.java:48) ~[?:1.8.8-5.1.2-a0333d5]
at org.bukkit.craftbukkit.v1_8_R3.inventory.CraftInventory.setItem(CraftInventory.java:79) ~[?:1.8.8-5.1.2-a0333d5]
at net.gravijet.fastbuilder.gui.StatsGui.lambda$openLeaderboardGui$1(StatsGui.java:226) ~[?:?]
at org.bukkit.craftbukkit.v1_8_R3.scheduler.CraftTask.run(CraftTask.java:62) ~[?:1.8.8-5.1.2-a0333d5]
at org.bukkit.craftbukkit.v1_8_R3.scheduler.CraftScheduler.mainThreadHeartbeat(CraftScheduler.java:376) ~[?:1.8.8-5.1.2-a0333d5]
at net.minecraft.server.v1_8_R3.MinecraftServer.B(MinecraftServer.java:991) ~[?:1.8.8-5.1.2-a0333d5]
at net.minecraft.server.v1_8_R3.DedicatedServer.B(DedicatedServer.java:474) ~[?:1.8.8-5.1.2-a0333d5]
at net.minecraft.server.v1_8_R3.MinecraftServer.A(MinecraftServer.java:912) ~[?:1.8.8-5.1.2-a0333d5]
at net.minecraft.server.v1_8_R3.MinecraftServer.run(MinecraftServer.java:813) ~[?:1.8.8-5.1.2-a0333d5]
at java.lang.Thread.run(Thread.java:1583) [?:?]
Caused by: com.google.gson.JsonSyntaxException: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $
at com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$Adapter.read(ReflectiveTypeAdapterFactory.java:226) ~[gson-2.8.5-relocated.jar:?]
at com.google.gson.Gson.fromJson(Gson.java:927) ~[gson-2.8.5-relocated.jar:?]
at com.google.gson.Gson.fromJson(Gson.java:892) ~[gson-2.8.5-relocated.jar:?]
at com.google.gson.Gson.fromJson(Gson.java:841) ~[gson-2.8.5-relocated.jar:?]
at com.google.gson.Gson.fromJson(Gson.java:813) ~[gson-2.8.5-relocated.jar:?]
at com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService.makeRequest(YggdrasilAuthenticationService.java:67) ~[authlib-1.5.25-relocated.jar:?]
... 29 more
Caused by: java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $
at com.google.gson.stream.JsonReader.beginObject(JsonReader.java:385) ~[gson-2.8.5-relocated.jar:?]
at com.google.gson.internal.bind.ReflectiveTypeAdapterFactory$Adapter.read(ReflectiveTypeAdapterFactory.java:215) ~[gson-2.8.5-relocated.jar:?]
at com.google.gson.Gson.fromJson(Gson.java:927) ~[gson-2.8.5-relocated.jar:?]
at com.google.gson.Gson.fromJson(Gson.java:892) ~[gson-2.8.5-relocated.jar:?]
at com.google.gson.Gson.fromJson(Gson.java:841) ~[gson-2.8.5-relocated.jar:?]
at com.google.gson.Gson.fromJson(Gson.java:813) ~[gson-2.8.5-relocated.jar:?]
at com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService.makeRequest(YggdrasilAuthenticationService.java:67) ~[authlib-1.5.25-relocated.jar:?]
... 29 more
das plugin soll bitte für minecraft 1.8.8 sein und diese blöcke müssen im block selector funktionieren wenn es sie in der 1.8.8 gibt bitte: hardened clay, structure block, repeating command block, chain command block
man soll mit dem drachenein mit rechtsklick nicht interagieren können und stattdessen wird ein block platziert bitte.
bei unterschiedlichen island designs soll die npc location, pressure plates, spawn location, etc. von dem jeweiligen design sein und nicht vom default design bitte. der spawnpunkt soll auf der gleichen höhe wie der spawnpunkt vom default design sein (für die höhe der insel, wie sie gepastet wird)
fixe das island jumping feature bitte.
bitte mach, dass wenn eine neue config option oder etwas anderes im plugin geupdated wird, dass sich das dann automatisch hinuzfügt zur config im server wenn man das plugin updated.



builde das plugin nicht!