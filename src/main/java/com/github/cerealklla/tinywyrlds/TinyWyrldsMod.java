package com.github.cerealklla.tinywyrlds;

import java.util.UUID;

import com.github.cerealklla.tinywyrlds.debug.DebugCommands;
import com.github.cerealklla.tinywyrlds.instance.WyrldInstanceRecord;
import com.github.cerealklla.tinywyrlds.instance.WyrldLifecycleTicker;
import com.github.cerealklla.tinywyrlds.instance.WyrldManager;
import com.github.cerealklla.tinywyrlds.instance.WyrldRespawnListener;
import com.github.cerealklla.tinywyrlds.registration.ModAttachments;
import com.github.cerealklla.tinywyrlds.registration.ModBlocks;
import com.github.cerealklla.tinywyrlds.registration.ModEntities;
import com.github.cerealklla.tinywyrlds.registration.ModItems;
import com.github.cerealklla.tinywyrlds.space.AdvancementBlocker;
import com.github.cerealklla.tinywyrlds.space.WyrldSpaceDimension;
import com.github.cerealklla.tinywyrlds.voxel.VoxelLockListener;
import com.github.cerealklla.tinywyrlds.wyrldprint.CreativeZoneTicker;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintAreaSetupListener;
import com.github.cerealklla.tinywyrlds.wyrldprint.entrance.WyrldEntranceProximityTicker;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintCapture;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintPlacement;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintRecord;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintStorage;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintType;
import com.github.cerealklla.tinywyrlds.wyrldprint.WyrldPrintValidator;
import com.github.cerealklla.tinywyrlds.wyrldprint.generate.OneOffGenerator;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.ClearCraftZonePayload;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.ClientWyrldPrintRequests;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.GenerateOneOffPayload;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.LoadWyrldPrintPayload;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.OpenWyrldWorkbenchScreenPayload;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.RegenerateDungeonPayload;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.SaveWyrldPrintPayload;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.ValidateAllWyrldPrintsPayload;
import com.github.cerealklla.tinywyrlds.wyrldprint.workbench.WyrldWorkbenchBlock;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(TinyWyrldsMod.MODID)
public class TinyWyrldsMod {

    public static final String MODID = "tinywyrlds";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TinyWyrldsMod(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addCreativeTabItems);
        modEventBus.addListener(TinyWyrldsMod::registerPayloads);

        NeoForge.EVENT_BUS.register(new VoxelLockListener());
        NeoForge.EVENT_BUS.register(new WyrldLifecycleTicker());
        NeoForge.EVENT_BUS.register(new WyrldPrintAreaSetupListener());
        NeoForge.EVENT_BUS.register(new CreativeZoneTicker());
        NeoForge.EVENT_BUS.register(new AdvancementBlocker());
        NeoForge.EVENT_BUS.register(new WyrldEntranceProximityTicker());
        NeoForge.EVENT_BUS.register(new WyrldRespawnListener());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> DebugCommands.register(event.getDispatcher()));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("TinyWyrlds common setup");
    }

    /** No mod in this suite had ever registered anything into a creative tab before -- every
     *  custom block/item across all 9 mods has only ever been obtainable via {@code /give} or a
     *  mod's own granting code. The six Connector Point blocks are meant to be hand-placed by an
     *  admin while authoring a WyrldPrint, so real spec: they belong in the browsable creative
     *  inventory, same tab (Functional Blocks) real vanilla doors/trapdoors already live in. */
    private void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            return;
        }
        event.accept(ModItems.CONNECTOR_POINT_WALL_SMALL_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_WALL_MEDIUM_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_WALL_LARGE_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_FLOOR_SMALL_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_FLOOR_MEDIUM_ITEM.get());
        event.accept(ModItems.CONNECTOR_POINT_FLOOR_LARGE_ITEM.get());
        event.accept(ModItems.SPAWN_LOCATION_ITEM.get());
        event.accept(ModItems.WYRLD_WORKBENCH_ITEM.get());
        ModItems.MARKER_ITEMS.values().forEach(item -> event.accept(item.get()));
        event.accept(ModItems.WYRLD_RETURN_ITEM.get());
        event.accept(ModItems.EXIT_PORTAL_SMALL_ITEM.get());
        event.accept(ModItems.EXIT_PORTAL_MEDIUM_ITEM.get());
        event.accept(ModItems.EXIT_PORTAL_LARGE_ITEM.get());
    }

    /** Backs {@code workbench.WyrldWorkbenchBlock}'s GUI -- each handler calls straight into the
     *  same lower-level APIs {@code debug.DebugCommands} already uses for the identical action, so
     *  the GUI and the debug commands never diverge in behavior. */
    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(OpenWyrldWorkbenchScreenPayload.TYPE, OpenWyrldWorkbenchScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientWyrldPrintRequests.requestWorkbenchScreen(payload));

        registrar.playToServer(SaveWyrldPrintPayload.TYPE, SaveWyrldPrintPayload.STREAM_CODEC,
                (payload, context) -> withWyrldSpace(context, level -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    WyrldPrintType type;
                    try {
                        type = WyrldPrintType.valueOf(payload.printType().toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        player.sendSystemMessage(Component.literal("Type must be 'room', 'connector', or 'entrance'."));
                        return;
                    }
                    WyrldPrintCapture.CaptureResult result = WyrldPrintCapture.capture(level, payload.name(), player.getName().getString(), type);
                    if (result.error().isPresent()) {
                        player.sendSystemMessage(Component.literal(result.error().get()));
                        return;
                    }
                    WyrldPrintStorage.get().save(result.record().get());
                    WyrldPrintPlacement.setLoadedName(payload.name());
                    player.sendSystemMessage(Component.literal("Saved " + type.name() + " WyrldPrint '" + payload.name() + "'."));
                    WyrldWorkbenchBlock.sendScreen(player, payload.workbenchPos());
                }));

        registrar.playToServer(LoadWyrldPrintPayload.TYPE, LoadWyrldPrintPayload.STREAM_CODEC,
                (payload, context) -> withWyrldSpace(context, level -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    var found = WyrldPrintStorage.get().load(payload.name());
                    if (found.isEmpty()) {
                        player.sendSystemMessage(Component.literal("No WyrldPrint named '" + payload.name() + "'."));
                        return;
                    }
                    WyrldPrintPlacement.loadIntoCraftingZone(level, found.get());
                    player.sendSystemMessage(Component.literal("Loaded '" + payload.name() + "' into the crafting zone."));
                }));

        registrar.playToServer(ClearCraftZonePayload.TYPE, ClearCraftZonePayload.STREAM_CODEC,
                (payload, context) -> withWyrldSpace(context, level -> {
                    WyrldPrintPlacement.clearCraftingZone(level);
                    if (context.player() instanceof ServerPlayer player) {
                        player.sendSystemMessage(Component.literal("Cleared the crafting zone."));
                    }
                }));

        registrar.playToServer(ValidateAllWyrldPrintsPayload.TYPE, ValidateAllWyrldPrintsPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    int broken = 0;
                    for (WyrldPrintRecord record : WyrldPrintStorage.get().listAll()) {
                        WyrldPrintValidator.Result result = WyrldPrintValidator.checkConnectivity(record);
                        if (!result.connected()) {
                            broken++;
                        }
                        LOGGER.info("{} {} ({}): {}", result.connected() ? "OK  " : "FAIL", record.name(), record.type(), result.message());
                    }
                    player.sendSystemMessage(Component.literal("Validated all WyrldPrints -- " + broken
                            + " broken (see server log for the full per-piece report)."));
                });

        registrar.playToServer(GenerateOneOffPayload.TYPE, GenerateOneOffPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    OneOffGenerator.GenerateResult result = OneOffGenerator.generate(player.level().getServer(), payload.minRooms(), payload.maxRooms());
                    if (result == null) {
                        player.sendSystemMessage(Component.literal("Not enough saved WyrldPrints to generate a dungeon -- need at least 1 room or entrance."));
                        return;
                    }
                    enterGenerated(player, result);
                });

        registrar.playToServer(RegenerateDungeonPayload.TYPE, RegenerateDungeonPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (!(context.player() instanceof ServerPlayer player)) {
                        return;
                    }
                    UUID id;
                    try {
                        id = UUID.fromString(payload.dungeonId());
                    } catch (IllegalArgumentException e) {
                        player.sendSystemMessage(Component.literal("'" + payload.dungeonId() + "' isn't a valid dungeon id."));
                        return;
                    }
                    OneOffGenerator.GenerateResult result;
                    try {
                        result = OneOffGenerator.regenerate(player.level().getServer(), id);
                    } catch (IllegalStateException e) {
                        player.sendSystemMessage(Component.literal(e.getMessage()));
                        return;
                    }
                    if (result == null) {
                        player.sendSystemMessage(Component.literal("No saved dungeon with id " + id + "."));
                        return;
                    }
                    enterGenerated(player, result);
                });
    }

    private static void enterGenerated(ServerPlayer player, OneOffGenerator.GenerateResult result) {
        WyrldInstanceRecord instance = result.instance();
        if (!result.spawnPoints().isEmpty()) {
            WyrldManager.enterAt(player, instance.id(), result.spawnPoints().get(0));
        } else {
            WyrldManager.enter(player, instance.id());
        }
        player.sendSystemMessage(Component.literal("Generated and entered a one-off Wyrld " + instance.id()
                + " -- layout saved as " + result.dungeonId() + "."));
    }

    private interface WyrldSpaceAction {
        void run(ServerLevel level);
    }

    private static void withWyrldSpace(IPayloadContext context, WyrldSpaceAction action) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.level().getServer().getLevel(WyrldSpaceDimension.KEY);
        if (level == null) {
            player.sendSystemMessage(Component.literal("Wyrld Space isn't loaded."));
            return;
        }
        action.run(level);
    }
}
