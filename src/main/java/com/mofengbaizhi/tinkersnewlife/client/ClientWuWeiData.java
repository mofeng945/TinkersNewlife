package com.mofengbaizhi.tinkersnewlife.client;

/**
 * 无为转变 客户端状态：当前被操控的变形实体 id（服务端经 PacketWuWeiControl 同步）。
 */
public final class ClientWuWeiData {

    private static int controlledEntityId = 0;

    private ClientWuWeiData() {}

    public static void setControlledEntity(int id) {
        controlledEntityId = id;
    }

    public static int getControlledEntity() {
        return controlledEntityId;
    }
}
