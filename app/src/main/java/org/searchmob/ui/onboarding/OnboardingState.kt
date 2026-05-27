package org.searchmob.ui.onboarding

/** Ordered pages of the first-run wizard. */
enum class OnboardingStep {
    WELCOME,
    PERMISSIONS,
    DEFAULT_SEARCH,
    PRIVACY,
}

/**
 * Pure step-navigation logic for the wizard pager, extracted so it can be unit-tested without Compose.
 *
 * [index] is the current page position into [OnboardingStep.entries]. [next] advances until the last
 * page; [back] retreats until the first. [isFirst]/[isLast] drive the Back/Next/Finish controls.
 */
data class OnboardingProgress(
    val index: Int = 0,
) {
    init {
        require(index in OnboardingStep.entries.indices) { "step index out of range: $index" }
    }

    val step: OnboardingStep get() = OnboardingStep.entries[index]
    val isFirst: Boolean get() = index == 0
    val isLast: Boolean get() = index == OnboardingStep.entries.lastIndex
    val pageCount: Int get() = OnboardingStep.entries.size

    fun next(): OnboardingProgress = if (isLast) this else OnboardingProgress(index + 1)

    fun back(): OnboardingProgress = if (isFirst) this else OnboardingProgress(index - 1)
}
