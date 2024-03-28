package com.lucas.automateddutyplanner.data

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import java.util.Date

enum class Constraint
{
    EXCUSE_STAY_IN, // only weekend half day
    CHAOKENG_WARRIOR, // treat as weekend FD preference
    EXCUSE_DUTY,
    PREFER_WEEKEND,
    PREFER_WEEKDAY,
    PREFER_MONDAY,
    PREFER_TUESDAY,
    PREFER_WEDNESDAY,
    PREFER_THURSDAY,
    PREFER_FRIDAY,
    PREFER_SATURDAY,
    PREFER_SUNDAY
}

@Serializable
data class DutyAssistant(val name: String, var constraints: MutableList<Constraint> = mutableListOf(), var unableDates: MutableList<LocalDate> = mutableListOf(), var assignedDates: MutableList<LocalDate> = mutableListOf(), var assignedReserve: MutableList<LocalDate> = mutableListOf(), var priority: Int = 10, var isTouched: Boolean = false, var persistentPriority: Int = 10, var tempPriority: Int = 10){
    fun changeMonths(){
        // swap over the priorities such that it apply the modified priorities from this month
        this.persistentPriority = this.tempPriority
        this.assignedReserve = mutableListOf()
        this.assignedDates = mutableListOf()
        this.unableDates = mutableListOf()
        // this value will get updated anyway, so just reset it to default so it saves space in the exported json
        this.priority = 10
    }
}
