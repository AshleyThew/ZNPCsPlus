package io.github.znetworkw.znpcservers.nms;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import io.github.znetworkw.znpcservers.reflection.Reflections;
import io.github.znetworkw.znpcservers.npc.ItemSlot;
import io.github.znetworkw.znpcservers.npc.NPC;
import io.github.znetworkw.znpcservers.utility.Utils;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.util.Map;

public class NMSV8 implements NMS {
    public int version() {
        return 8;
    }

    public Object createPlayer(Object nmsWorld, GameProfile gameProfile) throws ReflectiveOperationException {
        Constructor<?> constructor = (Utils.BUKKIT_VERSION > 13) ? Reflections.PLAYER_INTERACT_MANAGER_NEW_CONSTRUCTOR.get() : Reflections.PLAYER_INTERACT_MANAGER_OLD_CONSTRUCTOR.get();
        return Reflections.PLAYER_CONSTRUCTOR_OLD.get().newInstance(Reflections.GET_SERVER_METHOD
                .get().invoke(Bukkit.getServer()), nmsWorld, gameProfile, constructor.newInstance(nmsWorld));
    }

    public Object createSpawnPacket(Object nmsEntity, boolean isPlayer) throws ReflectiveOperationException {
        return isPlayer ? Reflections.PACKET_PLAY_OUT_NAMED_ENTITY_CONSTRUCTOR.get().newInstance(nmsEntity) : Reflections.PACKET_PLAY_OUT_SPAWN_ENTITY_CONSTRUCTOR.get().newInstance(nmsEntity);
    }

    public Object createEntityEquipmentPacket(int entityId, ItemSlot itemSlot, ItemStack itemStack) throws ReflectiveOperationException {
        return Reflections.PACKET_PLAY_OUT_ENTITY_EQUIPMENT_CONSTRUCTOR_OLD.get().newInstance(entityId,
                itemSlot.getSlotOld(), Reflections.AS_NMS_COPY_METHOD.get().invoke(Reflections.CRAFT_ITEM_STACK_CLASS, itemStack));
    }

    public Object createMetadataPacket(int entityId, Object nmsEntity) throws ReflectiveOperationException {
        Object dataWatcher = Reflections.GET_DATA_WATCHER_METHOD.get().invoke(nmsEntity);
        try {
            return Reflections.PACKET_PLAY_OUT_ENTITY_META_DATA_CONSTRUCTOR.get().newInstance(entityId, dataWatcher, true);
        } catch (Exception e2) {
            return Reflections.PACKET_PLAY_OUT_ENTITY_META_DATA_CONSTRUCTOR_V1.get().newInstance(entityId, Reflections.GET_DATAWATCHER_B_LIST.get().invoke(dataWatcher));
        }
    }

    public Object createArmorStandSpawnPacket(Object armorStand) throws ReflectiveOperationException {
        return Reflections.PACKET_PLAY_OUT_SPAWN_ENTITY_CONSTRUCTOR.get().newInstance(armorStand);
    }

    public ImmutableList<Object> createEquipmentPacket(NPC npc) throws ReflectiveOperationException {
        ImmutableList.Builder<Object> builder = ImmutableList.builder();
        for (Map.Entry<ItemSlot, ItemStack> stackEntry : npc.getNpcPojo().getNpcEquip().entrySet()) {
            builder.add(Reflections.PACKET_PLAY_OUT_ENTITY_EQUIPMENT_CONSTRUCTOR_OLD.get().newInstance(npc.getEntityID(), stackEntry.getKey().getSlotOld(),
                    createEntityEquipmentPacket(npc.getEntityID(), stackEntry.getKey(), stackEntry.getValue())));
        }
        return builder.build();
    }

    public void updateGlow(NPC npc, Object packet) throws ReflectiveOperationException {
        throw new IllegalStateException("Glow color is not supported for 1.8 version.");
    }

    public boolean allowsGlowColor() {
        return false;
    }
}
