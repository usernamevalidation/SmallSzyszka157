   package ca.small_szyszka157.sizeplugin;

   import org.bukkit.Bukkit;
   import org.bukkit.ChatColor;
   import org.bukkit.attribute.Attribute;
   import org.bukkit.attribute.AttributeInstance;
   import org.bukkit.command.Command;
   import org.bukkit.command.CommandSender;
   import org.bukkit.command.TabCompleter;
   import org.bukkit.configuration.ConfigurationSection;
   import org.bukkit.configuration.file.FileConfiguration;
   import org.bukkit.configuration.file.YamlConfiguration;
   import org.bukkit.entity.Entity;
   import org.bukkit.entity.Player;
   import org.bukkit.entity.Projectile;
   import org.bukkit.event.EventHandler;
   import org.bukkit.event.EventPriority;
   import org.bukkit.event.Listener;
   import org.bukkit.event.entity.EntityDamageByEntityEvent;
   import org.bukkit.event.entity.EntityDamageEvent;
   import org.bukkit.event.inventory.InventoryClickEvent;
   import org.bukkit.event.inventory.InventoryCloseEvent;
   import org.bukkit.event.inventory.InventoryType;
   import org.bukkit.event.player.PlayerJoinEvent;
   import org.bukkit.event.player.PlayerItemHeldEvent;
   import org.bukkit.event.player.PlayerSwapHandItemsEvent;
   import org.bukkit.inventory.ItemStack;
   import org.bukkit.plugin.java.JavaPlugin;

   import java.io.File;
   import java.io.FileWriter;
   import java.io.IOException;
   import java.io.InputStream;
   import java.io.InputStreamReader;
   import java.lang.reflect.Field;
   import java.lang.reflect.Method;
   import java.nio.charset.StandardCharsets;
   import java.text.SimpleDateFormat;
   import java.util.ArrayList;
   import java.util.Collections;
   import java.util.Date;
   import java.util.HashMap;
   import java.util.HashSet;
   import java.util.List;
   import java.util.Map;
   import java.util.Set;
   import java.util.UUID;

   public final class SmallSzyszka157 extends JavaPlugin implements Listener, TabCompleter {

       private static final String T_PLAYER = "player";
       private static final String T_ENTITY = "entity";
       private static final String T_NATURAL = "natural";
       private static final String T_ATTACKER_PVP = "attacker-pvp";
       private static final String T_ATTACKER_MOB = "attacker-mob";

       private boolean debugMode = false;
       private boolean scaleSupported = true;
       private String scaleAttributeName = "GENERIC_SCALE";

       private boolean pvpCooldownEnabled = false;

       private boolean triggerVictimPlayer = true;
       private boolean triggerVictimEntity = false;
       private boolean triggerVictimNatural = false;

       private boolean triggerAttackerPvp = false;
       private boolean triggerAttackerMob = false;

       private boolean listRequiresPermission = false;

       private final Map<String, Integer> cooldownSeconds = new HashMap<>();

       private final Map<String, Double> helmetScales = new HashMap<>();
       private final Map<String, Double> colorScales = new HashMap<>();

       private final Map<UUID, Long> cooldownEndTime = new HashMap<>();
       private final Map<UUID, Boolean> isInCooldown = new HashMap<>();
       private final Map<UUID, Long> lastMessageTime = new HashMap<>();

       private File messagesFile;
       private FileConfiguration messages;
       private final Set<String> warnedMissingKeys = new HashSet<>();

       // ================================================================
       // LIFECYCLE
       // ================================================================

       @Override
       public void onEnable() {
           saveDefaultConfig();
           saveDefaultMessages();
           loadMessages();
           loadConfig();

           getCommand("hsize").setTabCompleter(this);

           getServer().getPluginManager().registerEvents(this, this);
           detectScaleAttribute();

           logMsg("console.enabled-header");
           logMsg("console.enabled-title");
           logMsg("console.enabled-pvp",
                   "status", statusText(pvpCooldownEnabled));
           logMsg("console.enabled-victim",
                   "victim-player", boolText(triggerVictimPlayer),
                   "victim-entity", boolText(triggerVictimEntity),
                   "victim-natural", boolText(triggerVictimNatural));
           logMsg("console.enabled-attacker",
                   "attacker-pvp", boolText(triggerAttackerPvp),
                   "attacker-mob", boolText(triggerAttackerMob));
           logMsg("console.enabled-helmets", "count", String.valueOf(helmetScales.size()));
           logMsg("console.enabled-header");

           for (String name : helmetScales.keySet()) {
               logMsg("console.enabled-helmet-entry",
                       "name", name,
                       "scale", String.valueOf(helmetScales.get(name)));
           }

           for (Player player : Bukkit.getOnlinePlayers()) {
               if (!isInCooldown(player)) {
                   forceCheckAndApplySize(player);
               }
           }
       }

       @Override
       public void onDisable() {
           for (Player player : Bukkit.getOnlinePlayers()) {
               resetPlayerScale(player);
           }
           logMsg("console.disabled");
       }

       // ================================================================
       // CONFIG LOADING
       // ================================================================

       private void loadConfig() {
           reloadConfig();
           FileConfiguration config = getConfig();

           debugMode = config.getBoolean("debug-mode", false);
           pvpCooldownEnabled = config.getBoolean("pvp-cooldown-enabled", false);
           listRequiresPermission = config.getBoolean("list-requires-permission", false);

           ConfigurationSection triggers = config.getConfigurationSection("damage-triggers");
           if (triggers != null) {
               triggerVictimPlayer = triggers.getBoolean("player", true);
               triggerVictimEntity = triggers.getBoolean("entity", false);
               triggerVictimNatural = triggers.getBoolean("natural", false);
               triggerAttackerPvp = triggers.getBoolean("attacker-pvp", false);
               triggerAttackerMob = triggers.getBoolean("attacker-mob", false);
           } else {
               triggerVictimPlayer = true;
               triggerVictimEntity = false;
               triggerVictimNatural = false;
               triggerAttackerPvp = false;
               triggerAttackerMob = false;
           }

           cooldownSeconds.clear();
           ConfigurationSection sec = config.getConfigurationSection("cooldown-seconds");
           if (sec != null) {
               for (String key : sec.getKeys(false)) {
                   cooldownSeconds.put(key.toLowerCase(), sec.getInt(key, 15));
               }
           }

           helmetScales.clear();
           colorScales.clear();

           ConfigurationSection helmetsSection = config.getConfigurationSection("helmets");
           if (helmetsSection != null) {
               for (String key : helmetsSection.getKeys(false)) {
                   double scale = helmetsSection.getDouble(key, 0.88);
                   helmetScales.put(key, scale);
               }
           }

           ConfigurationSection colorsSection = config.getConfigurationSection("colors");
           if (colorsSection != null) {
               for (String key : colorsSection.getKeys(false)) {
                   double scale = colorsSection.getDouble(key, 0.88);
                   colorScales.put(key.toLowerCase(), scale);
               }
           }

           if (config.contains("scale-attribute") && !config.getString("scale-attribute").isEmpty()) {
               scaleAttributeName = config.getString("scale-attribute");
           }
       }

       private void saveDefaultMessages() {
           messagesFile = new File(getDataFolder(), "messages.yml");
           if (!messagesFile.exists()) {
               saveResource("messages.yml", false);
           }
       }

       private void loadMessages() {
           if (messagesFile == null) {
               messagesFile = new File(getDataFolder(), "messages.yml");
           }
           if (!messagesFile.exists()) {
               saveResource("messages.yml", false);
           }
           messages = YamlConfiguration.loadConfiguration(messagesFile);

           InputStream defStream = getResource("messages.yml");
           if (defStream != null) {
               YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                       new InputStreamReader(defStream, StandardCharsets.UTF_8));
               messages.setDefaults(defaults);
           }

           warnedMissingKeys.clear();
           logMsg("console.config-loaded");
       }

       // ================================================================
       // MESSAGE HELPERS
       // ================================================================

       private String translate(String raw) {
           String s = ChatColor.translateAlternateColorCodes('&', raw);
           String prefix = messages.getString("prefix");
           if (prefix != null) {
               String colored = ChatColor.translateAlternateColorCodes('&', prefix);
               s = s.replace("%prefix%", colored);
           }
           return s;
       }

       private String applyReplacements(String s, String... replacements) {
           for (int i = 0; i + 1 < replacements.length; i += 2) {
               s = s.replace("%" + replacements[i] + "%", replacements[i + 1]);
           }
           return s;
       }

       private String msg(String key, String... replacements) {
           String raw = messages.getString(key);
           if (raw == null) {
               if (warnedMissingKeys.add(key)) {
                   getLogger().warning("Missing messages.yml key: " + key);
               }
               return null;
           }
           return applyReplacements(translate(raw), replacements);
       }

       private List<String> msgList(String key) {
           List<String> list = messages.getStringList(key);
           if (list == null || list.isEmpty()) {
               if (warnedMissingKeys.add(key)) {
                   getLogger().warning("Missing messages.yml key (list): " + key);
               }
               return new ArrayList<>();
           }
           return list;
       }

       private void send(CommandSender to, String key, String... replacements) {
           String scalar = msg(key, replacements);
           if (scalar != null) {
               if (!scalar.isEmpty()) {
                   to.sendMessage(scalar);
               }
               return;
           }

           List<String> list = msgList(key);
           for (String line : list) {
               String out = applyReplacements(translate(line), replacements);
               if (!out.isEmpty()) {
                   to.sendMessage(out);
               }
           }
       }

       private void sendList(CommandSender to, String key, String... replacements) {
           List<String> list = msgList(key);
           for (String line : list) {
               String out = applyReplacements(translate(line), replacements);
               if (!out.isEmpty()) {
                   to.sendMessage(out);
               }
           }
       }

       private void logMsg(String key, String... replacements) {
           String m = msg(key, replacements);
           if (m != null && !m.isEmpty()) {
               getLogger().info(ChatColor.stripColor(m));
               return;
           }
           List<String> list = msgList(key);
           for (String line : list) {
               String out = applyReplacements(translate(line), replacements);
               if (!out.isEmpty()) {
                   getLogger().info(ChatColor.stripColor(out));
               }
           }
       }

       private String boolText(boolean value) {
           String key = value ? "values.yes" : "values.no";
           String s = msg(key);
           if (s == null) {
               if (warnedMissingKeys.add(key)) {
                   getLogger().warning("messages.yml is missing '" + key
                           + "'. Using built-in default.");
               }
               s = value ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO";
           }
           return s;
       }

       private String statusText(boolean enabled) {
           String key = enabled ? "values.enabled" : "values.disabled";
           String s = msg(key);
           if (s == null) {
               if (warnedMissingKeys.add(key)) {
                   getLogger().warning("messages.yml is missing '" + key
                           + "'. Using built-in default.");
               }
               s = enabled ? ChatColor.RED + "ENABLED" : ChatColor.GREEN + "DISABLED";
           }
           return s;
       }

       private int secondsFor(String triggerKey) {
           Integer v = cooldownSeconds.get(triggerKey.toLowerCase());
           return v != null ? v : 15;
       }

       // ================================================================
       // SCALE ATTRIBUTE DETECTION
       // ================================================================

       private void detectScaleAttribute() {
           try {
               Attribute.valueOf("GENERIC_SCALE");
               scaleSupported = true;
               scaleAttributeName = "GENERIC_SCALE";
               logMsg("console.scale-supported");
           } catch (IllegalArgumentException e) {
               scaleSupported = false;
               logMsg("console.scale-missing");

               try {
                   Field[] fields = Attribute.class.getFields();
                   for (Field field : fields) {
                       if (field.getName().contains("SCALE") || field.getName().contains("scale")) {
                           scaleAttributeName = field.getName();
                           scaleSupported = true;
                           logMsg("console.scale-fallback-found", "name", scaleAttributeName);
                           break;
                       }
                   }
               } catch (Exception ex) {
                   logMsg("console.scale-fallback-none");
               }
           }
       }

       private AttributeInstance getScaleAttribute(Player player) {
           if (!scaleSupported) return null;
           try {
               try {
                   Attribute attr = Attribute.valueOf(scaleAttributeName);
                   return player.getAttribute(attr);
               } catch (IllegalArgumentException e) {
                   return getScaleAttributeReflection(player);
               }
           } catch (Exception e) {
               return null;
           }
       }

       private AttributeInstance getScaleAttributeReflection(Player player) {
           try {
               Class<?> attributeClass = Class.forName("org.bukkit.attribute.Attribute");
               Method getAttribute = player.getClass().getMethod("getAttribute", attributeClass);

               String[] possibleNames = {"GENERIC_SCALE", "SCALE"};
               for (String name : possibleNames) {
                   try {
                       Field field = attributeClass.getField(name);
                       Object attr = field.get(null);
                       Object result = getAttribute.invoke(player, attr);
                       if (result != null) {
                           return (AttributeInstance) result;
                       }
                   } catch (Exception ignored) {
                   }
               }
           } catch (Exception e) {
               if (debugMode) {
                   getLogger().warning("Reflection failed: " + e.getMessage());
               }
           }
           return null;
       }

       // ================================================================
       // COOLDOWN LOGIC
       // ================================================================

       private boolean isInCooldown(Player player) {
           UUID playerId = player.getUniqueId();
           if (!isInCooldown.containsKey(playerId) || !isInCooldown.get(playerId)) {
               return false;
           }

           long currentTime = System.currentTimeMillis();
           long endTime = cooldownEndTime.getOrDefault(playerId, 0L);

           if (currentTime >= endTime) {
               isInCooldown.put(playerId, false);
               cooldownEndTime.remove(playerId);
               if (debugMode) {
                   getLogger().info(player.getName() + " cooldown expired!");
               }
               forceCheckAndApplySize(player);
               return false;
           }
           return true;
       }

       private void startCooldown(Player player, String triggerKey) {
           UUID playerId = player.getUniqueId();
           long currentTime = System.currentTimeMillis();
           int seconds = secondsFor(triggerKey);
           long newEndTime = currentTime + (seconds * 1000L);

           if (isInCooldown.containsKey(playerId) && isInCooldown.get(playerId)) {
               long oldEndTime = cooldownEndTime.getOrDefault(playerId, 0L);
               long remaining = oldEndTime - currentTime;

               if (remaining > 0 && newEndTime < oldEndTime) {
                   if (debugMode) {
                       getLogger().info("Trigger '" + triggerKey + "' ignored for "
                               + player.getName() + " (new duration " + seconds
                               + "s < remaining " + (remaining / 1000) + "s)");
                   }
                   return;
               }
           }

           boolean alreadyInCooldown = isInCooldown.containsKey(playerId) && isInCooldown.get(playerId);

           isInCooldown.put(playerId, true);
           cooldownEndTime.put(playerId, newEndTime);

           if (debugMode) {
               getLogger().info("===== COOLDOWN STARTED for " + player.getName()
                       + " (trigger: " + triggerKey + ", " + seconds + "s) =====");
           }

           try {
               AttributeInstance scale = getScaleAttribute(player);
               if (scale != null) {
                   double current = scale.getBaseValue();
                   scale.setBaseValue(1.0);
                   if (debugMode) {
                       getLogger().info("  Scale reset from " + current + " to 1.0");
                   }
               }
           } catch (Exception e) {
               getLogger().severe("Error resetting scale: " + e.getMessage());
           }

           Long lastMsg = lastMessageTime.get(playerId);
           boolean spam = lastMsg != null && (currentTime - lastMsg) < 1000;
           lastMessageTime.put(playerId, currentTime);

           if (!spam) {
               if (!alreadyInCooldown) {
                   send(player, "cooldown.started", "seconds", String.valueOf(seconds));
                   send(player, "cooldown.started-sub");
               } else {
                   send(player, "cooldown.refreshed", "seconds", String.valueOf(seconds));
               }
           }
       }

       // ================================================================
       // COMMAND HANDLING
       // ================================================================

       @Override
       public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
           if (!command.getName().equalsIgnoreCase("hsize")) return false;

           if (args.length == 0) {
               sendHelpMessage(sender);
               return true;
           }

           String subCommand = args[0].toLowerCase();

           switch (subCommand) {
               case "reload": {
                   if (!sender.hasPermission("sizeplugin.reload") && !sender.isOp()) {
                       send(sender, "generic.no-permission");
                       return true;
                   }
                   reloadPlugin(sender);
                   return true;
               }
               case "status": {
                   if (!sender.hasPermission("sizeplugin.status") && !sender.isOp()) {
                       send(sender, "generic.no-permission");
                       return true;
                   }
                   sendStatusMessage(sender);
                   return true;
               }
               case "list": {
                   if (listRequiresPermission
                           && !sender.hasPermission("sizeplugin.list")
                           && !sender.isOp()) {
                       send(sender, "generic.no-permission");
                       return true;
                   }
                   sendHelmetList(sender);
                   return true;
               }
               case "debug": {
                   if (!sender.hasPermission("sizeplugin.debug") && !sender.isOp()) {
                       send(sender, "generic.no-permission");
                       return true;
                   }
                   runDebug(sender);
                   return true;
               }
               default: {
                   sendHelpMessage(sender);
                   return true;
               }
           }
       }

       @Override
       public List<String> onTabComplete(CommandSender sender, Command command,
                                         String alias, String[] args) {
           if (!command.getName().equalsIgnoreCase("hsize")) {
               return Collections.emptyList();
           }

           if (args.length != 1) {
               return Collections.emptyList();
           }

           String partial = args[0].toLowerCase();
           List<String> suggestions = new ArrayList<>();

           if ("help".startsWith(partial)) suggestions.add("help");
           if ("reload".startsWith(partial)
                   && (sender.hasPermission("sizeplugin.reload") || sender.isOp())) {
               suggestions.add("reload");
           }
           if ("status".startsWith(partial)
                   && (sender.hasPermission("sizeplugin.status") || sender.isOp())) {
               suggestions.add("status");
           }
           boolean listAllowed = !listRequiresPermission
                   || sender.hasPermission("sizeplugin.list")
                   || sender.isOp();
           if (listAllowed && "list".startsWith(partial)) suggestions.add("list");
           if ("debug".startsWith(partial)
                   && (sender.hasPermission("sizeplugin.debug") || sender.isOp())) {
               suggestions.add("debug");
           }

           return suggestions;
       }

       private void reloadPlugin(CommandSender sender) {
           loadMessages();
           loadConfig();
           detectScaleAttribute();
           isInCooldown.clear();
           cooldownEndTime.clear();
           lastMessageTime.clear();

           for (Player player : Bukkit.getOnlinePlayers()) {
               forceCheckAndApplySize(player);
           }

           sendList(sender, "hsize.reload",
                   "status", statusText(pvpCooldownEnabled),
                   "victim-player", boolText(triggerVictimPlayer),
                   "victim-entity", boolText(triggerVictimEntity),
                   "victim-natural", boolText(triggerVictimNatural),
                   "attacker-pvp", boolText(triggerAttackerPvp),
                   "attacker-mob", boolText(triggerAttackerMob),
                   "victim-player-seconds", String.valueOf(secondsFor(T_PLAYER)),
                   "victim-entity-seconds", String.valueOf(secondsFor(T_ENTITY)),
                   "victim-natural-seconds", String.valueOf(secondsFor(T_NATURAL)),
                   "attacker-pvp-seconds", String.valueOf(secondsFor(T_ATTACKER_PVP)),
                   "attacker-mob-seconds", String.valueOf(secondsFor(T_ATTACKER_MOB)),
                   "count", String.valueOf(helmetScales.size()));
       }

       private void sendStatusMessage(CommandSender sender) {
           sendList(sender, "hsize.status",
                   "status", statusText(pvpCooldownEnabled),
                   "victim-player", boolText(triggerVictimPlayer),
                   "victim-entity", boolText(triggerVictimEntity),
                   "victim-natural", boolText(triggerVictimNatural),
                   "attacker-pvp", boolText(triggerAttackerPvp),
                   "attacker-mob", boolText(triggerAttackerMob),
                   "victim-player-seconds", String.valueOf(secondsFor(T_PLAYER)),
                   "victim-entity-seconds", String.valueOf(secondsFor(T_ENTITY)),
                   "victim-natural-seconds", String.valueOf(secondsFor(T_NATURAL)),
                   "attacker-pvp-seconds", String.valueOf(secondsFor(T_ATTACKER_PVP)),
                   "attacker-mob-seconds", String.valueOf(secondsFor(T_ATTACKER_MOB)),
                   "count", String.valueOf(helmetScales.size()));
       }

       private void sendHelmetList(CommandSender sender) {
           List<String> list = msgList("hsize.list");
           if (list.isEmpty()) return;

           List<String> staticLines = new ArrayList<>();
           List<String> helmetLines = new ArrayList<>();

           for (String line : list) {
               if (line.contains("%name%") || line.contains("%scale%")) {
                   helmetLines.add(line);
               } else {
                   staticLines.add(line);
               }
           }

           for (String line : staticLines) {
               String out = translate(line);
               if (!out.isEmpty()) sender.sendMessage(out);
           }

           for (Map.Entry<String, Double> entry : helmetScales.entrySet()) {
               for (String line : helmetLines) {
                   String out = applyReplacements(translate(line),
                           "name", entry.getKey(),
                           "scale", String.valueOf(entry.getValue()));
                   if (!out.isEmpty()) sender.sendMessage(out);
               }
           }
       }

       private void sendHelpMessage(CommandSender sender) {
           sendList(sender, "hsize.help");
       }

       // ================================================================
       // DEBUG COMMAND
       // ================================================================

       private void runDebug(CommandSender sender) {
           File debugFile = new File(getDataFolder(), "debug.log");
           List<String> out = new ArrayList<>();

           String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
           out.add("SmallSzyszka157 debug report - " + ts);
           out.add("Triggered by: " + sender.getName());
           out.add("");

           // ---- PATH SECTION ----
           out.add("[Paths]");
           out.add("dataFolder: " + getDataFolder().getAbsolutePath());
           out.add("config.yml: " + new File(getDataFolder(), "config.yml").getAbsolutePath()
                   + " (exists=" + new File(getDataFolder(), "config.yml").exists() + ")");
           out.add("messages.yml: " + messagesFile.getAbsolutePath()
                   + " (exists=" + messagesFile.exists() + ")");
           out.add("debug.log: " + debugFile.getAbsolutePath());
           out.add("");

           // ---- RAW YAML FROM DISK ----
           out.add("[Raw YAML from disk]");
           try {
               File cfg = new File(getDataFolder(), "config.yml");
               YamlConfiguration rawCfg = YamlConfiguration.loadConfiguration(cfg);
               out.add("config contains damage-triggers.player: "
                       + rawCfg.contains("damage-triggers.player"));
               out.add("config contains cooldown-seconds.player: "
                       + rawCfg.contains("cooldown-seconds.player"));
               out.add("config contains helmets: " + rawCfg.contains("helmets"));
               out.add("config keys: " + rawCfg.getKeys(true));
           } catch (Exception e) {
               out.add("config read error: " + e.getMessage());
           }

           try {
               YamlConfiguration rawMsg = YamlConfiguration.loadConfiguration(messagesFile);
               out.add("messages contains values.yes: " + rawMsg.contains("values.yes"));
               out.add("messages contains values.no: " + rawMsg.contains("values.no"));
               out.add("messages contains cooldown.started: " + rawMsg.contains("cooldown.started"));
               out.add("messages contains console.enabled-title: "
                       + rawMsg.contains("console.enabled-title"));
               out.add("messages keys: " + rawMsg.getKeys(true));
           } catch (Exception e) {
               out.add("messages read error: " + e.getMessage());
           }
           out.add("");

           // ---- IN MEMORY ----
           out.add("[In-memory state]");
           out.add("debugMode: " + debugMode);
           out.add("scaleSupported: " + scaleSupported);
           out.add("scaleAttributeName: " + scaleAttributeName);
           out.add("pvpCooldownEnabled: " + pvpCooldownEnabled);
           out.add("listRequiresPermission: " + listRequiresPermission);
           out.add("triggerVictimPlayer: " + triggerVictimPlayer);
           out.add("triggerVictimEntity: " + triggerVictimEntity);
           out.add("triggerVictimNatural: " + triggerVictimNatural);
           out.add("triggerAttackerPvp: " + triggerAttackerPvp);
           out.add("triggerAttackerMob: " + triggerAttackerMob);
           out.add("cooldownSeconds: " + cooldownSeconds);
           out.add("helmetScales (" + helmetScales.size() + "): " + helmetScales);
           out.add("colorScales (" + colorScales.size() + "): " + colorScales);
           out.add("");

           // ---- MESSAGE KEY PROBE ----
           out.add("[Message key probe]");
           String[] keys = {
                   "prefix",
                   "generic.no-permission",
                   "generic.player-only",
                   "hsize.help",
                   "hsize.reload",
                   "hsize.status",
                   "hsize.list",
                   "values.enabled",
                   "values.disabled",
                   "values.yes",
                   "values.no",
                   "cooldown.started",
                   "cooldown.started-sub",
                   "cooldown.refreshed",
                   "cooldown.warning",
                   "console.enabled-header",
                   "console.enabled-title",
                   "console.enabled-pvp",
                   "console.enabled-victim",
                   "console.enabled-attacker",
                   "console.enabled-helmets",
                   "console.enabled-helmet-entry",
                   "console.disabled",
                   "console.scale-supported",
                   "console.scale-missing",
                   "console.scale-fallback-found",
                   "console.scale-fallback-none",
                   "console.config-loaded"
           };
           for (String k : keys) {
               Object v = messages.get(k);
               out.add(k + " = " + (v == null ? "NULL" : v.toString()));
           }
           out.add("");

           // ---- RUNTIME ----
           out.add("[Runtime]");
           int inCd = 0;
           for (Player p : Bukkit.getOnlinePlayers()) {
               if (isInCooldown(p)) inCd++;
           }
           out.add("online players: " + Bukkit.getOnlinePlayers().size());
           out.add("players currently in cooldown: " + inCd);
           out.add("warnedMissingKeys: " + warnedMissingKeys);

           // ---- WRITE FILE ----
           boolean written = false;
           String writeError = null;
           try (FileWriter w = new FileWriter(debugFile, false)) {
               for (String line : out) {
                   w.write(line);
                   w.write(System.lineSeparator());
               }
               written = true;
           } catch (IOException e) {
               writeError = e.getMessage();
           }

           // ---- OUTPUT ----
           if (debugMode) {
               for (String line : out) {
                   sender.sendMessage(ChatColor.GRAY + line);
                   getLogger().info("[DEBUG] " + line);
               }
           }

           if (written) {
               send(sender, "debug.file-written", "file", debugFile.getAbsolutePath());
           } else {
               send(sender, "debug.file-failed", "error",
                       writeError == null ? "unknown" : writeError);
           }
       }

       // ================================================================
       // DAMAGE HANDLING
       // ================================================================

       @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
       public void onEntityDamage(EntityDamageEvent event) {
           if (!pvpCooldownEnabled) return;

           Entity damager = null;
           if (event instanceof EntityDamageByEntityEvent) {
               damager = ((EntityDamageByEntityEvent) event).getDamager();

               if (damager instanceof Projectile) {
                   Projectile proj = (Projectile) damager;
                   if (proj.getShooter() instanceof Entity) {
                       damager = (Entity) proj.getShooter();
                   }
               }
           }

           if (event.getEntity() instanceof Player) {
               Player victim = (Player) event.getEntity();

               if (isWearingTrackedHelmet(victim)) {
                   String triggerKey = null;
                   if (damager instanceof Player) {
                       if (triggerVictimPlayer) triggerKey = T_PLAYER;
                   } else if (damager != null) {
                       if (triggerVictimEntity) triggerKey = T_ENTITY;
                   } else {
                       if (triggerVictimNatural) triggerKey = T_NATURAL;
                   }

                   if (triggerKey != null) {
                       if (debugMode) {
                           getLogger().info("Victim trigger: " + victim.getName()
                                   + " -> " + triggerKey);
                       }
                       startCooldown(victim, triggerKey);
                   }
               }
           }

           if (damager instanceof Player) {
               Player attacker = (Player) damager;
               boolean isVictimPlayer = event.getEntity() instanceof Player;

               if (isWearingTrackedHelmet(attacker)) {
                   String triggerKey = null;
                   if (isVictimPlayer && triggerAttackerPvp) {
                       triggerKey = T_ATTACKER_PVP;
                   } else if (!isVictimPlayer && triggerAttackerMob) {
                       triggerKey = T_ATTACKER_MOB;
                   }

                   if (triggerKey != null) {
                       if (debugMode) {
                           getLogger().info("Attacker trigger: " + attacker.getName()
                                   + " -> " + triggerKey);
                       }
                       startCooldown(attacker, triggerKey);
                   }
               }
           }
       }

       private boolean isWearingTrackedHelmet(Player player) {
           ItemStack helmet = player.getInventory().getHelmet();
           if (helmet == null || !helmet.hasItemMeta()) return false;
           String name = helmet.getItemMeta().getDisplayName();
           return name != null && helmetScales.containsKey(name);
       }

       // ================================================================
       // INVENTORY / PLAYER EVENTS
       // ================================================================

       @EventHandler
       public void onInventoryClose(InventoryCloseEvent event) {
           if (event.getPlayer() instanceof Player player) {
               Bukkit.getScheduler().runTaskLater(this, () -> {
                   if (!isInCooldown(player)) {
                       forceCheckAndApplySize(player);
                   }
               }, 2L);
           }
       }

       @EventHandler
       public void onInventoryClick(InventoryClickEvent event) {
           if (event.getWhoClicked() instanceof Player player) {
               if (event.getSlotType() == InventoryType.SlotType.ARMOR
                       || event.getSlot() == 39
                       || event.getSlot() == 36 || event.getSlot() == 37 || event.getSlot() == 38
                       || event.getSlot() == 40) {
                   Bukkit.getScheduler().runTask(this, () -> {
                       if (!isInCooldown(player)) {
                           forceCheckAndApplySize(player);
                       }
                   });
               }
           }
       }

       @EventHandler
       public void onPlayerItemHeld(PlayerItemHeldEvent event) {
           Player player = event.getPlayer();
           Bukkit.getScheduler().runTask(this, () -> {
               if (!isInCooldown(player)) {
                   forceCheckAndApplySize(player);
               }
           });
       }

       @EventHandler
       public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
           Player player = event.getPlayer();
           Bukkit.getScheduler().runTask(this, () -> {
               if (!isInCooldown(player)) {
                   forceCheckAndApplySize(player);
               }
           });
       }

       @EventHandler
       public void onPlayerJoin(PlayerJoinEvent event) {
           Bukkit.getScheduler().runTaskLater(this, () -> {
               if (!isInCooldown(event.getPlayer())) {
                   forceCheckAndApplySize(event.getPlayer());
               }
           }, 5L);
       }

       // ================================================================
       // SIZE APPLICATION
       // ================================================================

       private void forceCheckAndApplySize(Player player) {
           if (!scaleSupported) return;
           if (isInCooldown(player)) return;

           try {
               ItemStack helmet = player.getInventory().getHelmet();
               AttributeInstance scale = getScaleAttribute(player);

               if (scale == null) return;

               Double targetScale = null;

               if (helmet != null && helmet.hasItemMeta() && helmet.getItemMeta().getDisplayName() != null) {
                   String displayName = helmet.getItemMeta().getDisplayName();
                   if (helmetScales.containsKey(displayName)) {
                       targetScale = helmetScales.get(displayName);
                   }
               }

               double target = (targetScale != null) ? targetScale : 1.0;
               double current = scale.getBaseValue();

               if (Math.abs(current - target) > 0.001) {
                   scale.setBaseValue(target);
                   if (debugMode) {
                       getLogger().info("Set " + player.getName() + " scale to " + target);
                   }
               }
           } catch (Exception e) {
               getLogger().severe("Error: " + e.getMessage());
           }
       }

       private void resetPlayerScale(Player player) {
           try {
               AttributeInstance scale = getScaleAttribute(player);
               if (scale != null) {
                   scale.setBaseValue(1.0);
               }
               isInCooldown.remove(player.getUniqueId());
               cooldownEndTime.remove(player.getUniqueId());
               lastMessageTime.remove(player.getUniqueId());
           } catch (Exception ignored) {
           }
       }
   }