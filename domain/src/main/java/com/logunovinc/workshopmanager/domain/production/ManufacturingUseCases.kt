package com.logunovinc.workshopmanager.domain.production

import java.math.BigDecimal
import com.logunovinc.workshopmanager.domain.*
import com.logunovinc.workshopmanager.domain.task.*
import com.logunovinc.workshopmanager.domain.pending.CommandOutcome

object ManufacturingValidator {
    fun prepare(task: Task, draft: ManufacturingDraft): Outcome<ManufacturingCommand> {
        fun invalid(message: String) = Outcome.Failure(AppError(ErrorKind.VALIDATION, message))
        val descriptor = task.production
        if (task.type != TaskType.MANUFACTURE_PRODUCT || descriptor?.supported != true || !task.canExecute)
            return Outcome.Failure(AppError(ErrorKind.INCOMPATIBLE_SERVER, "Производство требует поддерживаемого серверного контракта v2", "PRODUCTION_CONTRACT_UPGRADE_REQUIRED"))
        val outputLine = task.lines.filter { it.role == "output" }.singleOrNull() ?: return invalid("Не определена единственная строка выпуска задания")
        if (draft.output.taskLineId != outputLine.id) return invalid("Выпуск должен ссылаться на task_line_id изделия этого задания")
        val output = when (val actual = ActualLinesValidator.validate(task, listOf(draft.output))) {
            is Outcome.Failure -> return actual
            is Outcome.Success -> actual.value.singleOrNull() ?: return invalid("Укажите положительный физический выпуск")
        }
        if (output.quantity != null && output.quantity >= BigDecimal("10000000000")) return invalid("Выпуск должен быть меньше 10000000000")
        if (descriptor.status == RecipeStatus.READY) {
            if (draft.materials.isNotEmpty()) return invalid("Расход по готовой норме рассчитывает сервер; ручная замена материалов запрещена")
            return Outcome.Success(ManufacturingCommand.ByRecipe(task.version, descriptor.recipeVersion, output))
        }
        if (draft.materials.isEmpty() || draft.materials.size > 499) return invalid("Для первого прототипа укажите от 1 до 499 фактически затраченных материалов")
        val materials = mutableListOf<PrototypeMaterial>()
        val identities = mutableSetOf<Pair<Long, String?>>()
        for ((index, raw) in draft.materials.withIndex()) {
            val label = "Материал ${index + 1}"
            if (raw.productId <= 0) return invalid("$label: выберите товар из каталога")
            val sheets = if (raw.sheets.isBlank()) null else QuantityInputParser.sheets(raw.sheets)?.takeIf { it > 0 }
                ?: return invalid("$label: число листов от 1 до 2147483647")
            val quantity = if (raw.quantity.isBlank()) null else QuantityInputParser.quantity(raw.quantity)
                ?: return invalid("$label: количество должно иметь не более 4 десятичных знаков")
            if (quantity != null && quantity >= BigDecimal("10000000000")) return invalid("$label: количество должно быть меньше 10000000000")
            val length = if (raw.sheetLength.isBlank()) null else parseLength(raw.sheetLength)
                ?: return invalid("$label: длина положительная, меньше 10000000, не более 3 десятичных знаков")
            if (sheets == null && (quantity == null || quantity.signum() <= 0 || length != null)) return invalid("$label: без листов нужно положительное базовое количество, без длины")
            if (sheets != null && length == null) return invalid("$label: укажите длину листов; базовое количество можно оставить пустым")
            val unitName = raw.unitName.trim().takeIf(String::isNotEmpty)
            val unitQuantity = if (raw.unitQuantity.isBlank()) null else QuantityInputParser.quantity(raw.unitQuantity)?.takeIf { it.signum() > 0 }
                ?: return invalid("$label: количество в выбранной единице должно быть положительным, до 4 десятичных знаков")
            if ((unitName == null) != (unitQuantity == null) || (unitName?.length ?: 0) > 40) return invalid("$label: единица (до 40 символов) и её количество задаются вместе")
            if (unitQuantity != null && unitQuantity >= BigDecimal("10000000000")) return invalid("$label: количество в единице должно быть меньше 10000000000")
            if (!identities.add(raw.productId to length?.stripTrailingZeros()?.toPlainString())) return invalid("$label: один товар и длина не должны повторяться")
            materials += PrototypeMaterial(raw.productId, quantity, sheets, length, unitName, unitQuantity)
        }
        return Outcome.Success(ManufacturingCommand.Prototype(task.version, descriptor.recipeVersion, output, materials.toList()))
    }
    private fun parseLength(raw: String): BigDecimal? = raw.trim().replace(',', '.').takeIf { it.length <= 64 && it.matches(Regex("[0-9]+(?:[.][0-9]{1,3})?")) }
        ?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 && it < BigDecimal("10000000") }
    fun validateCommand(task: Task, command: ManufacturingCommand): Outcome<ManufacturingCommand> {
        val materials = (command as? ManufacturingCommand.Prototype)?.materials.orEmpty().map { PrototypeMaterialInput(it.productId,
            it.quantity?.toPlainString().orEmpty(), it.sheets?.toString().orEmpty(), it.sheetLength?.toPlainString().orEmpty(), it.unitName.orEmpty(), it.unitQuantity?.toPlainString().orEmpty()) }
        val draft = ManufacturingDraft(ActualLineInput(command.output.taskLineId, command.output.quantity?.toPlainString().orEmpty(), command.output.sheets?.toString().orEmpty()), materials)
        return when (val result = prepare(task, draft)) {
            is Outcome.Failure -> result
            is Outcome.Success -> if (result.value == command) result else Outcome.Failure(AppError(ErrorKind.CONFLICT, "Версия задания или нормы изменилась. Получите новый расчёт", "RECIPE_VERSION_CONFLICT"))
        }
    }
    fun previewMatches(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview): Boolean {
        val line = task.lines.singleOrNull { it.id == command.output.taskLineId } ?: return false
        val proposalMatches = if (!preview.canExecute) true else when (command) {
            is ManufacturingCommand.ByRecipe -> preview.proposedRecipe == null
            is ManufacturingCommand.Prototype -> preview.proposedRecipe?.let { proposed ->
                proposed.supported && proposed.mode == ProductionMode.PROTOTYPE_REQUIRED && proposed.status == RecipeStatus.READY && proposed.recipeVersion == 1L &&
                    (preview.output.sheets?.let { it == proposed.basisOutput?.sheets } ?: (proposed.basisOutput?.quantity?.compareTo(preview.output.quantity) == 0))
            } == true
        }
        return proposalMatches && preview.taskId == task.id && preview.taskVersion == command.taskVersion && preview.recipeVersion == command.recipeVersion &&
            preview.mode == command.mode && preview.output.taskLineId == command.output.taskLineId && preview.output.productId == line.productId &&
            (command.output.sheets?.let { it == preview.output.sheets } ?: (command.output.quantity?.compareTo(preview.output.quantity) == 0)) &&
            preview.materials.none { it.dimensionAvailability == DimensionAvailability.UNSUPPORTED }
    }
}
/** Validates user facts and selects prototype or by-recipe mode solely from the server descriptor. */
class PrepareManufacturingUseCase {
    operator fun invoke(task: Task, draft: ManufacturingDraft): Outcome<ManufacturingCommand> = ManufacturingValidator.prepare(task, draft)
}
/** Requests an advisory server calculation; never computes or reserves recipe quantities on the phone. */
class PreviewManufacturingUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task, command: ManufacturingCommand): Outcome<ManufacturingPreview> = when (val validated = ManufacturingValidator.validateCommand(task, command)) {
        is Outcome.Failure -> validated
        is Outcome.Success -> when (val preview = repository.manufacturingPreview(task, command)) {
            is Outcome.Failure -> preview
            is Outcome.Success -> if (ManufacturingValidator.previewMatches(task, command, preview.value)) preview
                else Outcome.Failure(AppError(ErrorKind.INCOMPATIBLE_SERVER, "Расчёт сервера не соответствует заданию и его версиям"))
        }
    }
}
/** Commits only a reviewed matching preview; the backend repeats validation atomically under its locks. */
class ExecuteManufacturingUseCase(private val repository: TaskRepository) {
    suspend operator fun invoke(task: Task, command: ManufacturingCommand, preview: ManufacturingPreview, comment: String): CommandOutcome {
        val validated = ManufacturingValidator.validateCommand(task, command)
        if (validated is Outcome.Failure) return CommandOutcome.Rejected(null, validated.error)
        if (comment.length > 2000) return CommandOutcome.Rejected(null, AppError(ErrorKind.VALIDATION, "Комментарий должен быть не длиннее 2000 символов"))
        if (!preview.canExecute || !ManufacturingValidator.previewMatches(task, command, preview))
            return CommandOutcome.Rejected(null, AppError(ErrorKind.CONFLICT, "Нужен актуальный разрешающий расчёт сервера"))
        return repository.manufacture(task, command, preview, comment)
    }
}
