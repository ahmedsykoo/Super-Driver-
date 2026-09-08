package com.superdriver.app.zones

data class AvoidZoneRule(
    val id: String,
    val name: String,
    val keywords: List<String>,
    val policy: AvoidZonePolicy,
    val activeFrom: String? = null,
    val activeTo: String? = null,
    val enabled: Boolean = true
)

