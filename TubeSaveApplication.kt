package com.tubesave.pro

import android.app.Application
import androidx.room.Room
import com.tubesave.pro.data.AppDatabase

class TubeSaveApplication: Application() {
 val db by lazy { Room.databaseBuilder(this,AppDatabase::class.java,"tubesave.db").fallbackToDestructiveMigration().build() }
}
