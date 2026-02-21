package com.example.immersiveciv.command;

import com.example.immersiveciv.Immerviseciv;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /civresult <json>
 * Внутренняя команда — вызывается Java-модом при получении validate_result
 * от Middleware, чтобы передать пакет в KubeJS через серверную команду.
 * Игроки не используют её напрямую (требует permission level 2).
 */
public class CivResultCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext buildContext) {
        dispatcher.register(
            Commands.literal("civresult")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("payload", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        String raw = StringArgumentType.getString(ctx, "payload");
                        // KubeJS перехватит команду через commandRegistry
                        // Здесь просто логируем для отладки
                        Immerviseciv.LOGGER.debug("[ImmersiveCiv] /civresult вызван: {}…",
                                raw.length() > 100 ? raw.substring(0, 100) : raw);
                        return 1;
                    }))
        );
    }
}
