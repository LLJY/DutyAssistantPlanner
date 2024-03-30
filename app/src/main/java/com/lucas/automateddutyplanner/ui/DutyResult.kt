package com.lucas.automateddutyplanner.ui

import kotlinx.datetime.LocalDate
import java.time.format.DateTimeFormatter

data class DutyResult(var assigneeNames :Pair<String, String?>, var reserveName: String?, var assigned: LocalDate )
