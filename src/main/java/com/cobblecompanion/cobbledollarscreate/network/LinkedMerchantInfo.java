package com.cobblecompanion.cobbledollarscreate.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * Ein an das Netzwerk des gerade bearbeiteten Lagertickers angeschlossener CustomNPC-Trader oder
 * CobbleMerchant (siehe LinkedMerchantsSyncPacket) - für das Übersichts-Panel im Preis-Editor
 * (StockTickerPriceScreen). hasStorage=false -> keine Kisten-Verknüpfung, storageDimension/
 * storageX/Y/Z dann irrelevant (Platzhalterwerte).
 *
 * Eigener Handgeschriebener StreamCodec statt StreamCodec.composite(): dessen Overloads decken nur
 * bis zu 6 Felder ab, dieser Datensatz hat mehr.
 */
public record LinkedMerchantInfo(UUID uuid, boolean isCustomNpc, String name, String dimension, int x, int y, int z,
        boolean hasStorage, String storageDimension, int storageX, int storageY, int storageZ) {

    public static final StreamCodec<ByteBuf, LinkedMerchantInfo> CODEC = new StreamCodec<>() {
        @Override
        public LinkedMerchantInfo decode(ByteBuf buf) {
            UUID uuid = UUIDUtil.STREAM_CODEC.decode(buf);
            boolean isCustomNpc = ByteBufCodecs.BOOL.decode(buf);
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            String dimension = ByteBufCodecs.STRING_UTF8.decode(buf);
            int x = ByteBufCodecs.VAR_INT.decode(buf);
            int y = ByteBufCodecs.VAR_INT.decode(buf);
            int z = ByteBufCodecs.VAR_INT.decode(buf);
            boolean hasStorage = ByteBufCodecs.BOOL.decode(buf);
            String storageDimension = ByteBufCodecs.STRING_UTF8.decode(buf);
            int storageX = ByteBufCodecs.VAR_INT.decode(buf);
            int storageY = ByteBufCodecs.VAR_INT.decode(buf);
            int storageZ = ByteBufCodecs.VAR_INT.decode(buf);
            return new LinkedMerchantInfo(uuid, isCustomNpc, name, dimension, x, y, z, hasStorage, storageDimension, storageX, storageY, storageZ);
        }

        @Override
        public void encode(ByteBuf buf, LinkedMerchantInfo info) {
            UUIDUtil.STREAM_CODEC.encode(buf, info.uuid());
            ByteBufCodecs.BOOL.encode(buf, info.isCustomNpc());
            ByteBufCodecs.STRING_UTF8.encode(buf, info.name());
            ByteBufCodecs.STRING_UTF8.encode(buf, info.dimension());
            ByteBufCodecs.VAR_INT.encode(buf, info.x());
            ByteBufCodecs.VAR_INT.encode(buf, info.y());
            ByteBufCodecs.VAR_INT.encode(buf, info.z());
            ByteBufCodecs.BOOL.encode(buf, info.hasStorage());
            ByteBufCodecs.STRING_UTF8.encode(buf, info.storageDimension());
            ByteBufCodecs.VAR_INT.encode(buf, info.storageX());
            ByteBufCodecs.VAR_INT.encode(buf, info.storageY());
            ByteBufCodecs.VAR_INT.encode(buf, info.storageZ());
        }
    };
}
