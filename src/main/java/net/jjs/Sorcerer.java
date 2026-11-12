package net.jjs;

public enum Sorcerer {
    LIMITLESS("limitless", "Limitless Sorcerer",
            "Gravity, repulsion and erasure. Nothing touches you, everything bends to you.",
            0x4aa8ff,
            new Ability("Blue: Lapse", "Create a gravity singularity that drags in and crushes everything nearby.", 30, 100, "blue", 0x3d8bff, Abilities::blue),
            new Ability("Red: Reversal", "Fire a sphere that detonates on impact and launches enemies away.", 40, 60, "red", 0xff3030, Abilities::red),
            new Ability("Hollow Purple", "Fuse Blue and Red, then erase everything in a 40 block line. Destroys terrain.", 120, 200, "purple", 0xa020ff, Abilities::purple),
            new Ability("Domain: Infinite Void", "Paralyse everything inside a 14 block void while you are supercharged.", 150, 600, "void", 0x6a5cff, Abilities::voidDomain)),

    SHRINE("shrine", "Shrine Sovereign",
            "A calamity in human form. Cut, burn and carve through anything.",
            0xe01e37,
            new Ability("Dismantle", "Three invisible blades slice a long fan. Cuts soft terrain.", 25, 20, "dismantle", 0xff2a3a, Abilities::dismantle),
            new Ability("Cleave", "A brutal strike that scales with the target's max health.", 45, 80, "cleave", 0xff5566, Abilities::cleave),
            new Ability("Flame Arrow", "A blazing bolt that detonates in a fireball and sets enemies ablaze.", 60, 120, "flame", 0xff8a1f, Abilities::flameArrow),
            new Ability("Domain: Malevolent Shrine", "Endless slashes tear apart everything inside. Shreds soft blocks.", 150, 600, "shrine", 0xd0102a, Abilities::shrineDomain)),

    BRAWLER("brawler", "Black Flash Fighter",
            "Pure physical sorcery. Every punch is a chance to hit the spark.",
            0xff6a2b,
            new Ability("Cursed Rush", "Dash forward in a burst, smashing anything in your path.", 20, 40, "rush", 0xffa040, Abilities::rush),
            new Ability("Divergent Fist", "A punch that hits twice. The second impact arrives late and hard.", 35, 60, "fist", 0xffd060, Abilities::fist),
            new Ability("Black Flash Barrage", "Close the gap and unleash a 5-hit combo ending in a guaranteed Black Flash.", 80, 160, "barrage", 0xff1030, Abilities::barrage),
            new Ability("Overdrive", "10 seconds of berserk power, shockwaves and a huge Black Flash chance.", 120, 500, "overdrive", 0xff3a2a, Abilities::overdrive)),

    SHADOW("shadow", "Shadow Summoner",
            "Command the darkness beneath your feet. Strike from where they can't see.",
            0x7a5cff,
            new Ability("Shadow Step", "Blink behind your target (or 12 blocks ahead) and stab from the dark.", 25, 50, "step", 0x8a6cff, Abilities::shadowStep),
            new Ability("Shadow Spikes", "A line of spikes erupts from the ground, impaling everything.", 40, 80, "spikes", 0x9a7cff, Abilities::shadowSpikes),
            new Ability("Shadow Pool", "A pool of darkness that slows and drains enemies. Grants you speed and stealth.", 50, 140, "pool", 0x6a4cff, Abilities::shadowPool),
            new Ability("Domain: Chimera Garden", "A living nightmare. Spikes erupt under every enemy while you turn invisible.", 150, 600, "garden", 0xb060ff, Abilities::garden));

    public static final Ability RCT = new Ability("Reverse Cursed Technique", "Heal 6 hearts and cleanse harmful effects.", 60, 100, "rct", 0x40ff80, Abilities::rct);
    public static final Ability REINFORCE = new Ability("Cursed Energy Reinforcement", "Toggle: speed, strength, jump and defense. Drains cursed energy.", 0, 10, "reinforce", 0x58b8ff, Abilities::reinforce);

    public final String id, title, tagline;
    public final int color;
    public final Ability[] abilities;

    Sorcerer(String id, String title, String tagline, int color, Ability... abilities) {
        this.id = id; this.title = title; this.tagline = tagline; this.color = color; this.abilities = abilities;
    }

    public static Ability slot(Sorcerer s, int slot) {
        if (slot < 4) return s.abilities[slot];
        return slot == 4 ? RCT : REINFORCE;
    }
}
