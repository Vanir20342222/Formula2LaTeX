package com.formula2latex.data.provider

import com.formula2latex.domain.model.FormulaInput
import com.formula2latex.domain.model.FormulaProvider
import com.formula2latex.domain.model.FormulaResult
import com.formula2latex.domain.model.ModelInfo
import com.formula2latex.domain.model.ProviderConfig

class DebugFakeFormulaProvider : FormulaProvider {
    override suspend fun listModels(config: ProviderConfig) = Result.success(listOf(ModelInfo("fake/formula")))
    override suspend fun convert(config: ProviderConfig, modelId: String, input: FormulaInput) =
        Result.success(FormulaResult("\\frac{x^2}{2}", 1.0))
}
