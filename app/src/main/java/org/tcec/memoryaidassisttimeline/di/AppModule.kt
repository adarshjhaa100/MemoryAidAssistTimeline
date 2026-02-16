package org.tcec.memoryaidassisttimeline.di

import android.app.Application
import androidx.room.Room
import org.tcec.memoryaidassisttimeline.data.MemoryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(app: Application): MemoryDatabase {
        return Room.databaseBuilder(app, MemoryDatabase::class.java, "memory_db").build()
    }

    @Provides
    fun provideDao(db: MemoryDatabase) = db.memoryDao()
}
