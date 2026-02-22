package com.example.immersiveciv.item;

import com.example.immersiveciv.Immerviseciv;
import com.example.immersiveciv.network.ModMessages;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Предмет «Городская карта» (immervise-civ:city_map).
 * При использовании ПКМ:
 *   - Клиент: открывает CityMapScreen (через Immerviseciv.openCityMapScreen прокси)
 *   - Сервер: отправляет S2C OPEN_CITY_MAP (на случай если клиентский вызов не отработал)
 */
public class CityMapItem extends Item {

    public static final CityMapItem INSTANCE = new CityMapItem(
            new Item.Properties().stacksTo(1)
    );

    public CityMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            // Прокси устанавливается в ImmervisecivClient.onInitializeClient()
            if (Immerviseciv.openCityMapScreen != null) {
                Immerviseciv.openCityMapScreen.run();
            }
        } else {
            // Серверная сторона: шлём S2C-сигнал (backup)
            if (player instanceof ServerPlayer sp) {
                ServerPlayNetworking.send(sp, ModMessages.OPEN_CITY_MAP, PacketByteBufs.empty());
            }
        }

        return InteractionResultHolder.success(stack);
    }
}
