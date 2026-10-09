package com.grinch.rivo4

import androidx.room.Room
import com.grinch.rivo4.modal.db.RivoDatabase
import com.grinch.rivo4.controller.CallLogViewModel
import com.grinch.rivo4.controller.ContactsViewModel
import com.grinch.rivo4.modal.`interface`.ICallLogRepository
import com.grinch.rivo4.modal.`interface`.IContactsRepository
import com.grinch.rivo4.modal.repository.CallLogRepository
import com.grinch.rivo4.modal.repository.ContactsRepository
import com.grinch.rivo4.controller.CallAnalyticsViewModel
import com.grinch.rivo4.controller.util.PreferenceManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { com.grinch.rivo4.controller.identification.CallerIdentification(androidContext()) }
    single {
        Room.databaseBuilder(
            androidContext(),
            RivoDatabase::class.java,
            "rivo_database"
        ).allowMainThreadQueries()
            .addMigrations(RivoDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<RivoDatabase>().privateContactDao() }
    single { get<RivoDatabase>().callNoteDao() }
    single { get<RivoDatabase>().callbackReminderDao() }
    single { get<RivoDatabase>().vobizCallRecordDao() }

    single<IContactsRepository> {
        ContactsRepository(androidContext(), get())
    }
    single<ICallLogRepository> {
        CallLogRepository(androidContext(), get(), get())
    }
    single {
        PreferenceManager(androidContext())
    }
    single {
        com.grinch.rivo4.controller.reminder.CallbackReminderManager(androidContext(), get())
    }
    viewModel { ContactsViewModel(get(), get()) }
    viewModel { CallLogViewModel(get()) }
    viewModel { CallAnalyticsViewModel(get()) }
}
