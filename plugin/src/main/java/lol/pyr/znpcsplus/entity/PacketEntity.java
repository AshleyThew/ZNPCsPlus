package lol.pyr.znpcsplus.entity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import lol.pyr.znpcsplus.api.entity.EntityProperty;
import lol.pyr.znpcsplus.api.entity.PropertyHolder;
import lol.pyr.znpcsplus.packets.PacketFactory;
import lol.pyr.znpcsplus.reflection.Reflections;
import lol.pyr.znpcsplus.util.NpcLocation;
import lol.pyr.znpcsplus.util.Viewable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;

public class PacketEntity implements PropertyHolder {
    private final PacketFactory packetFactory;

    private final PropertyHolder properties;
    private final Viewable viewable;
    private final int entityId;
    private final UUID uuid;

    private final EntityType type;
    private NpcLocation location;

    private PacketEntity vehicle;

    public PacketEntity(PacketFactory packetFactory, PropertyHolder properties, Viewable viewable, EntityType type, NpcLocation location) {
        this.packetFactory = packetFactory;
        this.properties = properties;
        this.viewable = viewable;
        this.entityId = reserveEntityID();
        this.uuid = UUID.randomUUID();
        this.type = type;
        this.location = location;
    }

    public int getEntityId() {
        return entityId;
    }

    public NpcLocation getLocation() {
        return location;
    }

    public UUID getUuid() {
        return uuid;
    }

    public EntityType getType() {
        return type;
    }

    public void setLocation(NpcLocation location) {
        this.location = location;
        if (vehicle != null) {
            vehicle.setLocation(location.withY(location.getY() - 0.9));
            return;
        }
        for (Player viewer : viewable.getViewers()) packetFactory.teleportEntity(viewer, this);
    }

    public void spawn(Player player) {
        if (type == EntityTypes.PLAYER) packetFactory.spawnPlayer(player, this, properties);
        else packetFactory.spawnEntity(player, this, properties);
        if (vehicle != null) {
            vehicle.spawn(player);
            packetFactory.setPassenger(player, vehicle, this);
        }
    }

    public void setHeadRotation(Player player, float yaw, float pitch) {
        packetFactory.sendHeadRotation(player, this, yaw, pitch);
    }

    public PacketEntity getVehicle() {
        return vehicle;
    }

    public Viewable getViewable() {
        return viewable;
    }

    public void setVehicle(PacketEntity vehicle) {
        // remove old vehicle
        if (this.vehicle != null) {
            for (Player player : viewable.getViewers()) {
                packetFactory.setPassenger(player, this.vehicle, null);
                this.vehicle.despawn(player);
                packetFactory.teleportEntity(player, this);
            }
        }

        this.vehicle = vehicle;
        if (this.vehicle == null) return;

        vehicle.setLocation(location.withY(location.getY() - 0.9));
        for (Player player : viewable.getViewers()) {
            vehicle.spawn(player);
            packetFactory.setPassenger(player, vehicle, this);
        }
    }

    public void despawn(Player player) {
        packetFactory.destroyEntity(player, this, properties);
        if (vehicle != null) vehicle.despawn(player);
    }

    public void refreshMeta(Player player) {
        packetFactory.sendAllMetadata(player, this, properties);
    }

    public void swingHand(Player player, boolean offhand) {
        packetFactory.sendHandSwing(player, this, offhand);
    }

    private static int reserveEntityID() {
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_14)) {
            return Reflections.ATOMIC_ENTITY_ID_FIELD.get().incrementAndGet();
        } else {
            int id = Reflections.ENTITY_ID_MODIFIER.get();
            Reflections.ENTITY_ID_MODIFIER.set(id + 1);
            return id;
        }
    }

    @Override
    public <T> T getProperty(EntityProperty<T> key) {
        return properties.getProperty(key);
    }

    @Override
    public boolean hasProperty(EntityProperty<?> key) {
        return properties.hasProperty(key);
    }

    @Override
    public <T> void setProperty(EntityProperty<T> key, T value) {
        properties.setProperty(key, value);
    }

    @Override
    public void setItemProperty(EntityProperty<?> key, ItemStack value) {
        properties.setItemProperty(key, value);
    }

    @Override
    public ItemStack getItemProperty(EntityProperty<?> key) {
        return properties.getItemProperty(key);
    }

    @Override
    public Set<EntityProperty<?>> getAppliedProperties() {
        return properties.getAppliedProperties();
    }
}
