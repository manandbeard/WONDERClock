package com.manandbeard.wonderclock.render

import com.manandbeard.wonderclock.data.ClockConfig

/**
 * The effective colors for one render.
 *
 * With dynamic color off this is just the config's colors. With it on, every
 * hue is replaced by the matching wallpaper color while the user's *alpha*
 * survives — so a config tuned to sit at 60% over a photo keeps doing that.
 */
internal class Palette(cfg: ClockConfig, env: RenderEnv) {

    private val dyn: DynamicPalette? = if (cfg.dynamicColor) env.dynamic else null

    private fun pick(base: Int, rgb: Int?): Int = if (rgb == null) base else recolor(base, rgb)

    val time: Int = pick(cfg.timeColor, dyn?.accentSoft)
    val hour: Int = pick(if (cfg.splitColors) cfg.hourColor else cfg.timeColor, dyn?.accentSoft)
    val minute: Int = pick(if (cfg.splitColors) cfg.minuteColor else cfg.timeColor, dyn?.accent)
    val separator: Int =
        pick(if (cfg.splitColors) cfg.separatorColor else cfg.timeColor, dyn?.accent)
    val gradient: Int = pick(cfg.gradientColor, dyn?.secondary)
    val stroke: Int = pick(cfg.strokeColor, dyn?.accentSoft)
    val shadow: Int = cfg.shadowColor

    val date: Int = pick(cfg.dateColor, dyn?.accentSoft)
    val module: Int = pick(cfg.moduleColor, dyn?.accentSoft)

    val background: Int = pick(cfg.backgroundColor, dyn?.surface)
    val background2: Int = pick(cfg.backgroundColor2, dyn?.surface)
    val border: Int = pick(cfg.borderColor, dyn?.accentSoft)

    val progress: Int = pick(cfg.progressColor, dyn?.accent)
    val progressTrack: Int = pick(cfg.progressTrackColor, dyn?.accent)

    val dial: Int = pick(cfg.dialColor, dyn?.accentSoft)
    val numeral: Int = pick(cfg.numeralColor, dyn?.accentSoft)
    val hourHand: Int = pick(cfg.hourHandColor, dyn?.onSurface)
    val minuteHand: Int = pick(cfg.minuteHandColor, dyn?.accent)
    val centerDot: Int = pick(cfg.centerDotColor, dyn?.secondary)
    val ring: Int = pick(cfg.analogRingColor, dyn?.accentSoft)
    val analogFill: Int = pick(cfg.analogFillColor, dyn?.surface)

    val wordInactive: Int = pick(cfg.wordInactiveColor, dyn?.accentSoft)
    val binaryOff: Int = pick(cfg.binaryOffColor, dyn?.accentSoft)
}
