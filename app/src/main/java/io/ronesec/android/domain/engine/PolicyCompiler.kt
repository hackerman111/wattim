package io.ronesec.android.domain.engine

class PolicyCompiler {

    fun compile(state: RuntimeState): Map<String, CompiledAppPolicy> {
        val compiledMap = mutableMapOf<String, CompiledAppPolicy>()

        for ((pkg, target) in state.targets) {
            val appSchedules = state.blockSchedules
                .filter { it.enabled && it.packages.contains(pkg) }
                .map { schedule ->
                    NormalizedScheduleInterval(
                        schedule = schedule,
                        scheduleType = schedule.scheduleType,
                        isOvernight = schedule.start > schedule.end,
                        start = schedule.start,
                        end = schedule.end
                    )
                }

            compiledMap[pkg] = CompiledAppPolicy(
                packageName = pkg,
                enabled = target.enabled,
                baseIntervention = target.intervention,
                schedules = appSchedules
            )
        }

        return compiledMap
    }
}
