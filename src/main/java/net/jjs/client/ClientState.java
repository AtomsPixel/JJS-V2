package net.jjs.client;

public final class ClientState {
    public static int character = -1;
    public static float ce = 200f;
    public static boolean reinforced;
    public static int zone;
    public static final int[] cd = new int[6];
    public static boolean prompted;

    public static void tick() {
        if (zone > 0) zone--;
        for (int i = 0; i < 6; i++) if (cd[i] > 0) cd[i]--;
    }
    private ClientState() {}
}
