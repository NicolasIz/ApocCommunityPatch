package com.arkcronist.content.bukkit.vehicle;

import com.arkcronist.content.bukkit.item.CustomItem;
import com.arkcronist.content.bukkit.item.ItemFactory;
import com.arkcronist.content.bukkit.item.ItemRegistry;
import com.arkcronist.content.bukkit.protection.Interaction;
import com.arkcronist.content.bukkit.protection.Protection;
import com.arkcronist.content.core.ballistics.Aabb;
import com.arkcronist.content.core.storage.BlockKey;
import com.arkcronist.content.core.storage.DatabaseManager;
import com.arkcronist.content.core.storage.StoredVehicle;
import com.arkcronist.content.core.vehicle.VehicleDefinition;
import com.arkcronist.content.core.vehicle.VehicleDefinition.Offset;
import com.arkcronist.content.core.vehicle.VehiclePhysics;
import com.arkcronist.content.core.vehicle.VehiclePhysics.Body;
import com.arkcronist.content.core.vehicle.VehiclePhysics.Input;
import com.arkcronist.content.core.vehicle.VehiclePhysics.State;
import com.arkcronist.content.core.vehicle.VehiclePhysics.Terrain;
import com.arkcronist.content.core.vehicle.VehiclePose;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 4.0: vehicles in the world - placed from their item, driven, parked, picked up again.
 *
 * <p><b>What a vehicle is made of.</b> An item display holding nothing sits where the driver
 * does: the vehicle's root, which the driver rides. The displays it is drawn with - its body, its
 * wheels - ride the root too, so each client carries them along with it, and they are placed and
 * turned by their transformation alone. Each further seat is an empty display of its own, and an
 * interaction entity the size of its footprint is what players click. None of them is saved with
 * the chunk: the {@code vehicles} table is, and puts the vehicle back as its chunk loads.</p>
 *
 * <p><b>Threads.</b> Each tick the main thread reads what it alone may - the driver's keys, the
 * collision boxes of the blocks within one step's reach - and hands them to
 * {@code ArkContent-Physics}, which works out the next {@link State} and every display's
 * transformation with {@link VehiclePhysics} and {@link VehiclePose}. The tick after, the main
 * thread moves the root and the seats there and sets the transformations that changed: moving an
 * entity is the server's to do, on its own thread. A parked vehicle no one sits in costs nothing.</p>
 */
public final class VehicleService {

    static final double STEP_SECONDS = 0.05;
    /** How often a moving vehicle's place is written, besides when it stops. */
    private static final long SAVE_EVERY_MILLIS = 10_000;
    private static final TeleportFlag[] KEEP_RIDERS = keepRiders();
    public static final String ADMIN = "arkcontent.vehicles.admin";

    private final Plugin plugin;
    private final ItemRegistry items;
    private final ItemFactory factory;
    private final DatabaseManager database;
    private final Protection protection;
    private final ScheduledExecutorService physics;
    private final Logger logger;
    private final NamespacedKey vehicleTag;

    /** Spawned, by id. Main thread. */
    private final Map<UUID, Live> live = new HashMap<>();
    /** In the table but not spawned - its chunk is not loaded - by id. Main thread. */
    private final Map<UUID, StoredVehicle> parked = new HashMap<>();
    /** Each entity of a spawned vehicle, to its vehicle's id. Main thread. */
    private final Map<UUID, UUID> byEntity = new HashMap<>();

    private final AtomicLong physicsNanos = new AtomicLong();
    private final AtomicLong physicsSteps = new AtomicLong();
    private long mainNanos;
    private long mainTicks;
    private @Nullable BukkitTask ticker;

    public VehicleService(Plugin plugin, ItemRegistry items, ItemFactory factory, DatabaseManager database,
                          Protection protection, ScheduledExecutorService physics) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.database = database;
        this.protection = protection;
        this.physics = physics;
        this.logger = plugin.getLogger();
        this.vehicleTag = com.arkcronist.content.bukkit.Keys.of("vehicle");
    }

    /**
     * The flag that keeps riders on through a teleport - needed up to 1.21.9; the default since
     * 1.21.10, where it is deprecated for removal. A server without it keeps them anyway.
     */
    @SuppressWarnings("removal")
    private static TeleportFlag[] keepRiders() {
        try {
            return new TeleportFlag[]{TeleportFlag.EntityState.RETAIN_PASSENGERS};
        } catch (NoClassDefFoundError | NoSuchFieldError gone) {
            return new TeleportFlag[0];
        }
    }

    /** A spawned vehicle. Main thread, but for {@link #result}, which the physics thread leaves. */
    final class Live {
        final UUID id;
        final String itemId;
        final @Nullable UUID owner;
        final World world;
        VehicleDefinition definition;
        ItemDisplay root;
        org.bukkit.entity.Interaction hitbox;
        /** The seats after the driver's. */
        final List<ItemDisplay> seats = new ArrayList<>();
        /** One for each of its definition's parts; null for a part with no item to draw. */
        final List<ItemDisplay> parts = new ArrayList<>();
        final List<VehiclePose.Transform> shown = new ArrayList<>();
        State state;
        volatile @Nullable Result result;
        boolean stepping;
        boolean dirty;
        long savedAt = System.currentTimeMillis();

        Live(UUID id, String itemId, @Nullable UUID owner, World world, VehicleDefinition definition, State state) {
            this.id = id;
            this.itemId = itemId;
            this.owner = owner;
            this.world = world;
            this.definition = definition;
            this.state = state;
        }

        StoredVehicle row() {
            return new StoredVehicle(id, itemId, world.getUID(), state.x(), state.y(), state.z(), (float) state.yaw(),
                    owner);
        }
    }

    /** What one physics step worked out: the vehicle's next state, and how each display is posed there. */
    private record Result(State state, List<VehiclePose.Transform> poses) {
    }

    // ---------------------------------------------------------------- lifecycle

    /** Reads every vehicle from the table, spawns those in loaded chunks and starts ticking. */
    public void start() {
        database.vehicles().thenAcceptAsync(rows -> {
            for (StoredVehicle row : rows) {
                parked.put(row.id(), row);
            }
            for (World world : plugin.getServer().getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    chunkLoaded(chunk);
                }
            }
            if (!rows.isEmpty()) {
                logger.info(rows.size() + " vehicle(s) in the world, " + live.size() + " in loaded chunks.");
            }
        }, task -> plugin.getServer().getScheduler().runTask(plugin, task)).exceptionally(error -> {
            logger.log(Level.WARNING, "Could not read the vehicles; none are put back this time", error);
            return null;
        });
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    /** Every vehicle's place is queued for writing, riders are let off and its entities removed. */
    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        for (Live vehicle : List.copyOf(live.values())) {
            database.saveVehicle(vehicle.row());
            despawn(vehicle);
        }
        live.clear();
        byEntity.clear();
    }

    /** A rebuild: each spawned vehicle takes its item's new definition; one no longer a vehicle is parked. */
    public void contentReloaded() {
        for (Live vehicle : List.copyOf(live.values())) {
            VehicleDefinition now = items.get(vehicle.itemId).map(item -> item.definition().vehicle()).orElse(null);
            if (now == null) {
                logger.warning("Vehicle " + vehicle.id + ": " + vehicle.itemId + " is no longer a vehicle - it is"
                        + " left parked until it is again.");
                park(vehicle);
            } else if (!now.equals(vehicle.definition)) {
                StoredVehicle row = vehicle.row();
                despawn(vehicle);
                live.remove(vehicle.id);
                spawn(row, now);
            }
        }
    }

    // ---------------------------------------------------------------- chunks

    public void chunkLoaded(Chunk chunk) {
        UUID world = chunk.getWorld().getUID();
        long key = BlockKey.pack(chunk.getX(), 0, chunk.getZ());
        for (StoredVehicle row : List.copyOf(parked.values())) {
            if (row.world().equals(world) && row.chunk() == key) {
                Optional<VehicleDefinition> definition = items.get(row.itemId()).map(item -> item.definition().vehicle());
                if (definition.isPresent()) {
                    parked.remove(row.id());
                    spawn(row, definition.get());
                }
            }
        }
    }

    public void chunkUnloading(Chunk chunk) {
        long key = BlockKey.pack(chunk.getX(), 0, chunk.getZ());
        for (Live vehicle : List.copyOf(live.values())) {
            if (vehicle.world.equals(chunk.getWorld()) && vehicle.row().chunk() == key) {
                park(vehicle);
            }
        }
    }

    /** Saved and despawned, kept as a row until its chunk loads again. */
    private void park(Live vehicle) {
        StoredVehicle row = vehicle.row();
        database.saveVehicle(row);
        despawn(vehicle);
        live.remove(vehicle.id);
        parked.put(row.id(), row);
    }

    // ---------------------------------------------------------------- players

    /**
     * Puts a vehicle down where {@code player} clicked, facing the way they look: on top of the
     * block clicked from above, else in front of the face clicked.
     *
     * @return whether it was put down - and the item is to be taken
     */
    public boolean place(Player player, CustomItem item, Block clicked, org.bukkit.block.BlockFace face) {
        VehicleDefinition definition = item.definition().vehicle();
        if (definition == null) {
            return false;
        }
        Block target = clicked.getRelative(face);
        if (!protection.check(player, target, Interaction.PLACE)) {
            return false;
        }
        double y = target.getY();
        if (face == org.bukkit.block.BlockFace.UP) {
            BoundingBox top = clicked.getCollisionShape().getBoundingBoxes().stream()
                    .reduce(BoundingBox::union).orElse(null);
            y = clicked.getY() + (top == null ? 0 : top.getMaxY());
        }
        State state = State.parked(target.getX() + 0.5, y, target.getZ() + 0.5, player.getLocation().getYaw());
        Aabb footprint = VehiclePhysics.footprint(definition, state.x(), state.y() + 0.01, state.z(), state.yaw());
        boolean crowded = collides(footprint, terrain(target.getWorld(), footprint).solid());
        Body placed = Body.of(definition, state);
        for (Live other : live.values()) {
            crowded |= other.world.equals(target.getWorld()) && placed.overlaps(Body.of(other.definition, other.state));
        }
        if (crowded) {
            player.sendActionBar(Component.text("No room for it here", NamedTextColor.GRAY));
            return false;
        }
        StoredVehicle row = new StoredVehicle(UUID.randomUUID(), item.id(), target.getWorld().getUID(), state.x(),
                state.y(), state.z(), (float) state.yaw(), player.getUniqueId());
        database.saveVehicle(row);
        spawn(row, definition);
        return true;
    }

    /**
     * {@code player} clicked {@code entity}, part of a vehicle: in they get - driving, if the
     * driver's seat is free - or, sneaking, they pick it up.
     *
     * @return whether the entity was a vehicle's
     */
    public boolean clicked(Player player, Entity entity) {
        UUID id = byEntity.get(entity.getUniqueId());
        Live vehicle = id == null ? null : live.get(id);
        if (vehicle == null) {
            return false;
        }
        if (player.isSneaking()) {
            pickUp(player, vehicle);
            return true;
        }
        if (player.isInsideVehicle()) {
            return true;
        }
        Block under = vehicle.world.getBlockAt((int) Math.floor(vehicle.state.x()), (int) Math.floor(vehicle.state.y()),
                (int) Math.floor(vehicle.state.z()));
        if (!protection.check(player, under, Interaction.SIT)) {
            return true;
        }
        if (driver(vehicle) == null) {
            faceForward(player, vehicle);
            vehicle.root.addPassenger(player);
            return true;
        }
        for (ItemDisplay seat : vehicle.seats) {
            if (seat.getPassengers().isEmpty()) {
                faceForward(player, vehicle);
                seat.addPassenger(player);
                return true;
            }
        }
        player.sendActionBar(Component.text("Every seat is taken", NamedTextColor.GRAY));
        return true;
    }

    /** Whoever gets in starts looking the way it faces; they look around freely after. */
    private static void faceForward(Player player, Live vehicle) {
        Location facing = player.getLocation();
        facing.setYaw((float) vehicle.state.yaw());
        player.teleport(facing);
    }

    /** Back into the hands of whoever placed it, or of anyone allowed to - with whoever was in it let off. */
    private void pickUp(Player player, Live vehicle) {
        boolean owner = vehicle.owner == null || vehicle.owner.equals(player.getUniqueId());
        if (!owner && !player.hasPermission(ADMIN)) {
            player.sendActionBar(Component.text("It is not yours to pick up", NamedTextColor.GRAY));
            return;
        }
        Optional<CustomItem> item = items.get(vehicle.itemId);
        despawn(vehicle);
        live.remove(vehicle.id);
        database.deleteVehicle(vehicle.id);
        if (item.isPresent() && player.getGameMode() != GameMode.CREATIVE) {
            for (ItemStack left : player.getInventory().addItem(factory.create(item.get(), 1)).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        }
    }

    /**
     * {@code rider} got off {@code from}, a seat of a vehicle: they step down beside it, on their
     * seat's side - or the other, or, with no room on either, where the game put them.
     */
    public void dismounted(Entity rider, Entity from) {
        UUID id = byEntity.get(from.getUniqueId());
        Live vehicle = id == null ? null : live.get(id);
        if (vehicle == null) {
            return;
        }
        int seat = from.equals(vehicle.root) ? 0 : vehicle.seats.indexOf(from) + 1;
        if (seat == 0 && !from.equals(vehicle.root)) {
            return;
        }
        Offset at = vehicle.definition.seats().get(seat);
        double out = vehicle.definition.width() / 2 + 0.45;
        double first = at.x() >= 0 ? 1 : -1;
        Location exit = null;
        for (double side : new double[]{first, -first}) {
            double[] point = VehiclePhysics.point(vehicle.state, new Offset(side * out, 0, at.z()));
            if (standable(vehicle.world, point[0], vehicle.state.y(), point[2])) {
                exit = new Location(vehicle.world, point[0], vehicle.state.y(), point[2]);
                break;
            }
        }
        if (exit == null) {
            return;
        }
        Location target = exit;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (rider.isValid() && !rider.isInsideVehicle()) {
                target.setYaw(rider.getLocation().getYaw());
                target.setPitch(rider.getLocation().getPitch());
                rider.teleport(target);
            }
        });
    }

    /** Room for a player standing at x, y, z, and ground under them within a step. */
    private static boolean standable(World world, double x, double y, double z) {
        Aabb body = new Aabb(x - 0.3, y + 0.01, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
        Aabb under = new Aabb(x - 0.3, y - 0.6, z - 0.3, x + 0.3, y + 0.01, z + 0.3);
        Terrain around = terrain(world, new Aabb(x - 0.3, y - 0.6, z - 0.3, x + 0.3, y + 1.8, z + 0.3));
        return !collides(body, around.solid()) && collides(under, around.solid());
    }

    /** Whether {@code entity} is part of a vehicle. */
    public boolean isPart(Entity entity) {
        return byEntity.containsKey(entity.getUniqueId());
    }

    // ---------------------------------------------------------------- the tick

    private void tick() {
        if (live.isEmpty()) {
            return;
        }
        long started = System.nanoTime();
        List<Runnable> steps = new ArrayList<>();
        for (Live vehicle : List.copyOf(live.values())) {
            if (!whole(vehicle) && !build(vehicle)) {
                refused(vehicle);
                continue;
            }
            Result result = vehicle.result;
            if (result != null) {
                vehicle.result = null;
                vehicle.stepping = false;
                apply(vehicle, result);
            }
        }
        // 5.0: every vehicle where it is now, by world - what each step can run into. The physics
        // thread works the steps one after another and moves each one's body as it goes, so no two
        // end a tick inside each other.
        Map<UUID, Map<UUID, Body>> bodies = new HashMap<>();
        for (Live vehicle : live.values()) {
            bodies.computeIfAbsent(vehicle.world.getUID(), world -> new java.util.LinkedHashMap<>())
                    .put(vehicle.id, Body.of(vehicle.definition, vehicle.state));
        }
        for (Live vehicle : List.copyOf(live.values())) {
            if (vehicle.stepping) {
                continue;
            }
            Player driver = driver(vehicle);
            Input input = driver == null ? Input.NONE : input(driver.getCurrentInput());
            if (driver == null && vehicle.state.atRest()) {
                if (vehicle.dirty) {
                    save(vehicle);
                }
                continue;
            }
            Terrain terrain = terrain(vehicle.world, VehiclePhysics.reach(vehicle.definition, vehicle.state, STEP_SECONDS));
            VehicleDefinition definition = vehicle.definition;
            State state = vehicle.state;
            Map<UUID, Body> around = bodies.get(vehicle.world.getUID());
            vehicle.stepping = true;
            steps.add(() -> {
                List<Body> others = new ArrayList<>(around.size());
                for (Map.Entry<UUID, Body> other : around.entrySet()) {
                    if (!other.getKey().equals(vehicle.id)) {
                        others.add(other.getValue());
                    }
                }
                State next = VehiclePhysics.step(definition, state, input, terrain, others, STEP_SECONDS);
                around.put(vehicle.id, Body.of(definition, next));
                List<VehiclePose.Transform> poses = new ArrayList<>(definition.parts().size());
                for (VehicleDefinition.Part part : definition.parts()) {
                    poses.add(VehiclePose.of(next, part, definition.seats().getFirst()));
                }
                vehicle.result = new Result(next, poses);
            });
        }
        if (!steps.isEmpty()) {
            physics.execute(() -> {
                long begun = System.nanoTime();
                for (Runnable step : steps) {
                    try {
                        step.run();
                    } catch (RuntimeException | LinkageError error) {
                        logger.log(Level.WARNING, "A vehicle physics step failed; the next one runs as usual", error);
                    }
                }
                physicsNanos.addAndGet(System.nanoTime() - begun);
                physicsSteps.addAndGet(steps.size());
            });
        }
        mainNanos += System.nanoTime() - started;
        mainTicks++;
    }

    /** The vehicle where the physics thread worked it out to be: root and seats moved, displays posed. */
    private void apply(Live vehicle, Result result) {
        State before = vehicle.state;
        State state = result.state();
        vehicle.state = state;
        VehicleDefinition definition = vehicle.definition;
        vehicle.root.teleport(at(vehicle.world, state, definition.seats().getFirst()), KEEP_RIDERS);
        for (int i = 0; i < vehicle.seats.size(); i++) {
            vehicle.seats.get(i).teleport(at(vehicle.world, state, definition.seats().get(i + 1)), KEEP_RIDERS);
        }
        vehicle.hitbox.teleport(new Location(vehicle.world, state.x(), state.y(), state.z()));
        for (int i = 0; i < vehicle.parts.size() && i < result.poses().size(); i++) {
            VehiclePose.Transform pose = result.poses().get(i);
            if (vehicle.parts.get(i) != null && !VehiclePose.same(vehicle.shown.get(i), pose)) {
                show(vehicle.parts.get(i), pose);
                vehicle.shown.set(i, pose);
            }
        }
        if (state.x() != before.x() || state.y() != before.y() || state.z() != before.z() || state.yaw() != before.yaw()) {
            vehicle.dirty = true;
        }
        if (vehicle.dirty && (state.atRest() || System.currentTimeMillis() - vehicle.savedAt > SAVE_EVERY_MILLIS)) {
            save(vehicle);
        }
    }

    private void save(Live vehicle) {
        database.saveVehicle(vehicle.row());
        vehicle.dirty = false;
        vehicle.savedAt = System.currentTimeMillis();
    }

    private static Input input(org.bukkit.Input keys) {
        return new Input(keys.isForward(), keys.isBackward(), keys.isLeft(), keys.isRight(), keys.isJump());
    }

    private static @Nullable Player driver(Live vehicle) {
        for (Entity rider : vehicle.root.getPassengers()) {
            if (rider instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    /**
     * The collision boxes of the blocks in {@code region}, and its water, as this tick sees them. A
     * block in a chunk that is not loaded counts as a full one: nothing drives into the unknown.
     */
    static Terrain terrain(World world, Aabb region) {
        List<Aabb> solid = new ArrayList<>();
        List<Aabb> water = new ArrayList<>();
        int minY = Math.max(world.getMinHeight(), (int) Math.floor(region.minY()));
        int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(region.maxY()));
        for (int x = (int) Math.floor(region.minX()); x <= (int) Math.floor(region.maxX()); x++) {
            for (int z = (int) Math.floor(region.minZ()); z <= (int) Math.floor(region.maxZ()); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    solid.add(new Aabb(x, minY, z, x + 1, maxY + 1, z + 1));
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    Material type = block.getType();
                    if (type.isAir()) {
                        continue;
                    }
                    BlockData data = block.getBlockData();
                    if (type == Material.WATER || data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
                        water.add(Aabb.block(x, y, z));
                    }
                    for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                        solid.add(new Aabb(x + box.getMinX(), y + box.getMinY(), z + box.getMinZ(),
                                x + box.getMaxX(), y + box.getMaxY(), z + box.getMaxZ()));
                    }
                }
            }
        }
        return new Terrain(solid, water);
    }

    private static boolean collides(Aabb box, List<Aabb> solid) {
        for (Aabb other : solid) {
            if (box.minX() < other.maxX() && box.maxX() > other.minX() && box.minY() < other.maxY()
                    && box.maxY() > other.minY() && box.minZ() < other.maxZ() && box.maxZ() > other.minZ()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- entities

    private void spawn(StoredVehicle row, VehicleDefinition definition) {
        World world = plugin.getServer().getWorld(row.world());
        if (world == null) {
            parked.put(row.id(), row);
            return;
        }
        Live vehicle = new Live(row.id(), row.itemId(), row.owner(), world, definition,
                State.parked(row.x(), row.y(), row.z(), row.yaw()));
        if (!build(vehicle)) {
            refused(vehicle);
            return;
        }
        live.put(row.id(), vehicle);
    }

    /**
     * Spawns whichever of {@code vehicle}'s entities are missing: all of them the first time; later,
     * those something else removed - a /kill, an entity clean-up, or the game itself, which takes a
     * driver's vehicle along when they leave the server. Whoever rides the rest stays put.
     *
     * @return whether it is whole now
     */
    private boolean build(Live vehicle) {
        World world = vehicle.world;
        VehicleDefinition definition = vehicle.definition;
        State state = vehicle.state;
        boolean newRoot = gone(vehicle.root);
        if (newRoot) {
            if (vehicle.root != null) {
                forget(vehicle.root);
            }
            for (ItemDisplay part : vehicle.parts) {
                if (part != null) {
                    forget(part);
                    part.remove();
                }
            }
            vehicle.parts.clear();
            vehicle.shown.clear();
            vehicle.root = world.spawn(at(world, state, definition.seats().getFirst()), ItemDisplay.class, display -> {
                common(display, vehicle.id);
                display.setTeleportDuration(2);
            });
            remember(vehicle.root, vehicle.id);
        }
        for (int i = 0; i < definition.parts().size(); i++) {
            if (i == vehicle.parts.size()) {
                vehicle.parts.add(null);
                vehicle.shown.add(null);
            }
            ItemDisplay part = vehicle.parts.get(i);
            // A part that has no item to draw is left out, and only looked for again with a new root.
            if (newRoot || part != null && gone(part)) {
                if (part != null) {
                    forget(part);
                }
                VehiclePose.Transform pose = VehiclePose.of(state, definition.parts().get(i), definition.seats().getFirst());
                ItemDisplay fresh = spawnPart(vehicle, definition.parts().get(i), pose);
                vehicle.parts.set(i, fresh);
                vehicle.shown.set(i, pose);
            }
        }
        for (int i = 1; i < definition.seats().size(); i++) {
            ItemDisplay seat = i - 1 < vehicle.seats.size() ? vehicle.seats.get(i - 1) : null;
            if (!gone(seat)) {
                continue;
            }
            if (seat != null) {
                forget(seat);
            }
            ItemDisplay fresh = world.spawn(at(world, state, definition.seats().get(i)), ItemDisplay.class, display -> {
                common(display, vehicle.id);
                display.setTeleportDuration(2);
            });
            remember(fresh, vehicle.id);
            if (i - 1 < vehicle.seats.size()) {
                vehicle.seats.set(i - 1, fresh);
            } else {
                vehicle.seats.add(fresh);
            }
        }
        if (gone(vehicle.hitbox)) {
            if (vehicle.hitbox != null) {
                forget(vehicle.hitbox);
            }
            vehicle.hitbox = world.spawn(new Location(world, state.x(), state.y(), state.z()),
                    org.bukkit.entity.Interaction.class, hitbox -> {
                        common(hitbox, vehicle.id);
                        hitbox.setInteractionWidth((float) Math.max(definition.width(), definition.length()));
                        hitbox.setInteractionHeight((float) definition.height());
                        hitbox.setResponsive(true);
                    });
            remember(vehicle.hitbox, vehicle.id);
        }
        return whole(vehicle);
    }

    private @Nullable ItemDisplay spawnPart(Live vehicle, VehicleDefinition.Part part, VehiclePose.Transform pose) {
        ItemStack stack = partItem(vehicle.itemId, part);
        if (stack == null) {
            return null;
        }
        ItemDisplay display = vehicle.world.spawn(vehicle.root.getLocation(), ItemDisplay.class, entity -> {
            common(entity, vehicle.id);
            entity.setItemStack(stack);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(transformation(pose));
        });
        vehicle.root.addPassenger(display);
        remember(display, vehicle.id);
        return display;
    }

    /** Every entity it is drawn with is there; a part with nothing to draw counts as there. */
    private static boolean whole(Live vehicle) {
        if (gone(vehicle.root) || gone(vehicle.hitbox)) {
            return false;
        }
        for (ItemDisplay part : vehicle.parts) {
            if (part != null && gone(part)) {
                return false;
            }
        }
        for (ItemDisplay seat : vehicle.seats) {
            if (gone(seat)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Removed, or never let into the world. Not {@link Entity#isValid()}: an entity just spawned in a
     * chunk the server has not started tracking entities in yet is not valid either, but is there.
     */
    private static boolean gone(@Nullable Entity entity) {
        return entity == null || entity.isDead() || !entity.isInWorld();
    }

    /** Another plugin stopped its entities: left parked, not tried again every tick. */
    private void refused(Live vehicle) {
        StoredVehicle row = vehicle.row();
        despawn(vehicle);
        live.remove(vehicle.id);
        parked.put(row.id(), row);
        logger.warning("Vehicle " + row.id() + " (" + row.itemId() + ") could not be spawned - another plugin"
                + " stopped its entities. It is tried again when its chunk loads next.");
    }

    private void remember(Entity entity, UUID id) {
        byEntity.put(entity.getUniqueId(), id);
    }

    private void forget(Entity entity) {
        byEntity.remove(entity.getUniqueId());
    }

    private void despawn(Live vehicle) {
        for (Entity entity : entities(vehicle)) {
            forget(entity);
            entity.eject();
            entity.remove();
        }
    }

    private static List<Entity> entities(Live vehicle) {
        List<Entity> all = new ArrayList<>();
        if (vehicle.root != null) {
            all.add(vehicle.root);
        }
        for (ItemDisplay part : vehicle.parts) {
            if (part != null) {
                all.add(part);
            }
        }
        all.addAll(vehicle.seats);
        if (vehicle.hitbox != null) {
            all.add(vehicle.hitbox);
        }
        return all;
    }

    private void common(Entity entity, UUID id) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.getPersistentDataContainer().set(vehicleTag, PersistentDataType.STRING, id.toString());
    }

    private @Nullable ItemStack partItem(String vehicleItem, VehicleDefinition.Part part) {
        String id = part.item() == null ? vehicleItem : part.item();
        Optional<CustomItem> item = items.get(id);
        if (item.isEmpty()) {
            logger.warning("Vehicle part " + id + " of " + vehicleItem + " is not a loaded item; it is not drawn.");
            return null;
        }
        return factory.create(item.get(), 1);
    }

    private static void show(ItemDisplay display, VehiclePose.Transform pose) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTransformation(transformation(pose));
    }

    private static Transformation transformation(VehiclePose.Transform pose) {
        return new Transformation(new Vector3f(pose.translation()), new Quaternionf(pose.rotation()),
                new Vector3f(pose.scale()), new Quaternionf());
    }

    private static Location at(World world, State state, Offset offset) {
        double[] point = VehiclePhysics.point(state, offset);
        return new Location(world, point[0], point[1], point[2], (float) state.yaw(), 0);
    }

    // ---------------------------------------------------------------- for /arkcontent vehicles

    /** Spawned, parked, and of the spawned how many are being driven. */
    public int[] counts() {
        int driven = 0;
        for (Live vehicle : live.values()) {
            if (driver(vehicle) != null) {
                driven++;
            }
        }
        return new int[]{live.size(), parked.size(), driven};
    }

    /** Mean main-thread time a tick, and physics time a vehicle step, in microseconds. */
    public double[] timing() {
        long steps = physicsSteps.get();
        return new double[]{mainTicks == 0 ? 0 : mainNanos / 1000.0 / mainTicks,
                steps == 0 ? 0 : physicsNanos.get() / 1000.0 / steps, steps};
    }

    /** Forgets the timings, for a fresh measurement. */
    public void resetTiming() {
        mainNanos = 0;
        mainTicks = 0;
        physicsNanos.set(0);
        physicsSteps.set(0);
    }
}
