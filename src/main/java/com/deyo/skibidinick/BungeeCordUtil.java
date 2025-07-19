package com.deyo.skibidinick.utils;

import com.deyo.skibidinick.skibidinick;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BungeeCordUtil implements PluginMessageListener {
    
    private final skibidinick plugin;
    private Map<UUID, String> pendingNickRequests = new HashMap<>();
    
    public BungeeCordUtil(skibidinick plugin) {
        this.plugin = plugin;
        setupChannels();
    }
    
    private void setupChannels() {
        // Register plugin messaging channels
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "skibidinick:data");
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "skibidinick:data", this);
    }
    
    /**
     * Check if a nickname is available across all servers
     * @param player The player requesting the check
     * @param nickname The nickname to check
     */
    public void checkNicknameAvailable(Player player, String nickname) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        
        try {
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF("skibidinick:data");
            
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF("CHECK_NICK");
            msgOut.writeUTF(player.getUniqueId().toString());
            msgOut.writeUTF(nickname);
            
            out.writeShort(msgBytes.toByteArray().length);
            out.write(msgBytes.toByteArray());
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to send nickname check: " + e.getMessage());
            return;
        }
        
        pendingNickRequests.put(player.getUniqueId(), nickname);
        player.sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
    }
    
    /**
     * Broadcast that a nickname is now taken
     * @param nickname The nickname that was taken
     */
    public void broadcastNicknameTaken(String nickname) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        
        try {
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF("skibidinick:data");
            
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF("NICK_TAKEN");
            msgOut.writeUTF(nickname);
            
            out.writeShort(msgBytes.toByteArray().length);
            out.write(msgBytes.toByteArray());
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to broadcast nickname taken: " + e.getMessage());
        }
        
        if (plugin.getServer().getOnlinePlayers().size() > 0) {
            plugin.getServer().getOnlinePlayers().iterator().next()
                .sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        }
    }
    
    /**
     * Broadcast that a nickname is now available
     * @param nickname The nickname that was freed
     */
    public void broadcastNicknameFreed(String nickname) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        
        try {
            out.writeUTF("Forward");
            out.writeUTF("ALL");
            out.writeUTF("skibidinick:data");
            
            ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
            DataOutputStream msgOut = new DataOutputStream(msgBytes);
            msgOut.writeUTF("NICK_FREED");
            msgOut.writeUTF(nickname);
            
            out.writeShort(msgBytes.toByteArray().length);
            out.write(msgBytes.toByteArray());
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to broadcast nickname freed: " + e.getMessage());
        }
        
        if (plugin.getServer().getOnlinePlayers().size() > 0) {
            plugin.getServer().getOnlinePlayers().iterator().next()
                .sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        }
    }
    
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("skibidinick:data")) {
            return;
        }
        
        try {
            ByteArrayInputStream stream = new ByteArrayInputStream(message);
            DataInputStream in = new DataInputStream(stream);
            
            String action = in.readUTF();
            
            switch (action) {
                case "CHECK_NICK":
                    handleNicknameCheck(in);
                    break;
                case "NICK_TAKEN":
                    handleNicknameTaken(in);
                    break;
                case "NICK_FREED":
                    handleNicknameFreed(in);
                    break;
                case "NICK_AVAILABLE":
                    handleNicknameAvailable(in);
                    break;
                case "NICK_UNAVAILABLE":
                    handleNicknameUnavailable(in);
                    break;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to handle plugin message: " + e.getMessage());
        }
    }
    
    private void handleNicknameCheck(DataInputStream in) throws IOException {
        String playerUUID = in.readUTF();
        String nickname = in.readUTF();
        
        // Check if nickname is available on this server
        boolean available = plugin.isNameAvailable(nickname);
        
        // Send response back
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(b);
        
        out.writeUTF("Forward");
        out.writeUTF("ALL");
        out.writeUTF("skibidinick:data");
        
        ByteArrayOutputStream msgBytes = new ByteArrayOutputStream();
        DataOutputStream msgOut = new DataOutputStream(msgBytes);
        msgOut.writeUTF(available ? "NICK_AVAILABLE" : "NICK_UNAVAILABLE");
        msgOut.writeUTF(playerUUID);
        msgOut.writeUTF(nickname);
        
        out.writeShort(msgBytes.toByteArray().length);
        out.write(msgBytes.toByteArray());
        
        if (plugin.getServer().getOnlinePlayers().size() > 0) {
            plugin.getServer().getOnlinePlayers().iterator().next()
                .sendPluginMessage(plugin, "BungeeCord", b.toByteArray());
        }
    }
    
    private void handleNicknameTaken(DataInputStream in) throws IOException {
        String nickname = in.readUTF();
        plugin.markNameAsUsed(nickname);
    }
    
    private void handleNicknameFreed(DataInputStream in) throws IOException {
        String nickname = in.readUTF();
        plugin.markNameAsAvailable(nickname);
    }
    
    private void handleNicknameAvailable(DataInputStream in) throws IOException {
        String playerUUID = in.readUTF();
        String nickname = in.readUTF();
        
        UUID uuid = UUID.fromString(playerUUID);
        Player player = plugin.getServer().getPlayer(uuid);
        
        if (player != null && pendingNickRequests.containsKey(uuid) && 
            pendingNickRequests.get(uuid).equals(nickname)) {
            
            pendingNickRequests.remove(uuid);
            plugin.setNicknameForPlayer(player, nickname);
        }
    }
    
    private void handleNicknameUnavailable(DataInputStream in) throws IOException {
        String playerUUID = in.readUTF();
        String nickname = in.readUTF();
        
        UUID uuid = UUID.fromString(playerUUID);
        Player player = plugin.getServer().getPlayer(uuid);
        
        if (player != null && pendingNickRequests.containsKey(uuid) && 
            pendingNickRequests.get(uuid).equals(nickname)) {
            
            pendingNickRequests.remove(uuid);
            player.sendMessage(plugin.getMessage("name-unavailable"));
        }
    }
    
    public void cleanup() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin);
        pendingNickRequests.clear();
    }
}
