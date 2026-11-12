package net.jjs;

import net.minecraft.util.Identifier;

public final class Net {
    public static final Identifier SELECT = new Identifier("jjs", "select"); // C2S int character
    public static final Identifier CAST   = new Identifier("jjs", "cast");   // C2S int slot
    public static final Identifier STATE  = new Identifier("jjs", "state");  // S2C player state
    public static final Identifier VFX    = new Identifier("jjs", "vfx");    // S2C spawn visual effect
    public static final Identifier SCREEN = new Identifier("jjs", "screen"); // S2C screen effect
    private Net() {}
}
