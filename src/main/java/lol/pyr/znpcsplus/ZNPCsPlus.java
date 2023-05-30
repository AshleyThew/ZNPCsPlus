package lol.pyr.znpcsplus;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.github.znetworkw.znpcservers.commands.list.DefaultCommand;
import io.github.znetworkw.znpcservers.configuration.Configuration;
import io.github.znetworkw.znpcservers.configuration.ConfigurationConstants;
import io.github.znetworkw.znpcservers.listeners.InventoryListener;
import io.github.znetworkw.znpcservers.listeners.PlayerListener;
import io.github.znetworkw.znpcservers.npc.NPC;
import io.github.znetworkw.znpcservers.npc.NPCModel;
import io.github.znetworkw.znpcservers.npc.NPCPath;
import io.github.znetworkw.znpcservers.npc.NPCType;
import io.github.znetworkw.znpcservers.npc.interaction.InteractionPacketListener;
import io.github.znetworkw.znpcservers.npc.task.NPCPositionTask;
import io.github.znetworkw.znpcservers.npc.task.NPCSaveTask;
import io.github.znetworkw.znpcservers.npc.task.NPCVisibilityTask;
import io.github.znetworkw.znpcservers.user.ZUser;
import io.github.znetworkw.znpcservers.utility.BungeeUtils;
import io.github.znetworkw.znpcservers.utility.SchedulerUtils;
import io.github.znetworkw.znpcservers.utility.itemstack.ItemStackSerializer;
import io.github.znetworkw.znpcservers.utility.location.ZLocation;
import lol.pyr.znpcsplus.updater.UpdateChecker;
import lol.pyr.znpcsplus.updater.UpdateNotificationListener;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.apache.commons.io.FileUtils;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;

public class ZNPCsPlus extends JavaPlugin {
    public static Logger LOGGER;
    public static File PLUGIN_FOLDER;
    public static File PATH_FOLDER;
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ZLocation.class, ZLocation.SERIALIZER)
            .registerTypeHierarchyAdapter(ItemStack.class, new ItemStackSerializer())
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final int PLUGIN_ID = 18244;
    public static SchedulerUtils SCHEDULER;
    public static BungeeUtils BUNGEE_UTILS;
    public static BukkitAudiences ADVENTURE;

    private boolean enabled = false;

    public static NPC createNPC(int id, NPCType npcType, Location location, String name) {
        NPC find = NPC.find(id);
        if (find != null)
            return find;
        NPCModel pojo = (new NPCModel(id)).withHologramLines(Collections.singletonList(name)).withHologramHeight(npcType.getHoloHeight()).withLocation(new ZLocation(location)).withNpcType(npcType);
        ConfigurationConstants.NPC_LIST.add(pojo);
        return new NPC(pojo, true);
    }

    public static void deleteNPC(int npcID) {
        NPC npc = NPC.find(npcID);
        if (npc == null) throw new IllegalStateException("can't find npc:  " + npcID);
        NPC.unregister(npcID);
        ConfigurationConstants.NPC_LIST.remove(npc.getNpcPojo());
    }

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().checkForUpdates(false);
        PacketEvents.getAPI().load();
        LOGGER = getLogger();
        PLUGIN_FOLDER = getDataFolder();
        PATH_FOLDER = new File(PLUGIN_FOLDER, "paths");
    }

    private void log(String str) {
        Bukkit.getConsoleSender().sendMessage(str);
    }

    @Override
    public void onEnable() {
        log(ChatColor.YELLOW + "  ___       __   __  __");
        log(ChatColor.YELLOW + "   _/ |\\ | |__) |   (__` " + ChatColor.GOLD + "__|__   " + ChatColor.YELLOW + getDescription().getName() + " " + ChatColor.GOLD + "v" + getDescription().getVersion());
        log(ChatColor.YELLOW + "  /__ | \\| |    |__ .__) " + ChatColor.GOLD + "  |     " + ChatColor.GRAY + "Maintained with " + ChatColor.RED + "\u2764 " + ChatColor.GRAY + " by Pyr#6969");
        log("");

        if (Bukkit.getPluginManager().isPluginEnabled("ServersNPC")) {
            log(ChatColor.DARK_RED + " * Detected old version of ZNPCs! Disabling the plugin.");
            log("");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        long before = System.currentTimeMillis();

        File oldFolder = new File(PLUGIN_FOLDER.getParent(), "ServersNPC");
        if (!PLUGIN_FOLDER.exists() && oldFolder.exists()) {
            log(ChatColor.WHITE + " * Converting old ZNPCs files...");
            try {
                FileUtils.moveDirectory(oldFolder, PLUGIN_FOLDER);
            } catch (IOException e) {
                log(ChatColor.RED + " * Failed to convert old ZNPCs files" + (e.getMessage() == null ? "" : " due to " + e.getMessage()));
            }
        }

        log(ChatColor.WHITE + " * Initializing Adventure...");
        ADVENTURE = BukkitAudiences.create(this);

        log(ChatColor.WHITE + " * Initializing PacketEvents...");
        PacketEvents.getAPI().getEventManager().registerListener(new InteractionPacketListener(), PacketListenerPriority.MONITOR);
        PacketEvents.getAPI().init();

        PLUGIN_FOLDER.mkdirs();
        PATH_FOLDER.mkdirs();

        log(ChatColor.WHITE + " * Loading paths...");
        loadAllPaths();

        log(ChatColor.WHITE + " * Registering components...");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        new Metrics(this, PLUGIN_ID);
        new DefaultCommand();
        SCHEDULER = new SchedulerUtils(this);
        BUNGEE_UTILS = new BungeeUtils(this);
        Bukkit.getOnlinePlayers().forEach(ZUser::find);

        log(ChatColor.WHITE + " * Starting tasks...");
        new NPCPositionTask(this);
        new NPCVisibilityTask(this);
        new NPCSaveTask(this, ConfigurationConstants.SAVE_DELAY);
        new PlayerListener(this);
        new InventoryListener(this);
        if (ConfigurationConstants.CHECK_FOR_UPDATES) new UpdateNotificationListener(this, new UpdateChecker(this));

        enabled = true;
        log(ChatColor.WHITE + " * Loading complete! (" + (System.currentTimeMillis() - before) + "ms)");
        log("");
    }

    @Override
    public void onDisable() {
        if (!enabled) return;
        Configuration.SAVE_CONFIGURATIONS.forEach(Configuration::save);
        Bukkit.getOnlinePlayers().forEach(ZUser::unregister);
        ADVENTURE.close();
        ADVENTURE = null;
    }

    public void loadAllPaths() {
        File[] files = PATH_FOLDER.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.getName().endsWith(".path")) continue;
            NPCPath.AbstractTypeWriter abstractTypeWriter = NPCPath.AbstractTypeWriter.forFile(file, NPCPath.AbstractTypeWriter.TypeWriter.MOVEMENT);
            abstractTypeWriter.load();
        }
    }
}
