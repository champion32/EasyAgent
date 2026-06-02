package com.easyagent.planner

object PlannerFactory {

    fun create(mode: PlannerMode): Planner {
        return when (mode) {
            PlannerMode.FUNCTION_CALLING -> FunctionCallingPlanner()
            PlannerMode.REACT -> ReActPlanner()
        }
    }
}
