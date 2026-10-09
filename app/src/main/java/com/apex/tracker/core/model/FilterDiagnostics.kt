package com.apex.tracker.core.model

data class FilterDiagnostics(
    val posVarEast: Double,
    val posVarNorth: Double,
    val velVarEast: Double,
    val velVarNorth: Double,
    val mahalanobisD2: Double,
    val outliersRejectedCount: Int,
    val isDeadbandActive: Boolean
)
