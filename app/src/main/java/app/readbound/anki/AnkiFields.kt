package app.readbound.anki

internal fun buildAnkiFields(
    front: String,
    back: String,
    fieldCount: Int,
    frontIndex: Int,
    backIndex: Int,
): List<String> {
    require(fieldCount >= 2) { "The Anki note type must contain at least two fields" }
    require(frontIndex != backIndex && frontIndex in 0 until fieldCount && backIndex in 0 until fieldCount) {
        "Choose separate front and back fields"
    }
    return MutableList(fieldCount) { "" }.apply {
        this[frontIndex] = front
        this[backIndex] = back
    }
}

internal fun isEligibleSingleCardModel(fieldCount: Int, cardTemplateCount: Int): Boolean =
    fieldCount >= 2 && cardTemplateCount == 1
