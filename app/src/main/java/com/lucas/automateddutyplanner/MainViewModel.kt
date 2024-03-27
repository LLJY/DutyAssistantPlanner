package com.lucas.automateddutyplanner

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
import kotlinx.datetime.plus
import kotlinx.datetime.toJavaLocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import javax.inject.Inject


@HiltViewModel
class MainViewModel @Inject constructor(application: Application): AndroidViewModel(application) {
    private val _dutyAssistantList: MutableStateFlow<List<DutyAssistant>> = MutableStateFlow(listOf())
    private var _dutyAssistants: MutableList<DutyAssistant> = mutableListOf()
    val dutyAssistantList = _dutyAssistantList.asStateFlow()

    val selectedYear = java.time.LocalDate.now().year
    var selectedMonth = 0

    private val _dutyPlannedResults: MutableStateFlow<List<Pair<String, LocalDate>>> = MutableStateFlow(listOf())
    private var _dutyPlannedList: List<Pair<String, LocalDate>> = listOf()
    val dutyPlannedResults = _dutyPlannedResults.asStateFlow()

    var dutyReserveList: List<Pair<String, LocalDate>> = listOf()

    lateinit var sharedPrefs: SharedPreferences

    private suspend fun _initialiseValues(){
        sharedPrefs = getApplication<Application>().getSharedPreferences("somekeylol", Context.MODE_PRIVATE)
        selectedMonth = sharedPrefs.getInt("month", 1)
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
            it.unableDates = mutableListOf()
            it.assignedDates = mutableListOf()
            it.assignedReserve = mutableListOf()
            it.priority = 10
            it
        }.toMutableList()
        _dutyPlannedList = listOf()
        dutyReserveList = listOf()
        _dutyAssistantList.emit(_dutyAssistants)
        _dutyPlannedResults.emit(_dutyPlannedList)
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

        _dutyPlannedResults.emit(_dutyPlannedList)
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

    suspend fun planDuty(isReserve: Boolean = false, writePriority: Boolean = true){
        // clear all previously assigned dates
        _dutyAssistants = _dutyAssistants.map {
            it.assignedDates = mutableListOf()
            it
        }.toMutableList()

        val results = dutyPlanningMethodByDate(_dutyAssistants, getDaysInMonth(selectedYear,selectedMonth), false, writePriority)
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
        _dutyPlannedResults.emit(_dutyPlannedList)
        if(isReserve){
            planReserve()
        }
        savePlannedDuty()
    }

    fun planReserve(){
        val results = dutyPlanningMethodByDate(_dutyAssistants, getDaysInMonth(selectedYear,selectedMonth), true)
        val returnList: MutableList<Pair<String, LocalDate>> = mutableListOf()
        results.forEach { da ->
            da.assignedReserve.forEach{
                returnList.add(Pair(da.name, it))
            }
        }
        dutyReserveList = dutyReserveList.sortedBy { it.second }
        dutyReserveList = returnList
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

    private fun getDaysInMonth(year: Int, month: Int): List<LocalDate> {
        val firstDayOfMonth = LocalDate(year, month, 1)
        val daysInMonth = firstDayOfMonth.month.length((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0))


        return (1..daysInMonth).map { dayOfMonth ->
            firstDayOfMonth.plus(DatePeriod(days = dayOfMonth -1))
        }
    }
}