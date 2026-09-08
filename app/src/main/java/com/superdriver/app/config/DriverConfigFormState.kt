package com.superdriver.app.config

data class DriverConfigFormState(
    val minEgpPerKm: String = "",
    val minEgpPerHour: String = "",
    val minNetProfit: String = "",
    val costPerKm: String = "",
    val costPerMinute: String = "",
    val reviewTolerancePercent: String = ""
) {
    fun update(
        field: DriverConfigFormField,
        value: String
    ): DriverConfigFormState {
        return when (field) {
            DriverConfigFormField.MIN_EGP_PER_KM -> copy(minEgpPerKm = value)
            DriverConfigFormField.MIN_EGP_PER_HOUR -> copy(minEgpPerHour = value)
            DriverConfigFormField.MIN_NET_PROFIT -> copy(minNetProfit = value)
            DriverConfigFormField.COST_PER_KM -> copy(costPerKm = value)
            DriverConfigFormField.COST_PER_MINUTE -> copy(costPerMinute = value)
            DriverConfigFormField.REVIEW_TOLERANCE_PERCENT -> copy(reviewTolerancePercent = value)
        }
    }

    fun toDriverConfig(
        currentConfig: DriverConfig
    ): DriverConfigFormValidationResult {
        val minEgpPerKm = parseRequiredNonNegative(minEgpPerKm, "الحد الأدنى جنيه/كم")
        val minEgpPerHour = parseRequiredNonNegative(minEgpPerHour, "الحد الأدنى جنيه/ساعة")
        val minNetProfit = parseRequiredNonNegative(minNetProfit, "الحد الأدنى للربح")
        val costPerKm = parseRequiredNonNegative(costPerKm, "التكلفة لكل كم")
        val costPerMinute = parseRequiredNonNegative(costPerMinute, "التكلفة لكل دقيقة")
        val reviewTolerancePercent = parseRequiredNonNegative(reviewTolerancePercent, "هامش المراجعة")

        val error = listOf(
            minEgpPerKm,
            minEgpPerHour,
            minNetProfit,
            costPerKm,
            costPerMinute,
            reviewTolerancePercent
        ).firstOrNull { it.errorMessage != null }?.errorMessage

        if (error != null) {
            return DriverConfigFormValidationResult.Invalid(error)
        }

        return DriverConfigFormValidationResult.Valid(
            currentConfig.copy(
                minEgpPerKm = minEgpPerKm.value ?: currentConfig.minEgpPerKm,
                minEgpPerHour = minEgpPerHour.value ?: currentConfig.minEgpPerHour,
                minNetProfit = minNetProfit.value ?: currentConfig.minNetProfit,
                costPerKm = costPerKm.value ?: currentConfig.costPerKm,
                costPerMinute = costPerMinute.value ?: currentConfig.costPerMinute,
                reviewTolerancePercent = reviewTolerancePercent.value ?: currentConfig.reviewTolerancePercent
            )
        )
    }

    private fun parseRequiredNonNegative(
        rawValue: String,
        label: String
    ): ParsedConfigValue {
        val normalized = rawValue.trim().replace(",", ".")
        if (normalized.isBlank()) {
            return ParsedConfigValue(errorMessage = "$label لا يمكن تركه فارغًا")
        }

        val value = normalized.toDoubleOrNull()
            ?: return ParsedConfigValue(errorMessage = "$label يجب أن يكون رقمًا")
        if (value < 0.0) {
            return ParsedConfigValue(errorMessage = "$label لا يمكن أن يكون سالبًا")
        }

        return ParsedConfigValue(value = value)
    }

    private data class ParsedConfigValue(
        val value: Double? = null,
        val errorMessage: String? = null
    )

    companion object {
        fun fromConfig(config: DriverConfig): DriverConfigFormState {
            return DriverConfigFormState(
                minEgpPerKm = config.minEgpPerKm.toInputText(),
                minEgpPerHour = config.minEgpPerHour.toInputText(),
                minNetProfit = config.minNetProfit.toInputText(),
                costPerKm = config.costPerKm.toInputText(),
                costPerMinute = config.costPerMinute.toInputText(),
                reviewTolerancePercent = config.reviewTolerancePercent.toInputText()
            )
        }

        private fun Double.toInputText(): String {
            return if (this % 1.0 == 0.0) {
                toLong().toString()
            } else {
                toString()
            }
        }
    }
}

sealed interface DriverConfigFormValidationResult {
    data class Valid(val config: DriverConfig) : DriverConfigFormValidationResult
    data class Invalid(val message: String) : DriverConfigFormValidationResult
}
