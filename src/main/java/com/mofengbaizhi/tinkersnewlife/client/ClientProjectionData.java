package com.mofengbaizhi.tinkersnewlife.client;

/**
 * 客户端 投射咒法 罚站状态（由 PacketProjectionStun 同步）。
 * 罚站期间禁用鼠标键盘输入并锁定视角。
 */
public class ClientProjectionData {

    private static boolean stunned;
    private static float stunYaw;
    private static float stunPitch;

    public static void update(boolean stunnedIn, float yaw, float pitch) {
        stunned = stunnedIn;
        if (stunnedIn) {
            stunYaw = yaw;
            stunPitch = pitch;
        }
    }

    public static boolean isStunned() {
        return stunned;
    }

    public static float getStunYaw() {
        return stunYaw;
    }

    public static float getStunPitch() {
        return stunPitch;
    }
}
