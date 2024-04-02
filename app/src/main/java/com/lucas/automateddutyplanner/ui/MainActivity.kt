package com.lucas.automateddutyplanner.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.aminography.primecalendar.civil.CivilCalendar
import com.aminography.primedatepicker.picker.PrimeDatePicker
import com.aminography.primedatepicker.picker.callback.MultipleDaysPickCallback
import com.lucas.automateddutyplanner.BottomNavItemList
import com.lucas.automateddutyplanner.R
import com.lucas.automateddutyplanner.data.Constraint
import com.lucas.automateddutyplanner.data.DutyAssistant
import com.lucas.automateddutyplanner.ui.theme.AutomatedDutyPlannerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.toJavaLocalDate
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.PreferenceCategory
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import java.io.File
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()
    private lateinit var navController: NavHostController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.initialiseValues()
        setContent {
            val coroutineScope = rememberCoroutineScope()
            var showingAddDialog by remember {
                mutableStateOf(false)
            }
            navController = rememberNavController()
            AutomatedDutyPlannerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Scaffold(
                        floatingActionButton = {
                            FAB(onClick = {
                                showingAddDialog = true
                            }, navController, viewModel = viewModel)
                        },
                        bottomBar = { BottomNavigationBar(navController = navController) }
                    ) {
                        NavigationHost(
                            this@MainActivity,
                            this,
                            navController,
                            supportFragmentManager,
                            viewModel
                        )
                        @Suppress("UNUSED_EXPRESSION")
                        it
                    }
                }
                if (showingAddDialog) {
                    AddEditDutyAssistantDialog(coroutineScope = coroutineScope, viewModel) {
                        showingAddDialog = false
                    }
                }
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int, resultCode: Int, resultData: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, resultData)
        when (requestCode) {
            PICK_FILE -> {
                // file uri
                lifecycleScope.launch(Dispatchers.IO) {
                    resultData?.data?.let {
                        val stream = contentResolver.openInputStream(it)
                        if (stream != null) {
                            viewModel.importJsonToDaConfig(stream)
                        }
                    }
                }
            }

            CREATE_FILE -> {
                // file uri
                lifecycleScope.launch(Dispatchers.IO) {
                    resultData?.data?.let {
                        val stream = contentResolver.openOutputStream(it)
                        if (stream != null) {
                            viewModel.createJsonFromDaConfig(stream)
                        }
                        stream?.close()
                    }
                }
            }

            PICK_ICS_FILE -> {
                lifecycleScope.launch(Dispatchers.IO) {
                    resultData?.data?.let {
                        val stream = contentResolver.openInputStream(it)
                        if (stream != null) {
                            viewModel.importIcsToLocalDate(stream)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavigationHost(
    activityContext: Activity,
    context: Context,
    navController: NavHostController,
    fm: FragmentManager,
    viewModel: MainViewModel
) {
    NavHost(navController, startDestination = BottomNavItemList[0].route) {
        composable(BottomNavItemList[0].route) { HomeScreen(fm, viewModel) }
        composable(BottomNavItemList[1].route) { ResultsPage(context, viewModel) }
        composable(BottomNavItemList[2].route) { SettingsPage(activityContext, viewModel, fm) }
    }
}

@SuppressLint("CoroutineCreationDuringComposition")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(fm: FragmentManager, viewModel: MainViewModel) {
    val recomposeToggleState: MutableState<Boolean> = remember { mutableStateOf(false) }

    var expanded: Boolean by remember { mutableStateOf(false) }
    var showingChangeMonthDialog: Boolean by remember { mutableStateOf(false) }

    val daList = viewModel.dutyAssistantList.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var loopMonth: Int by remember {
        mutableIntStateOf(0)
    }

    //force a recompose for every collect
    coroutineScope.launch {
        viewModel.dutyAssistantList.collect {
            recomposeToggleState.value = !recomposeToggleState.value
        }
    }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentSize(Alignment.TopEnd)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { expanded = !expanded },
                    content = {
                        Text(
                            "Select Month (Current ${
                                LocalDate(
                                    year = viewModel.selectedYear,
                                    month = Month.of(viewModel.selectedMonth),
                                    dayOfMonth = 1
                                ).month
                            })"
                        )
                    })
                if (expanded) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "More"
                        )
                    }
                } else {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "More"
                        )
                    }
                }
            }

            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(
                    "January",
                    "February",
                    "March",
                    "April",
                    "May",
                    "June",
                    "July",
                    "August",
                    "September",
                    "October",
                    "November",
                    "December"
                )
                    .forEachIndexed { i: Int, s: String ->
                        DropdownMenuItem(text = { Text(text = s) }, onClick = {
                            loopMonth = i
                            showingChangeMonthDialog = true
                            expanded = false
                        })

                    }

            }
            if (showingChangeMonthDialog) {
                ConfirmationDialog(
                    alertString = "Changing months will reset everyone's unavailable dates! Are you sure you want to change the month to ${
                        Month(
                            loopMonth + 1
                        )
                    } ?",
                    onDismissed = { showingChangeMonthDialog = false },
                    onPositive = {
                        showingChangeMonthDialog = false
                        coroutineScope.launch {
                            viewModel.changeMonth(loopMonth + 1)
                        }
                    },
                    onNegative = { showingChangeMonthDialog = false })

            }
        }
        Text(
            text = "Duty Personnel List",
            modifier = Modifier.padding(start = 8.dp, 8.dp),
            fontWeight = FontWeight.Bold
        )
        val formatter = DateTimeFormatter.ofPattern("dd")
        if (daList.value.isNotEmpty()) {
            LaunchedEffect(recomposeToggleState.value) {}
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(bottom = 86.dp)
            ) {
                items(daList.value.count()) {
                    val dutyAssistant = daList.value[it]
                    DAItemCard(
                        da = dutyAssistant,
                        dateFormatter = formatter,
                        fm = fm,
                        index = it,
                        viewModel = viewModel,
                        modifier = Modifier.animateItemPlacement()
                    )
                }
                item {
                    Box(Modifier.height(84.dp)) {

                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.sad_pikachu),
                    contentDescription = "Sad pikachu"
                )
                Text(
                    text = "Looks like nobody's manning COC at the moment",
                    fontWeight = FontWeight.Bold
                )
            }
        }

    }
}

@Composable
fun ConfirmationDialog(
    alertString: String,
    onDismissed: () -> Unit,
    onPositive: () -> Unit,
    onNegative: () -> Unit
) {
    AlertDialog(
        icon = { Icon(Icons.Default.Warning, contentDescription = "Warning") },
        onDismissRequest = { onDismissed() },
        title = { Text("Irreversible Action!") },
        text = { Text(text = alertString) },
        confirmButton = {
            TextButton(onClick = {
                onPositive()
                onDismissed()
            }) {
                Text(text = "Yes")
            }
        },
        dismissButton = {
            TextButton(onClick = { onNegative() }) {
                Text(text = "No")
            }
        },

        )
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        BottomNavItemList.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
//                selectedContentColor = Color.White,
//                unselectedContentColor = Color.Black,

            )
        }
    }
}

// every duty assistant item card, most complex logic of the entire ui lol
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DAItemCard(
    da: DutyAssistant,
    dateFormatter: DateTimeFormatter,
    index: Int,
    fm: FragmentManager,
    viewModel: MainViewModel,
    modifier: Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var showingConstraintsDialog by remember {
        mutableStateOf(false)
    }
    var showingDeletionConfirmationDialog by remember {
        mutableStateOf(false)
    }
    var showingEditNameDialog by remember {
        mutableStateOf(false)
    }
    OutlinedCard(
        modifier = modifier
            .wrapContentSize()
            .height(76.dp)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            .combinedClickable(
                onLongClick = {
                    showingEditNameDialog = true
                },
                onClick = {

                }),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)) {
                // add (AM) to mark out those who cannot do duty on weekends
                val daName =
                    if (Constraint.EXCUSE_STAY_IN in da.constraints) da.name + " (AM)" else da.name
                Text(
                    text = daName,
                    Modifier
                        .padding(start = 8.dp)
                        .basicMarquee(),
                    fontWeight = FontWeight.SemiBold
                )
                Text(modifier = Modifier
                    .padding(start = 8.dp)
                    .basicMarquee(),
                    // avoid showing dates if there are none, or there are way too many
                    text = if (da.unableDates.isNotEmpty() && da.unableDates.count() < 10) {
                        da.unableDates.map { dateFormatter.format(it.toJavaLocalDate()) }
                            .toString()
                            .replace('[', ' ').replace(']', ' ').trim()
                    } else if (da.unableDates.count() > 8) {
                        "Too Many To Show!"
                    } else {
                        "No Unavailable Dates"
                    })
            }
            val selectDateUnit = {
                val callback = MultipleDaysPickCallback { days ->
                    da.unableDates =
                        days.map { LocalDate(it.year, it.month + 1, it.dayOfMonth) }.toMutableList()
                    coroutineScope.launch {
                        viewModel.modifyDutyAssistant(da, index)
                    }
                }
                val startDate = CivilCalendar().apply {
                    date = 1
                    month = viewModel.selectedMonth - 1
                    year = viewModel.selectedYear
                }
                val endDate = CivilCalendar().apply {
                    date = 1
                    month = viewModel.selectedMonth
                    year = viewModel.selectedYear
                }
                startDate.set(
                    year = viewModel.selectedYear,
                    month = viewModel.selectedMonth - 1,
                    dayOfMonth = 1
                )
                val datePicker = PrimeDatePicker.bottomSheetWith(startDate)
                    .pickMultipleDays(callback)
                    .initiallyPickedMultipleDays(da.unableDates.map {
                        val cc = CivilCalendar()
                        cc.set(
                            year = it.year,
                            month = it.monthNumber - 1,
                            dayOfMonth = it.dayOfMonth
                        )
                        cc
                    })
                    .maxPossibleDate(endDate)
                    .minPossibleDate(startDate)// or pickRangeDays(callback) or pickMultipleDays(callback
                datePicker.build().show(fm, "sometag")
            }
            // emulate an icon button using a box, so we can make use of longclick
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentSize()
                    .weight(0.6f),
            ) {
                // set unable dates
                Box(modifier = Modifier
                    .clip(CircleShape)
                    .size(48.dp)
                    .combinedClickable(onClick = {
                        selectDateUnit()
                    }, onLongClick = {
                        da.unableDates = mutableListOf()
                        coroutineScope.launch {
                            viewModel.modifyDutyAssistant(da, index)
                        }
                    }), contentAlignment = Alignment.Center)
                {
                    if (da.unableDates.isNotEmpty())
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Unavailable Dates",
                            tint = Color.Blue
                        )
                    else
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Select Unavailable Dates"
                        )
                }
                // set person preferences and constraints i.e ex stay in
                IconButton(
                    onClick =
                    {
                        showingConstraintsDialog = true
                    },
                )
                {
                    if (da.constraints.isEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Set Person Constraints and Preferences"
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Set Person Constraints and Preferences",
                            tint = Color.Green
                        )
                    }
                }
                IconButton(
                    onClick =
                    {
                        showingDeletionConfirmationDialog = true

                    },
                )
                {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Person",
                        tint = Color.Red
                    )
                }
            }
        }
        if (showingDeletionConfirmationDialog) {
            ConfirmationDialog(alertString = "Are you sure you want to delete ${da.name} ?",
                onDismissed = { showingDeletionConfirmationDialog = false },
                onPositive = {
                    coroutineScope.launch {
                        viewModel.deleteDaFromList(index)
                    }
                },
                onNegative = {
                    showingDeletionConfirmationDialog = false
                })
        }
        if (showingConstraintsDialog) {
            DutyConstraintsDialog(
                index = index,
                dutyAssistant = da,
                coroutineScope = coroutineScope,
                viewModel = viewModel,
                onDismissed = {
                    showingConstraintsDialog = false
                })
        }
        if (showingEditNameDialog) {
            AddEditDutyAssistantDialog(
                coroutineScope = coroutineScope,
                viewModel = viewModel,
                index = index
            ) {
                showingEditNameDialog = false
            }
        }
    }
}

// dialog to modify DA constraints
@Composable
fun DutyConstraintsDialog(
    index: Int,
    dutyAssistant: DutyAssistant,
    coroutineScope: CoroutineScope,
    onDismissed: () -> Unit,
    viewModel: MainViewModel
) {
    Dialog(
        onDismissRequest = {
            coroutineScope.launch {
                // save changes to the DA
                viewModel.modifyDutyAssistant(dutyAssistant, index)
            }
            onDismissed()
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(enabled = true, state = rememberScrollState())
                    .weight(1f), horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Constraint.entries.forEach { constraint ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        var checked by remember {
                            mutableStateOf(constraint in dutyAssistant.constraints)
                        }
                        Text(
                            modifier = Modifier.padding(12.dp),
                            text = constraint.toString().replace("_", " "),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Start
                        )
                        Checkbox(checked = checked, onCheckedChange = {
                            checked = !checked
                            if (checked)
                                dutyAssistant.constraints.add(constraint)
                            else
                                dutyAssistant.constraints.remove(constraint)
                        })
                    }
                }
                Row(
                    modifier = Modifier.width(240.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(Modifier.wrapContentSize()) {
                        OutlinedIconButton(
                            onClick =
                            {
                                dutyAssistant.persistentPriority -= 1
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null
                            )
                        }
                        OutlinedIconButton(onClick = {
                            dutyAssistant.persistentPriority += 1
                        }) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null
                            )
                        }
                    }
                    Text(text = "Priority Value: ${dutyAssistant.persistentPriority}")

                }
            }

        }
    }
}

@Composable
fun AddEditDutyAssistantDialog(
    coroutineScope: CoroutineScope,
    viewModel: MainViewModel,
    index: Int? = null,
    onDismissed: () -> Unit
) {
    var name by rememberSaveable {
        mutableStateOf(if (index != null) viewModel.dutyAssistantList.value[index].name else "")
    }
    Dialog(
        onDismissRequest = {
            onDismissed()
        },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                modifier = Modifier.padding(top = 16.dp, start = 16.dp),
                text = if (index != null) "Edit Duty Personnel" else "Add Duty Personnel",
                fontWeight = FontWeight.Bold
            )
            Column(Modifier.wrapContentSize(), verticalArrangement = Arrangement.SpaceAround) {
                Box(
                    modifier = Modifier
                        .height(160.dp)
                        .fillMaxWidth()
                ) {
                    TextField(
                        modifier = Modifier
                            .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth(),
                        value = name,
                        onValueChange = {
                            name = it
                        },
                        placeholder = { Text("Name") },
                        keyboardOptions = KeyboardOptions.Default.copy(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            coroutineScope.launch {
                                // save changes to the DA
                                viewModel.addDaToList(DutyAssistant(name))
                            }
                        })
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(modifier = Modifier.padding(end = 16.dp), onClick = {
                        coroutineScope.launch {
                            // save changes to the DA
                            if (index == null) {
                                viewModel.addDaToList(DutyAssistant(name))
                            } else {
                                viewModel.editDaName(index, name)
                            }
                            onDismissed()
                        }
                    }) {
                        if (index == null) {
                            Text("Add")
                        } else {
                            Text("Save")
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun ResultsPage(context: Context, viewModel: MainViewModel) {
    val dutyResults by viewModel.dutyPlannedResults.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "Planning Duty For: " +
                        "${Month(viewModel.selectedMonth)}",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, top = 20.dp)
            )
            TextButton(
                modifier = Modifier
                    .padding(end = 8.dp, top = 12.dp)
                    .height(32.dp),
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val isReserve = viewModel.sharedPrefs.getBoolean("enable_reserve", false)
                        val file = viewModel.createCsvFromDuty(context.filesDir, isReserve)
                        val sendIntent = Intent()
                        sendIntent.action = Intent.ACTION_SEND
                        sendIntent.putExtra(
                            Intent.EXTRA_STREAM,
                            FileProvider.getUriForFile(
                                context,
                                context.applicationContext.packageName + ".provider",
                                file
                            )
                        )
                        sendIntent.type = "text/csv"

                        // tell the main thread to launch a new activity
                        launch(Dispatchers.Main) {
                            context.startActivity(Intent.createChooser(sendIntent, "SHARE"))
                        }
                    }

                },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp)
            )
            {
                Row(Modifier.wrapContentSize(), Arrangement.SpaceBetween) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null)
                    Text(
                        text = "Export to CSV",
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
            }
        }
        if (dutyResults.isNotEmpty()) {
            val formatter = DateTimeFormatter.ofPattern("EEEE, dd/MM")
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp, bottom = 86.dp),
            ) {
                items(dutyResults.size) {
                    DaAssignedDutyCard(
                        formatter = formatter,
                        assigneeNames = dutyResults[it].assigneeNames,
                        reserveName = dutyResults[it].reserveName,
                        assigned = dutyResults[it].assigned,
                    )
                }
                // to space out the bottom item so the FAB doesn't obscure it
                item{
                    Box(Modifier.height(84.dp)) {

                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.psyduck),
                    contentDescription = "psyduck"
                )
                Text(
                    modifier = Modifier.padding(top = 260.dp),
                    text = "When you forget to plan duty for next month",
                    fontWeight = FontWeight.Bold
                )
            }
        }

    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DaAssignedDutyCard(
    formatter: DateTimeFormatter,
    assigneeNames: Pair<String, String?>,
    reserveName: String?,
    assigned: LocalDate,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        modifier = modifier
            .wrapContentSize()
            .height(76.dp)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier.weight(1f)) {
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = formatter.format(assigned.toJavaLocalDate()),
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(start = 8.dp)
                ) {
                    val text =
                        if (assigneeNames.second != null) "${assigneeNames.first} / ${assigneeNames.second} (PM)" else assigneeNames.first
                    Text(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .basicMarquee(), text = text, fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (reserveName != null) {
                Text(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .weight(1f)
                        .basicMarquee(),
                    text = "R: $reserveName",
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Box {}
            }
        }
    }
}

@Composable
fun SettingsPage(activityContext: Activity, viewModel: MainViewModel, fm: FragmentManager) {
    var value by remember {
        mutableStateOf(viewModel.sharedPrefs.getBoolean("enable_reserve", false))
    }
    var showingPlanReserveDialog by remember {
        mutableStateOf(false)
    }
    var showingDeletePersonnelDialog by remember {
        mutableStateOf(false)
    }
    var showingClearPublicHolidaysDialog by remember {
        mutableStateOf(false)
    }
    val coroutineScope = rememberCoroutineScope()
    val publicHolidays by viewModel.publicHolidaysFlow.collectAsState()
    ProvidePreferenceLocals {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                PreferenceCategory(title = { Text("Duty Planning Options") })
            }
            item {
                SwitchPreference(
                    value = value,
                    onValueChange = {
                        value = it
                        viewModel.sharedPrefs.edit().putBoolean("enable_reserve", it).apply()
                        // prompt if they would like to plan reserve
                        if (viewModel.dutyReserveList.isEmpty() && it) {
                            showingPlanReserveDialog = true
                        }
                        if (!it) {
                            coroutineScope.launch {
                                viewModel.clearReserves()
                            }
                        }
                    },
                    title = { Text(text = "Enable Reserve Planning") },
                    summary = { Text(text = "Reserve planning will show up in both the Results screen and the exported CSV") })
            }

            item {
                PreferenceCategory(title = { Text("Manage Personnel List") })
            }
            item {
                Preference(
                    title = { Text(text = "Export Duty Personnel") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Duty Assistants"
                        )
                    },
                    summary = { Text(text = "Exports all the duty personnel and their saved priorities into a JSON file for import") },
                    onClick = {
                        val file =
                            File(activityContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath!!)
                        val uri = Uri.fromFile(file)
                        createDutyAssistantConfigFile(activityContext, uri)
                    }
                )
            }
            item {
                Preference(
                    title = { Text(text = "Import Duty Personnel") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Import Duty Assistants"
                        )
                    },
                    summary = { Text(text = "Imports all the duty personnel and their saved priorities into the current application") },
                    onClick = {
                        val file =
                            File(activityContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath!!)
                        val uri = Uri.fromFile(file)
                        importDutyAssistantConfigFile(activityContext, uri)
                    }
                )
            }
            item {
                Preference(
                    title = { Text(text = "Delete All Duty Personnel") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Import Duty Assistants"
                        )
                    },
                    summary = { Text(text = "Deletes All Duty Personnel") },
                    onClick = {
                        showingDeletePersonnelDialog = true
                    }
                )
            }
            item {
                PreferenceCategory(title = { Text("Public Holidays") })
            }
            item {
                val holidaysSetStr = publicHolidays.toString().replace("[", "").replace("]", "")
                Preference(
                    title = { Text(text = "Public Holidays Set") },
                    summary = { Text(text = holidaysSetStr.ifEmpty { "No public holidays set! Try importing the .ics from the MOM website or manually setting it below!" }) },
                )
            }
            item {
                Preference(
                    title = { Text(text = "Import Public Holidays From .ics") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Import Duty Assistants"
                        )
                    },
                    summary = { Text(text = "Imports public holidays from an ICS file, you can download it from mom.gov.sg") },
                    onClick = {
                        val file =
                            File(activityContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.absolutePath!!)
                        val uri = Uri.fromFile(file)
                        importPublicHolidaysIcs(activityContext, uri)
                    }
                )
            }
            item {
                val callback = MultipleDaysPickCallback { days ->
                    val dates =
                        days.map { LocalDate(it.year, it.month + 1, it.dayOfMonth) }.toMutableList()
                    coroutineScope.launch {
                        viewModel.setPublicHolidays(dates)
                    }
                }
                // start on 1st of jan
                val startDate = CivilCalendar().apply {
                    date = 1
                    month = 0
                    year = viewModel.selectedYear
                }
                // end on 31st december
                val endDate = CivilCalendar().apply {
                    date = 31
                    month = 11
                    year = viewModel.selectedYear
                }
                Preference(
                    title = { Text(text = "Manually Edit/Set Public Holidays") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Import Duty Assistants"
                        )
                    },
                    summary = { Text(text = "Manually set and edit public holidays") },
                    onClick = {
                        // pick dates using the excellent datepicker library
                        startDate.set(year = viewModel.selectedYear, month = 0, dayOfMonth = 1)
                        val datePicker = PrimeDatePicker.bottomSheetWith(startDate)
                            .pickMultipleDays(callback)
                            .initiallyPickedMultipleDays(publicHolidays.map {
                                val cc = CivilCalendar()
                                cc.set(
                                    year = it.year,
                                    month = it.monthNumber - 1,
                                    dayOfMonth = it.dayOfMonth
                                )
                                cc
                            })
                            .maxPossibleDate(endDate)
                            .minPossibleDate(startDate)// or pickRangeDays(callback) or pickMultipleDays(callback
                        datePicker.build().show(fm, "sometag2")
                    }
                )
            }
            item {
                Preference(
                    title = { Text(text = "Clear All Public Holidays") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Import Duty Assistants"
                        )
                    },
                    summary = { Text(text = "Deletes All Set Public Holidays") },
                    onClick = {
                        showingClearPublicHolidaysDialog = true
                    }
                )
            }
            item{
                Preference(
                    title = { Text(text = "About The Developer") },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Import Duty Assistants"
                        )
                    },
                    summary = { Text(text = "Developed by Lucas Lee Jing Yi from CTAB.\nWant access to the repo? Don't be afraid to reach out!\n" +
                            "Copyright © 2024 Lucas Lee Jing Yi.") },
                    onClick = {
                        activityContext.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/LLJY/")))
                    }
                )
            }
            item {
                Box(Modifier.height(84.dp)) {

                }
            }
        }
        if (showingPlanReserveDialog) {
            ConfirmationDialog(
                alertString = "You do not currently have reserves planned! This will result in a broken export.csv, would you like to plan reserve?",
                onDismissed = { showingPlanReserveDialog = false },
                onPositive = {
                    coroutineScope.launch(Dispatchers.IO) {
                        viewModel.planReserve(true)
                    }
                }) {
                showingPlanReserveDialog = false
            }
        }
        if (showingDeletePersonnelDialog) {
            ConfirmationDialog(
                alertString = "Are you sure you want to delete all personnel? This will reset their priority ranking for future duties!",
                onDismissed = { showingDeletePersonnelDialog = false },
                onPositive = {
                    coroutineScope.launch(Dispatchers.IO) {
                        viewModel.clearPersonnel()
                    }
                }) {
                showingDeletePersonnelDialog = false
            }
        }
        if (showingClearPublicHolidaysDialog) {
            ConfirmationDialog(
                alertString = "Are you sure you want to clear public holidays?",
                onDismissed = { showingClearPublicHolidaysDialog = false },
                onPositive = {
                    coroutineScope.launch(Dispatchers.IO) {
                        viewModel.setPublicHolidays(listOf())
                    }
                }) {
                showingClearPublicHolidaysDialog = false
            }
        }
    }

}


// floating action button to add a new DA
@Composable
fun FAB(onClick: () -> Unit, navController: NavHostController, viewModel: MainViewModel) {
    val results = viewModel.dutyPlannedResults.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val coroutineScope = rememberCoroutineScope()

    var showingPlanDutyConfirmationDialog by remember {
        mutableStateOf(false)
    }
    // for reference here is the actual routes
//    composable(BottomNavItemList[0].route) { HomeScreen(fm, viewModel) }
//    composable(BottomNavItemList[1].route) { ResultsPage(viewModel) }
//    composable(BottomNavItemList[2].route) { Text(text = "") }
    when (navBackStackEntry?.destination?.route) {
        BottomNavItemList[0].route -> {
            ExtendedFloatingActionButton(
                onClick = { onClick() },
                icon = { Icon(Icons.Filled.Add, "Add duty assistant") },
                text = { Text(text = "Add Duty Personnel") },
            )
        }

        BottomNavItemList[1].route -> {
            ExtendedFloatingActionButton(
                onClick = {
                    if (results.value.isEmpty()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            viewModel.planDuty(
                                viewModel.sharedPrefs.getBoolean(
                                    "enable_reserve",
                                    false
                                )
                            )
                        }
                    } else {
                        showingPlanDutyConfirmationDialog = true
                    }
                },
                icon = { Icon(Icons.Filled.Build, "Generate duty list") },
                text = { Text(text = "Plan Duty!") },
            )
        }

        BottomNavItemList[2].route -> {

        }

        else -> {

        }
    }
    if (showingPlanDutyConfirmationDialog) {
        ConfirmationDialog(
            alertString = "This will remove all previous duty planning results, are you sure you want to proceed?",
            onDismissed = { showingPlanDutyConfirmationDialog = false },
            onPositive = {
                coroutineScope.launch(Dispatchers.IO) {
                    viewModel.planDuty(viewModel.sharedPrefs.getBoolean("enable_reserve", false))
                }
            }) {
            showingPlanDutyConfirmationDialog = false
        }
    }
}

const val CREATE_FILE = 1

const val PICK_FILE = 2

const val PICK_ICS_FILE = 3

fun createDutyAssistantConfigFile(context: Activity, pickerInitialUri: Uri) {
    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        //addCategory(Intent.CATEGORY_OPENABLE)
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, "duty_planner_config.json")

        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker before your app creates the document.
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
    }
    context.startActivityForResult(intent, CREATE_FILE)
}

fun importDutyAssistantConfigFile(context: Activity, pickerInitialUri: Uri) {

    // Choose a directory using the system's file picker.
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        //addCategory(Intent.ACTION_OPEN_DOCUMENT)
        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when it loads.
        type = "text/plain"
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
    }

    context.startActivityForResult(intent, PICK_FILE)
}

fun importPublicHolidaysIcs(context: Activity, pickerInitialUri: Uri) {
    // Choose a directory using the system's file picker.
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        //addCategory(Intent.ACTION_OPEN_DOCUMENT)
        // Optionally, specify a URI for the directory that should be opened in
        // the system file picker when it loads.
        type = "text/calendar"
        putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
    }
    context.startActivityForResult(intent, PICK_ICS_FILE)
}
