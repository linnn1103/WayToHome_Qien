package com.qienhome;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import  net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class QienHomeMod implements ModInitializer {

	private static class Home {
		private final BlockPos pos;
		private final RegistryKey<World> world;

		public Home(BlockPos pos, RegistryKey<World> world) {
			this.pos = pos;
			this.world = world;
		}

		public BlockPos getPos() {
			return pos;
		}

		public RegistryKey<World> getWorld() {
			return world;
		}
	}

	private static final Map<String, Map<String, Home>> playerHomes = new HashMap<>();
	private static final Map<String, Home> lastPositions = new HashMap<>();

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("sethome")
					.then(CommandManager.argument("name", StringArgumentType.string())
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									String homeName = StringArgumentType.getString(context, "name");
									BlockPos pos = player.getBlockPos();
									RegistryKey<World> worldKey = player.getServerWorld().getRegistryKey();

									Home home = new Home(pos, worldKey);
									playerHomes.computeIfAbsent(player.getName().getString(), k -> new HashMap<>())
											.put(homeName, home);
									player.sendMessage(Text.of("§a家 '" + homeName + "' 已設定。"), false);
								}
								return 1;
							})
					)
			);

			dispatcher.register(CommandManager.literal("home")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player != null) {
							Map<String, Home> homes = playerHomes.get(player.getName().getString());
							if (homes == null || homes.isEmpty()) {
								player.sendMessage(Text.of("§c你未設定任何家。"), false);
							} else {
								player.sendMessage(Text.of("§6你的家: " + String.join(", ", homes.keySet())), false);
							}
						}
						return 1;
					})
					.then(CommandManager.argument("name", StringArgumentType.string())
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									String homeName = StringArgumentType.getString(context, "name");
									Map<String, Home> homes = playerHomes.get(player.getName().getString());
									if (homes != null && homes.containsKey(homeName)) {
										Home home = homes.get(homeName);
										BlockPos pos = home.getPos();
										ServerWorld targetWorld = player.getServer().getWorld(home.getWorld());
										if (targetWorld != null) {
											BlockPos currentPos = player.getBlockPos();
											RegistryKey<World> currentWorld = player.getServerWorld().getRegistryKey();
											lastPositions.put(player.getName().getString(), new Home(currentPos, currentWorld));

											player.teleport(
													targetWorld,
													pos.getX() + 0.5,
													pos.getY(),
													pos.getZ() + 0.5,
													Collections.emptySet(),
													player.getYaw(),
													player.getPitch(),
													false
											);
											player.sendMessage(Text.of("§a已傳送至家 '" + homeName + "'"), false);
										} else {
											player.sendMessage(Text.of("§c無法傳送：目標世界不存在。"), false);
										}
									} else {
										player.sendMessage(Text.of("§c家 '" + homeName + "' 不存在。"), false);
									}
								}
								return 1;
							})
							.suggests((context, builder) -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									Map<String, Home> homes = playerHomes.get(player.getName().getString());
									if (homes != null) {
										for (String homeName : homes.keySet()) {
											builder.suggest(homeName);
										}
									}
								}
								return builder.buildFuture();
							})
					)
			);

			dispatcher.register(CommandManager.literal("delhome")
					.then(CommandManager.argument("name", StringArgumentType.string())
							.executes(context -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									String homeName = StringArgumentType.getString(context, "name");
									Map<String, Home> homes = playerHomes.get(player.getName().getString());
									if (homes != null && homes.containsKey(homeName)) {
										homes.remove(homeName);
										player.sendMessage(Text.of("§a已刪除家 '" + homeName + "'"), false);
									} else {
										player.sendMessage(Text.of("§c家 '" + homeName + "' 不存在。"), false);
									}
								}
								return 1;
							})
							.suggests((context, builder) -> {
								ServerPlayerEntity player = context.getSource().getPlayer();
								if (player != null) {
									Map<String, Home> homes = playerHomes.get(player.getName().getString());
									if (homes != null) {
										for (String homeName : homes.keySet()) {
											builder.suggest(homeName);
										}
									}
								}
								return builder.buildFuture();
							})
					)
			);

			dispatcher.register(CommandManager.literal("back")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player != null) {
							String playerName = player.getName().getString();
							Home lastPosition = lastPositions.get(playerName);
							if (lastPosition != null) {
								ServerWorld targetWorld = player.getServer().getWorld(lastPosition.getWorld());
								if (targetWorld != null) {
									BlockPos pos = lastPosition.getPos();
									player.teleport(
											targetWorld,
											pos.getX() + 0.5,
											pos.getY(),
											pos.getZ() + 0.5,
											Collections.emptySet(),
											player.getYaw(),
											player.getPitch(),
											false
									);
									player.sendMessage(Text.of("§a已返回上一个位置。"), false);
									lastPositions.remove(playerName);
								} else {
									player.sendMessage(Text.of("§c無法返回：目標世界不存在。"), false);
								}
							} else {
								player.sendMessage(Text.of("§c沒有可返回的位置。"), false);
							}
						}
						return 1;
					})
			);

		});
	}
}