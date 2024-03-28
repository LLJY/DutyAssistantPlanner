package com.lucas.automateddutyplanner.data

import kotlinx.datetime.LocalDate
import kotlin.math.abs


fun dutyPlanningMethodByDate(daS: List<DutyAssistant>, daysOfMonth: List<LocalDate>, isReserve: Boolean, writePersistentPriority: Boolean = true): List<DutyAssistant>{
    val priorityDecreaseOnAssign = 10
    var dutyAssistants = daS
    // apply priority if not planning for reserve
    if(!isReserve){
        dutyAssistants = dutyAssistants.map {
            it.priority = it.persistentPriority
            it
        }
    }else{
        // reset the priority to 10 if planning for reserve
        dutyAssistants = dutyAssistants.map {
            it.priority = 10
            it
        }
    }
    for (day in daysOfMonth){
        println(day)
        val isWeekend = day.dayOfWeek == kotlinx.datetime.DayOfWeek.SATURDAY || day.dayOfWeek == kotlinx.datetime.DayOfWeek.SUNDAY
        dutyAssistants = if(isWeekend){
            // weekend set priorities
            dutyAssistants.map {
                if(it.constraints.contains(Constraint.EXCUSE_STAY_IN) || it.constraints.contains(Constraint.PREFER_WEEKEND)){
                    it.priority += 1
                    it.isTouched = true
                }
                if(it.constraints.contains(Constraint.CHAOKENG_WARRIOR)){
                    it.priority += 1
                    it.isTouched = true
                }
                if(day.dayOfWeek == kotlinx.datetime.DayOfWeek.SATURDAY && it.constraints.contains(Constraint.PREFER_SATURDAY)){
                    it.priority += 1
                    it.isTouched = true
                }
                else if(day.dayOfWeek == kotlinx.datetime.DayOfWeek.SUNDAY && it.constraints.contains(Constraint.PREFER_SUNDAY)){
                    it.priority += 1
                    it.isTouched = true
                }
                it
            }
        }else{
            // weekday
            dutyAssistants.map{
                if(it.constraints.contains(Constraint.PREFER_WEEKDAY)){
                    it.priority += 1
                    it.isTouched = true
                }
                else if(day.dayOfWeek == kotlinx.datetime.DayOfWeek.MONDAY && it.constraints.contains(Constraint.PREFER_MONDAY)){
                    it.priority += 1
                    it.isTouched = true
                }
                else if(day.dayOfWeek == kotlinx.datetime.DayOfWeek.TUESDAY && it.constraints.contains(Constraint.PREFER_TUESDAY)){
                    it.priority += 1
                    it.isTouched = true
                }
                else if(day.dayOfWeek == kotlinx.datetime.DayOfWeek.WEDNESDAY && it.constraints.contains(Constraint.PREFER_WEDNESDAY)){
                    it.priority += 1
                    it.isTouched = true
                }
                else if(day.dayOfWeek == kotlinx.datetime.DayOfWeek.THURSDAY && it.constraints.contains(Constraint.PREFER_THURSDAY)){
                    it.priority += 1
                    it.isTouched = true
                }
                else if(day.dayOfWeek == kotlinx.datetime.DayOfWeek.FRIDAY && it.constraints.contains(Constraint.PREFER_FRIDAY)){
                    it.priority += 1
                    it.isTouched = true
                }
                it
            }
        }
        dutyAssistants = dutyAssistants.shuffled()
        dutyAssistants = dutyAssistants.sortedByDescending { it.priority }
        try {
            if(isReserve){
                // the following conditions
                // can be any guy with no duties assigned, OR any guy with no duties within 1 day, not excused stay in and is available on that day. Reserve duty must also not be on the same day as any previous duty
                val dutyGuyIdx = dutyAssistants.indexOf(dutyAssistants.find { (it.assignedReserve.isEmpty() || abs(day.toEpochDays() - it.assignedReserve.last().toEpochDays()) > 1) && Constraint.EXCUSE_STAY_IN !in it.constraints && day !in it.unableDates && !it.assignedDates.any {a-> a == day } })
                if(dutyGuyIdx != -1) {
                    dutyAssistants[dutyGuyIdx].assignedReserve.add(day)
                    dutyAssistants[dutyGuyIdx].priority -= priorityDecreaseOnAssign
                }
            }
            else if (isWeekend) {
                val dutyGuyIdx = dutyAssistants.indexOf(dutyAssistants.find { (it.assignedDates.isEmpty() || abs(day.toEpochDays() - it.assignedDates.last().toEpochDays()) > 1) && day !in it.unableDates })
                if(dutyGuyIdx != -1) {
                    if (dutyAssistants[dutyGuyIdx].constraints.contains(Constraint.EXCUSE_STAY_IN)) {
                        dutyAssistants[dutyGuyIdx].assignedDates.add(day)
                        val second =
                            dutyAssistants.find { Constraint.EXCUSE_STAY_IN !in it.constraints &&  (it.assignedDates.isEmpty() || abs(day.toEpochDays() - it.assignedDates.last().toEpochDays()) > 1) && day !in it.unableDates }
                        val pmGuyIdx = dutyAssistants.indexOf(second)
                        dutyAssistants[pmGuyIdx].assignedDates.add(day)
                        dutyAssistants[pmGuyIdx].priority -= priorityDecreaseOnAssign

                    } else {
                        dutyAssistants[dutyGuyIdx].assignedDates.add(day)
                        dutyAssistants[dutyGuyIdx].priority -= priorityDecreaseOnAssign
                    }
                }
            } else {
                val dutyGuyIdx = dutyAssistants.indexOf(dutyAssistants.find { (it.assignedDates.isEmpty() || abs(day.toEpochDays() - it.assignedDates.last().toEpochDays()) > 1) && Constraint.EXCUSE_STAY_IN !in it.constraints && day !in it.unableDates})
                if(dutyGuyIdx != -1) {
                    dutyAssistants[dutyGuyIdx]
                        .assignedDates.add(day)
                    dutyAssistants[dutyGuyIdx]
                        .priority -= priorityDecreaseOnAssign
                }
            }

            // reset the priorities
            dutyAssistants = dutyAssistants.map {
                if(it.isTouched){
                    it.priority -= 1
                    it.isTouched = false
                }
            if(isReserve){
                dutyAssistants = dutyAssistants.map {
                    it.priority = it.persistentPriority
                    it
                }
            }else{
                if(writePersistentPriority) {
                    dutyAssistants = dutyAssistants.map {
                        it.persistentPriority = it.priority
                        it
                    }
                }
            }
            it}
        }catch (e: Exception){
            println("error: $e")
        }
    }
    return dutyAssistants
}