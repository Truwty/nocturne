package dev.truwty.nocturne.gui;

public record PickerColor(double hue, double saturation, double brightness, double opacity) {
    public PickerColor {
        if (!Double.isFinite(hue)
                || !Double.isFinite(saturation)
                || !Double.isFinite(brightness)
                || !Double.isFinite(opacity))
            throw new IllegalArgumentException("Color channels must be finite");
        hue = ((hue % 1) + 1) % 1;
        saturation = Math.clamp(saturation, 0, 1);
        brightness = Math.clamp(brightness, 0, 1);
        opacity = Math.clamp(opacity, 0, 1);
    }

    public int argb() {
        return (int) Math.round(opacity * 255) << 24
                | java.awt.Color.HSBtoRGB((float) hue, (float) saturation, (float) brightness)
                        & 0xffffff;
    }

    public static PickerColor from(int argb) {
        float[] hsv = java.awt.Color.RGBtoHSB(argb >> 16 & 255, argb >> 8 & 255, argb & 255, null);
        return new PickerColor(hsv[0], hsv[1], hsv[2], (argb >>> 24) / 255.0);
    }

    public static int hex(String value) {
        if (!value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))
            throw new IllegalArgumentException("Use #RRGGBB or #AARRGGBB");
        return (int) Long.parseLong(value.substring(1), 16)
                | (value.length() == 7 ? 0xff000000 : 0);
    }
}
