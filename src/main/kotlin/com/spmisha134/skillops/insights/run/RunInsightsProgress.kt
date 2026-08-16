package com.spmisha134.skillops.insights.run

interface RunInsightsProgress {
    fun update(message: String, completed: Int, total: Int)

    fun checkCanceled()

    fun withPrefix(prefix: String): RunInsightsProgress = object : RunInsightsProgress {
        override fun update(message: String, completed: Int, total: Int) {
            this@RunInsightsProgress.update("$prefix — $message", completed, total)
        }

        override fun checkCanceled() {
            this@RunInsightsProgress.checkCanceled()
        }
    }

    companion object {
        val NONE: RunInsightsProgress = object : RunInsightsProgress {
            override fun update(message: String, completed: Int, total: Int) = Unit

            override fun checkCanceled() = Unit
        }
    }
}

class RunInsightsCanceledException : RuntimeException()
