package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContactDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() { database.close() }

    @Test
    fun duplicatePhoneDoesNotCreateASecondSmsRecipient() = runBlocking {
        val dao = database.contactDao()
        dao.insertContact(Contact(name = "first", phoneNumber = "010-1234-5678"))
        assertEquals(ContactInsertResult.DUPLICATE, dao.insertContactIfAllowed(Contact(name = "again", phoneNumber = "01012345678")))
        assertEquals(1, dao.getContactCount())
    }

    @Test
    fun concurrentAdditionsCannotExceedThreeGuardians() = runBlocking {
        val dao = database.contactDao()
        val results = (1..5).map { index -> async(Dispatchers.IO) {
            dao.insertContactIfAllowed(Contact(name = "guardian$index", phoneNumber = "0101234567$index"))
        } }.awaitAll()
        assertEquals(3, results.count { it == ContactInsertResult.ADDED })
        assertEquals(3, dao.getContactCount())
    }

    @Test
    fun concurrentDeletionsRetainOneGuardianForActiveSafetyFeatures() = runBlocking {
        val dao = database.contactDao()
        val contacts = (1..2).map { Contact(id = it, name = "guardian$it", phoneNumber = "0101234567$it") }
        contacts.forEach { dao.insertContact(it) }
        val results = contacts.map { contact -> async(Dispatchers.IO) {
            dao.deleteContactIfAllowed(contact, requireRemainingContact = true)
        } }.awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(1, dao.getContactCount())
        val last = dao.getContactSnapshot().single()
        assertFalse(dao.deleteContactIfAllowed(last, true))
        assertTrue(dao.deleteContactIfAllowed(last, false))
        assertEquals(0, dao.getContactCount())
    }
}
