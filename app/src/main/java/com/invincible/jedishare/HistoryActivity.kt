package com.invincible.jedishare

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.room.Room
import com.invincible.jedishare.database.AppDatabase
import com.invincible.jedishare.database.HistoryItem
import com.invincible.jedishare.database.HistoryItemDao
import com.invincible.jedishare.ui.theme.JediShareTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.UUID


class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "history"
        ).build()

        val historyItemDao = db.historyItemDao()
        var historyEntries: List<HistoryItem> = emptyList()
        GlobalScope.launch(Dispatchers.IO){
            try{
                for (i in 1..10){
                    historyItemDao.insertAll(HistoryItem(UUID.randomUUID().toString(),"file $i","size $i","time $i"))
                }
                historyEntries = historyItemDao.getAll()
                Log.e("TAG", "onCreate: ${historyEntries.toString()}", )
            }
            catch (e: java.lang.Exception){
                e.stackTrace
                Log.e("TAG", "onCreate: Database Query Failed + ${e.toString()}", )
            }
        }



        setContent {
            JediShareTheme {
                // A surface container using the 'background' color from the theme
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn{
                        items(historyEntries){ item ->
                            Column(modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.SpaceEvenly) {
                                Text(text = item.file_name)
                                Text(text = item.file_size)
                                Text(text = item.file_timestamp)
                                Divider(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background),
                        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                    ) {
                        NavBar()
                    }
                }

            }
        }
    }
}