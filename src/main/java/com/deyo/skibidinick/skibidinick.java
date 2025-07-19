package com.deyo.skibidinick;

import dev.iiahmed.disguise.*;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import com.github.javafaker.Faker;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import java.util.Arrays;
import com.deyo.skibidinick.utils.BungeeCordUtil;

public class skibidinick extends JavaPlugin implements Listener, CommandExecutor {

    private final DisguiseProvider provider = DisguiseProvider.getProvider();
    private Pattern namePattern;
    private Map<UUID, Boolean> awaitingCustomNick = new HashMap<>();
    private Faker faker;
    private Set<String> usedNames;
    private File databaseFile;
    private YamlConfiguration database;
    private YamlConfiguration messages;
    private Map<String, Player> nickedPlayers = new HashMap<>();
    private BungeeCordUtil bungeeCordUtil;
    private boolean bungeeCordMode;

    @Override
    public void onEnable() {
        getLogger().info("Skibidinick V1 nick plugin by deyo has been enabled!");
        
        // Check if BungeeCord mode is enabled
        bungeeCordMode = getConfig().getBoolean("bungeecord.enabled", false);
        
        boolean allowEntities = getConfig().getBoolean("allow-entity-disguises", true);
        DisguiseManager.initialize(this, allowEntities);
        provider.allowOverrideChat(true);
        provider.setNameLength(16);
        this.namePattern = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");
        provider.setNamePattern(this.namePattern);

        getCommand("nick").setExecutor(this);
        getCommand("unnick").setExecutor(this);
        getCommand("realname").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        
        faker = new Faker();

        this.databaseFile = new File(getDataFolder(), "player_names.yml");
        this.database = YamlConfiguration.loadConfiguration(databaseFile);
        this.usedNames = new HashSet<>(database.getStringList("used_names"));
        loadConfig();
        loadMessages();
        
        // Initialize BungeeCord support if enabled
        if (bungeeCordMode) {
            bungeeCordUtil = new BungeeCordUtil(this);
            getLogger().info("BungeeCord mode enabled - nicknames will be synchronized across servers!");
        }
    }

    private void loadConfig() {
        saveDefaultConfig();
        reloadConfig();
    }

    private void loadMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String getMessage(String key) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(key, "Message not found: " + key));
    }

    public String getMessage(String key, Map<String, String> replacements) {
        String message = ChatColor.translateAlternateColorCodes('&', messages.getString(key, "Message not found: " + key));
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String realName = player.getName().toLowerCase();
        if (!usedNames.contains(realName)) {
            usedNames.add(realName);
            saveDatabase();
        }

        Player nickedPlayer = nickedPlayers.get(realName);
        if (nickedPlayer != null) {
            String randomNick = generateRandomNick();
            setNicknameAndSkin(nickedPlayer, randomNick);
            
            Map<String, String> replacements = new HashMap<>();
            replacements.put("oldnick", realName);
            replacements.put("newnick", randomNick);
            nickedPlayer.sendMessage(getMessage("nick-changed-on-join", replacements));
        }
    }

    private void saveDatabase() {
        database.set("used_names", new ArrayList<>(usedNames));
        try {
            database.save(databaseFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save player names database: " + e.getMessage());
        }
    }

    public boolean isNameAvailable(String name) {
        return !usedNames.contains(name.toLowerCase());
    }
    
    public void markNameAsUsed(String name) {
        usedNames.add(name.toLowerCase());
        saveDatabase();
    }
    
    public void markNameAsAvailable(String name) {
        usedNames.remove(name.toLowerCase());
        saveDatabase();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getMessage("player-only-command"));
            return true;
        }

        Player player = (Player) sender;

        switch (command.getName().toLowerCase()) {
            case "nick":
                if (!player.hasPermission("skibidinick.nick")) {
                    player.sendMessage(getMessage("no-permission"));
                    return true;
                }
                openNickGUI(player);
                return true;
            case "unnick":
                if (!player.hasPermission("skibidinick.unnick")) {
                    player.sendMessage(getMessage("no-permission"));
                    return true;
                }
                return handleUnnickCommand(player);
            case "realname":
                if (!player.hasPermission("skibidinick.realname")) {
                    player.sendMessage(getMessage("no-permission"));
                    return true;
                }
                return handleRealnameCommand(player, args);
            default:
                return false;
        }
    }

    private boolean handleNickCommand(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(getMessage("nick-usage"));
            return true;
        }

        String newName = args[0];

        if (!this.namePattern.matcher(newName).matches()) {
            player.sendMessage(getMessage("invalid-nickname-format"));
            return true;
        }

        // Use BungeeCord checking if enabled
        if (bungeeCordMode && bungeeCordUtil != null) {
            bungeeCordUtil.checkNicknameAvailable(player, newName);
            player.sendMessage(getMessage("checking-nickname-availability"));
            return true;
        }

        // Local check only
        if (!isNameAvailable(newName)) {
            player.sendMessage(getMessage("name-unavailable"));
            return true;
        }

        setNicknameForPlayer(player, newName);
        return true;
    }
    
    public void setNicknameForPlayer(Player player, String newName) {
        Disguise disguise = Disguise.builder()
                .setName(newName)
                .build();

        DisguiseResponse response = provider.disguise(player, disguise);
        switch (response) {
            case SUCCESS:
                Map<String, String> replacements = new HashMap<>();
                replacements.put("nickname", newName);
                player.sendMessage(getMessage("nick-set", replacements));
                nickedPlayers.put(newName.toLowerCase(), player);
                
                // Mark as used locally
                markNameAsUsed(newName);
                
                // Broadcast to other servers if BungeeCord is enabled
                if (bungeeCordMode && bungeeCordUtil != null) {
                    bungeeCordUtil.broadcastNicknameTaken(newName);
                }
                break;
            case FAIL_NAME_ALREADY_ONLINE:
                player.sendMessage(getMessage("name-already-online"));
                break;
            case FAIL_NAME_INVALID:
                player.sendMessage(getMessage("invalid-nickname-format"));
                break;
            
            default:
                Map<String, String> errorReplacements = new HashMap<>();
                errorReplacements.put("reason", response.toString());
                player.sendMessage(getMessage("disguise-unsuccessful", errorReplacements));
        }
    }

    private boolean handleUnnickCommand(Player player) {
        String currentNick = null;
        if (provider.isDisguised(player)) {
            currentNick = provider.getInfo(player).getName();
        }
        
        UndisguiseResponse response = provider.undisguise(player);
        if (response == UndisguiseResponse.SUCCESS) {
            player.setDisplayName(player.getName());
            player.sendMessage(getMessage("nick-removed"));
            
            if (currentNick != null) {
                nickedPlayers.remove(currentNick.toLowerCase());
                
                // Mark as available locally
                markNameAsAvailable(currentNick);
                
                // Broadcast to other servers if BungeeCord is enabled
                if (bungeeCordMode && bungeeCordUtil != null) {
                    bungeeCordUtil.broadcastNicknameFreed(currentNick);
                }
            }
        } else if (response == UndisguiseResponse.FAIL_ALREADY_UNDISGUISED) {
            player.sendMessage(getMessage("already-undisguised"));
        } else {
            Map<String, String> errorReplacements = new HashMap<>();
            errorReplacements.put("reason", response.toString());
            player.sendMessage(getMessage("failed-remove-nickname", errorReplacements));
        }
        return true;
    }

    private boolean handleRealnameCommand(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(getMessage("realname-usage"));
            return true;
        }

        String nickname = args[0];
        Player target = Bukkit.getPlayer(nickname);
        if (target == null) {
            player.sendMessage(getMessage("realname-not-found"));
            return true;
        }

        String realName = provider.getInfo(target).getName();
        Map<String, String> replacements = new HashMap<>();
        replacements.put("nickname", nickname);
        replacements.put("realname", realName);
        player.sendMessage(getMessage("realname-result", replacements));
        return true;
    }

    private void openNickGUI(Player player) {
        int size = getConfig().getInt("gui.size", 27);
        Inventory inv = Bukkit.createInventory(null, size, getMessage("gui.title"));

        ItemStack customNick = createGuiItem(Material.NAME_TAG, getMessage("gui.custom-nick"), getMessage("gui.custom-nick-lore"));
        ItemStack randomNick = createGuiItem(Material.PAPER, getMessage("gui.random-nick"), getMessage("gui.random-nick-lore"));
        ItemStack removeNick = createGuiItem(Material.BARRIER, getMessage("gui.remove-nick"), getMessage("gui.remove-nick-lore"));

        inv.setItem(getConfig().getInt("gui.slots.custom-nick", 11), customNick);
        inv.setItem(getConfig().getInt("gui.slots.random-nick", 13), randomNick);
        inv.setItem(getConfig().getInt("gui.slots.remove-nick", 15), removeNick);

        player.openInventory(inv);
    }

    private ItemStack createGuiItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore.split("\n")));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(getMessage("gui.title"))) return;
        event.setCancelled(true);
        
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getItemMeta().getDisplayName().equals(getMessage("gui.custom-nick"))) {
            if (!player.hasPermission("skibidinick.nick")) {
                player.sendMessage(getMessage("no-permission"));
                player.closeInventory();
                return;
            }
            player.closeInventory();
            awaitingCustomNick.put(player.getUniqueId(), true);
            player.sendMessage(getMessage("enter-custom-nick"));
        } else if (clickedItem.getItemMeta().getDisplayName().equals(getMessage("gui.random-nick"))) {
            if (!player.hasPermission("skibidinick.nick")) {
                player.sendMessage(getMessage("no-permission"));
                player.closeInventory();
                return;
            }
            player.closeInventory();
            String randomNick = generateRandomNick();
            setNicknameAndSkin(player, randomNick);
        } else if (clickedItem.getItemMeta().getDisplayName().equals(getMessage("gui.remove-nick"))) {
            if (!player.hasPermission("skibidinick.unnick")) {
                player.sendMessage(getMessage("no-permission"));
                player.closeInventory();
                return;
            }
            player.closeInventory();
            handleUnnickCommand(player);
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (awaitingCustomNick.remove(player.getUniqueId()) != null) {
            event.setCancelled(true);
            final String nickname = event.getMessage();

            Bukkit.getScheduler().runTask(this, () -> {
                if (bungeeCordMode && bungeeCordUtil != null) {
                    bungeeCordUtil.checkNicknameAvailable(player, nickname);
                    player.sendMessage(getMessage("checking-nickname-availability"));
                } else {
                    setNicknameAndSkin(player, nickname);
                }
            });
        }
    }

    private void setNicknameAndSkin(Player player, String nickname) {
        if (!this.namePattern.matcher(nickname).matches()) {
            player.sendMessage(getMessage("invalid-nickname-format"));
            return;
        }

        if (!isNameAvailable(nickname)) {
            player.sendMessage(getMessage("name-unavailable"));
            return;
        }

        Disguise disguise = Disguise.builder()
                .setName(nickname)
                .build();
        DisguiseResponse response = provider.disguise(player, disguise);
        if (response == DisguiseResponse.SUCCESS) {
            player.setDisplayName(nickname);
            Map<String, String> replacements = new HashMap<>();
            replacements.put("nickname", nickname);
            player.sendMessage(getMessage("nick-set", replacements));
            nickedPlayers.put(nickname.toLowerCase(), player);
            
            // Mark as used locally
            markNameAsUsed(nickname);
            
            // Broadcast to other servers if BungeeCord is enabled
            if (bungeeCordMode && bungeeCordUtil != null) {
                bungeeCordUtil.broadcastNicknameTaken(nickname);
            }
        } else {
            Map<String, String> errorReplacements = new HashMap<>();
            errorReplacements.put("reason", response.toString());
            player.sendMessage(getMessage("disguise-unsuccessful", errorReplacements));
        }
    }

    private String generateRandomNick() {
        String randomNick;
        int attempts = 0;
        do {
            randomNick = faker.superhero().name().replaceAll("\\s+", "");
            attempts++;
            if (attempts > 50) {
                // Fallback to ensure we don't get stuck in an infinite loop
                randomNick = "Player" + (int)(Math.random() * 10000);
                break;
            }
        } while (randomNick.length() > 16 || !isNameAvailable(randomNick));
        return randomNick;
    }

    @Override
    public void onDisable() {
        getLogger().info("Skibidinick V1 nick plugin by deyo has been disabled!");
        
        // Cleanup BungeeCord utilities
        if (bungeeCordUtil != null) {
            bungeeCordUtil.cleanup();
        }
        
        // Save database one final time
        saveDatabase();
    }
}
