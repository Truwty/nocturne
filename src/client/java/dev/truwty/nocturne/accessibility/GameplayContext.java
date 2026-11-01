package dev.truwty.nocturne.accessibility;

public enum GameplayContext {
    EXPLORATION("Exploration"),
    COMBAT("Combat"),
    MENUS("Menus"),
    SCREENSHOTS("Screenshots");
    public final String label;

    GameplayContext(String label) {
        this.label = label;
    }

    public static GameplayContext choose(boolean screenshot, boolean menu, boolean combat) {
        return screenshot ? SCREENSHOTS : menu ? MENUS : combat ? COMBAT : EXPLORATION;
    }
}
