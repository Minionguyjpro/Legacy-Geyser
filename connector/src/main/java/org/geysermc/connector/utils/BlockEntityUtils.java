/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.connector.utils;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.NbtMap;
import com.nukkitx.protocol.bedrock.packet.BlockEntityDataPacket;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.world.block.entity.BedrockOnlyBlockEntity;
import org.geysermc.connector.network.translators.world.block.entity.BlockEntityTranslator;
import org.geysermc.connector.network.translators.world.block.entity.RequiresBlockState;
import org.geysermc.connector.registry.Registries;

import java.util.HashMap;
import java.util.Map;

public class BlockEntityUtils {

    /**
     * A list of all block entities that require the Java block state in order to fill out their block entity information.
     * This list will be smaller with cache chunks on as we don't need to double-cache data
     */
    public static final ObjectArrayList<RequiresBlockState> REQUIRES_BLOCK_STATE_LIST = new ObjectArrayList<>(); // TODO: Handle as a registry item?

    /**
     * Contains a list of irregular block entity name translations that can't be fit into the regex
     */
    public static final Map<String, String> BLOCK_ENTITY_TRANSLATIONS = new HashMap<String, String>() {
        {
            // Bedrock/Java differences
            put("minecraft:enchanting_table", "EnchantTable");
            put("minecraft:jigsaw", "JigsawBlock");
            put("minecraft:piston_head", "PistonArm");
            put("minecraft:trapped_chest", "Chest");
            // There are some legacy IDs sent but as far as I can tell they are not needed for things to work properly
        }
    };

    private static final BlockEntityTranslator EMPTY_TRANSLATOR = Registries.BLOCK_ENTITIES.get("Empty");

    static {
        boolean cacheChunks = GeyserConnector.getInstance().getConfig().isCacheChunks();
        for (BlockEntityTranslator translator : Registries.BLOCK_ENTITIES.get().values()) {
            if (!(translator instanceof RequiresBlockState)) {
                continue;
            }
            Class<? extends BlockEntityTranslator> clazz = translator.getClass();
            GeyserConnector.getInstance().getLogger().debug("Found block entity that requires block state: " + clazz.getCanonicalName());

            try {
                RequiresBlockState requiresBlockState = (RequiresBlockState) clazz.newInstance();
                if (cacheChunks && !(requiresBlockState instanceof BedrockOnlyBlockEntity)) {
                    // Not needed to put this one in the map; cache chunks takes care of that for us
                    GeyserConnector.getInstance().getLogger().debug("Not adding because cache chunks is enabled.");
                    continue;
                }
                REQUIRES_BLOCK_STATE_LIST.add(requiresBlockState);
            } catch (InstantiationException | IllegalAccessException e) {
                GeyserConnector.getInstance().getLogger().error(LanguageUtils.getLocaleStringLog("geyser.network.translator.block_state.failed", clazz.getCanonicalName()));
            }
        }
    }

    public static String getBedrockBlockEntityId(String id) {
        // These are the only exceptions when it comes to block entity ids
        String value = BLOCK_ENTITY_TRANSLATIONS.get(id);
        if (value != null) {
            return value;
        }

        id = id.replace("minecraft:", "")
                .replace("_", " ");
        // Split at every space or capital letter - for the latter, some legacy Java block entity tags are the correct format already
        String[] words;
        if (!id.toUpperCase().equals(id)) { // Otherwise we get [S, K, U, L, L]
            words = id.split("(?=[A-Z])| "); // Split at every space or note or before every capital letter
        } else {
            words = id.split(" ");
        }
        for (int i = 0; i < words.length; i++) {
            words[i] = words[i].substring(0, 1).toUpperCase() + words[i].substring(1).toLowerCase();
        }

        return String.join("", words);
    }

    public static BlockEntityTranslator getBlockEntityTranslator(String name) {
        BlockEntityTranslator blockEntityTranslator = Registries.BLOCK_ENTITIES.get(name);
        if (blockEntityTranslator != null) {
            return blockEntityTranslator;
        }
        return EMPTY_TRANSLATOR;
    }

    public static void updateBlockEntity(GeyserSession session, NbtMap blockEntity, Position position) {
        updateBlockEntity(session, blockEntity, Vector3i.from(position.getX(), position.getY(), position.getZ()));
    }

    public static void updateBlockEntity(GeyserSession session, NbtMap blockEntity, Vector3i position) {
        BlockEntityDataPacket blockEntityPacket = new BlockEntityDataPacket();
        blockEntityPacket.setBlockPosition(position);
        blockEntityPacket.setData(blockEntity);
        session.sendUpstreamPacket(blockEntityPacket);
    }
}
