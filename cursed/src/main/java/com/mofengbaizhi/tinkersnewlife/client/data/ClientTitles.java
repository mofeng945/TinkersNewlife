package com.mofengbaizhi.tinkersnewlife.client.data;

import java.util.ArrayList;
import java.util.List;

/** §1305 客户端镜像：服务端同步来的"已持有 ＋ 已选中"头衔。 */
public final class ClientTitles {

    public static List<String> held = new ArrayList<>();
    public static List<String> selected = new ArrayList<>();

    private ClientTitles() {
    }

    public static void set(List<String> h, List<String> s) {
        held = new ArrayList<>(h);
        selected = new ArrayList<>(s);
    }
}