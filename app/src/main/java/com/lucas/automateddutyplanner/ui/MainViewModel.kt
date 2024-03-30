package com.lucas.automateddutyplanner.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lucas.automateddutyplanner.data.Constraint
import com.lucas.automateddutyplanner.data.DutyAssistant
import com.lucas.automateddutyplanner.data.dutyPlanningMethodByDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(application: Application): AndroidViewModel(application) {
    private val _dutyAssistantList: MutableStateFlow<List<DutyAssistant>> = MutableStateFlow(listOf())
    private var _dutyAssistants: MutableList<DutyAssistant> = mutableListOf()
    val dutyAssistantList = _dutyAssistantList.asStateFlow()

    val selectedYear = java.time.LocalDate.now().year
    var selectedMonth = 0

    private var _publicHolidaysFlow: MutableStateFlow<List<LocalDate>> = MutableStateFlow(mutableListOf())
    var publicHolidaysFlow = _publicHolidaysFlow.asStateFlow()
    private var _publicHolidays: MutableList<LocalDate> = mutableListOf()

    private val _dutyPlannedResults: MutableStateFlow<List<DutyResult>> = MutableStateFlow(listOf())
    private var _dutyPlannedList: List<Pair<String, LocalDate>> = listOf()
    val dutyPlannedResults = _dutyPlannedResults.asStateFlow()

    var dutyReserveList: List<Pair<String, LocalDate>> = listOf()

    lateinit var sharedPrefs: SharedPreferences

    private suspend fun _initialiseValues(){
        sharedPrefs = getApplication<Application>().getSharedPreferences("somekeylol", Context.MODE_PRIVATE)
        selectedMonth = sharedPrefs.getInt("month", 1)
        val publicHolsRawStr = sharedPrefs.getString("publicHolidays", "")
        if(!publicHolsRawStr.isNullOrEmpty()) {
            _publicHolidays = Json.decodeFromString(publicHolsRawStr)
            if(_publicHolidays.any { it.year != selectedYear }){
                // if we changed over to a new year, clear the public holidays list automatically
                _publicHolidays = mutableListOf()
                sharedPrefs.edit().putString("publicHolidays", Json.encodeToString(_publicHolidays)).apply()
            }
            _publicHolidays.sort()
            _publicHolidaysFlow.emit(_publicHolidays)
        }
        getSavedList()
        getSavedPlannedDuty()
    }

    fun initialiseValues(){
        viewModelScope.launch {
            _initialiseValues()
        }
    }

    suspend fun changeMonth(month: Int){
        // reset all values
        selectedMonth = month
        _dutyAssistants = _dutyAssistants.map {
            it.changeMonths()
            it
        }.toMutableList()
        _dutyPlannedList = listOf()
        dutyReserveList = listOf()
        _dutyAssistantList.emit(_dutyAssistants)
        _dutyPlannedResults.emit(displayDutyPersonnel())
        saveModifiedList()
        savePlannedDuty()
    }

    suspend fun getSavedList(){
         val rawStr = sharedPrefs.getString("daList", "")
        if(!rawStr.isNullOrBlank())
            _dutyAssistants = Json.decodeFromString(rawStr)
        _dutyAssistants.sortBy { it.name }
        _dutyAssistantList.emit(_dutyAssistants)
    }

    suspend fun getSavedPlannedDuty(){
        val rawStr = sharedPrefs.getString("plannedList", "")
        val reserveRawStr = sharedPrefs.getString("reserveList", "")
        if(!rawStr.isNullOrBlank())
            _dutyPlannedList = Json.decodeFromString(rawStr)
        _dutyPlannedList = _dutyPlannedList.sortedBy { it.second }

        if(!reserveRawStr.isNullOrBlank())
            dutyReserveList = Json.decodeFromString(reserveRawStr)
        dutyReserveList = dutyReserveList.sortedBy { it.second }

        _dutyPlannedResults.emit(displayDutyPersonnel())
    }

    private suspend fun savePlannedDuty() = withContext(Dispatchers.IO){
        sharedPrefs.edit().putString("plannedList", Json.encodeToString(_dutyPlannedList)).commit()
        sharedPrefs.edit().putString("reserveList", Json.encodeToString(dutyReserveList)).commit()
    }

    private suspend fun saveModifiedList() = withContext(Dispatchers.IO){
        sharedPrefs.edit().putString("daList", Json.encodeToString(_dutyAssistants)).commit()
        sharedPrefs.edit().putInt("month", selectedMonth).commit()
    }

    suspend fun modifyDutyAssistant(da: DutyAssistant, idx: Int){
        _dutyAssistants[idx] = da
        _dutyAssistantList.emit(_dutyAssistants)
        saveModifiedList()
    }

    suspend fun deleteDaFromList(idx: Int){
        _dutyAssistants.removeAt(idx)
        _dutyAssistantList.emit(_dutyAssistants)
        saveModifiedList()
    }

    suspend fun addDaToList(da: DutyAssistant){
        _dutyAssistants.add(da)
        _dutyAssistants.sortBy { it.name }
        _dutyAssistantList.emit(_dutyAssistants)
        saveModifiedList()
    }

    suspend fun planDuty(isReserve: Boolean = false){
        // clear all previously assigned dates
        _dutyAssistants = _dutyAssistants.map {
            it.assignedDates = mutableListOf()
            it
        }.toMutableList()

        val results = dutyPlanningMethodByDate(_dutyAssistants, getDaysInMonth(selectedYear,selectedMonth), false, _publicHolidays)
        _dutyAssistants = results.toMutableList()
        _dutyAssistantList.emit(_dutyAssistants)
        saveModifiedList()
        val returnList: MutableList<Pair<String, LocalDate>> = mutableListOf()
        results.forEach { da ->
            da.assignedDates.forEach{
                val daName = if(Constraint.EXCUSE_STAY_IN in da.constraints) da.name + " (AM)" else da.name
                returnList.add(Pair(daName, it))
            }
        }
        _dutyPlannedList = returnList.toList()
        _dutyPlannedList = _dutyPlannedList.sortedBy { it.second }
        _dutyPlannedResults.emit(displayDutyPersonnel())
        if(isReserve){
            planReserve()
        }
        savePlannedDuty()
    }

    fun planReserve(){
        val results = dutyPlanningMethodByDate(_dutyAssistants, getDaysInMonth(selectedYear,selectedMonth),true, _publicHolidays)
        val returnList: MutableList<Pair<String, LocalDate>> = mutableListOf()
        results.forEach { da ->
            da.assignedReserve.forEach{
                returnList.add(Pair(da.name, it))
            }
        }
        dutyReserveList = dutyReserveList.sortedBy { it.second }
        dutyReserveList = returnList
    }

    private fun displayDutyPersonnel(): List<DutyResult> {
        val returnList: MutableList<DutyResult> = mutableListOf()

        if (_dutyPlannedList.isNotEmpty()) {
            var i = 0
            while (i < _dutyPlannedList.size) {
                val assigned = mutableListOf<String?>()
                val dutyResult = _dutyPlannedList[i]
                assigned.add(dutyResult.first)

                if (dutyResult.second.dayOfWeek == DayOfWeek.SUNDAY || dutyResult.second.dayOfWeek == DayOfWeek.SATURDAY || dutyResult.second in _publicHolidays) {
                    val dutyDateAssignees =
                        _dutyPlannedList.filter { assignee -> assignee.second == dutyResult.second }
                    if (dutyDateAssignees.count() > 1) {
                        assigned.add(dutyDateAssignees.first { assignee -> assignee.first != dutyResult.first }.first)
                        i++
                        assigned.sortByDescending { it?.contains("(AM)") }

                    } else {
                        assigned.add(null)
                    }
                } else {
                    assigned.add(null)
                }
                val reserve = if (dutyReserveList.isNotEmpty()) {

                    dutyReserveList.first { reservee -> reservee.second == dutyResult.second }.first
                } else {
                    null
                }
                returnList.add(
                    DutyResult(
                        assigneeNames = Pair<String, String?>(assigned[0]!!, assigned[1]),
                        reserveName = reserve,
                        assigned = dutyResult.second
                    )
                )
                i++
            }
        }
        return returnList
    }

    suspend fun createCsvFromDuty(directory: File, reserve: Boolean): File = withContext(Dispatchers.IO){
        val formatter = DateTimeFormatter.ofPattern("dd-MMMM,EEE")
        val file = File(directory, "duties.csv")
        var CSV_HEADER = "Date,Day,Main"
        if(reserve){
            CSV_HEADER+=",Reserve"
        }
        var fileWriter: FileWriter? = null

        try {
            fileWriter = FileWriter(file)
            fileWriter.append("${CSV_HEADER}\n")
            _dutyPlannedList.forEach {
                fileWriter.append("${formatter.format(it.second.toJavaLocalDate())},${it.first}")
                if(reserve){
                    fileWriter.append(",${dutyReserveList.first { res -> it.second == res.second }.first}")
                }
                fileWriter.append("\n")
            }
            fileWriter.flush()
            fileWriter.close()
        } catch (e: Exception) {
            println("Writing CSV error!")
            e.printStackTrace()
        }

        return@withContext file
    }

    suspend fun createJsonFromDaConfig(outputStream: OutputStream) = withContext(Dispatchers.IO){
        try {
            val rawStr = Json.encodeToString(_dutyAssistants)
            val bytes: ByteArray = rawStr.toByteArray(StandardCharsets.UTF_8)
            outputStream.write(bytes)
        } catch (e: Exception) {
            println("Writing CSV error!")
            e.printStackTrace()
        }
    }

    suspend fun editDaName(index: Int, name: String){
        _dutyAssistants[index].name = name
        saveModifiedList()
        _dutyAssistantList.emit(_dutyAssistants)
    }

    suspend fun importJsonToDaConfig(inputStream: InputStream): Boolean = withContext(Dispatchers.IO){
        try {
            val rawStr = inputStream.bufferedReader().use { it.readText() }
            if(rawStr != null){
                val daList=Json.decodeFromString<MutableList<DutyAssistant>>(rawStr)
                _dutyAssistants = daList.sortedBy { it.name }.toMutableList()
                _dutyAssistantList.emit(_dutyAssistants)
                saveModifiedList()
            }
        }catch (e: Exception){
            e.printStackTrace()
            return@withContext false
        }
        return@withContext true
    }
    suspend fun setPublicHolidays(holidays: List<LocalDate>){
        _publicHolidays = mutableListOf()
        _publicHolidays = holidays.toMutableList()
        _publicHolidays.sort()
        _publicHolidaysFlow.emit(_publicHolidays)
        sharedPrefs.edit().putString("publicHolidays", Json.encodeToString(holidays)).apply()
    }

    suspend fun importIcsToLocalDate(inputStream: InputStream): Boolean = withContext(Dispatchers.IO){
        val format = LocalDate.Formats.ISO_BASIC
        val returnList: MutableList<LocalDate> = mutableListOf()
        try {
            val dateLine = inputStream.bufferedReader().use { it.readLines() }.filter { it.contains("DTSTART;") }
            dateLine.forEach {
                val dateStr = it.substring(19)
                returnList.add(LocalDate.parse(dateStr, format))
            }
            setPublicHolidays(returnList)
            Log.e("FFFF", _publicHolidays.toString())
        }catch (e: Exception){
            e.printStackTrace()
            return@withContext false
        }
        return@withContext true
    }

    private fun getDaysInMonth(year: Int, month: Int): List<LocalDate> {
        val firstDayOfMonth = LocalDate(year, month, 1)
        val daysInMonth = firstDayOfMonth.month.length((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0))


        return (1..daysInMonth).map { dayOfMonth ->
            firstDayOfMonth.plus(DatePeriod(days = dayOfMonth -1))
        }
    }
}