package com.astrocam.app.stacker

enum class StackingMode(val displayName: String, val requiresAlignment: Boolean) {
    DEEP_SKY_AVERAGE("Deep Sky (Milky Way)", requiresAlignment = true),
    KAPPA_SIGMA("Satellite Removal (Kappa-Sigma)", requiresAlignment = true),
    STAR_TRAILS("Star Trails", requiresAlignment = false)
}
