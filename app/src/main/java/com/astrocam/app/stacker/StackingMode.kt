package com.astrocam.app.stacker

enum class StackingMode(val displayName: String, val requiresAlignment: Boolean) {
    DEEP_SKY_AVERAGE("Ngân Hà (Deep Sky)", requiresAlignment = true),
    KAPPA_SIGMA("Khử Vệ Tinh (Kappa-Sigma)", requiresAlignment = true),
    STAR_TRAILS("Vệt Sao (Star Trails)", requiresAlignment = false)
}
