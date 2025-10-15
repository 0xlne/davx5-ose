/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.davdroid.db.Collection
import at.bitfire.davdroid.sync.account.TestAccount
import at.bitfire.ical4android.util.MiscUtils.asSyncAdapter
import at.bitfire.ical4android.util.MiscUtils.closeCompat
import at.bitfire.synctools.test.InitCalendarProviderRule
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import javax.inject.Inject
import kotlin.io.use

@HiltAndroidTest
class LocalCalendarStoreTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @get:Rule
    val initCalendarProviderRule: TestRule = InitCalendarProviderRule.initialize()

//    @get:Rule
//    val mockkRule = MockKRule(this)

    @Inject @ApplicationContext
    lateinit var context: Context

    @Inject
    lateinit var localCalendarFactory: LocalCalendar.Factory

    @Inject
    lateinit var localCalendarStore: LocalCalendarStore

    private lateinit var account: Account
    private var provider: ContentProviderClient? = null

    @Before
    fun setUp() {
        hiltRule.inject()
        account = TestAccount.create()
        provider = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)
    }

    @After
    fun tearDown() {
        provider?.closeCompat()
    }


    @Test
    fun testUpdateAccount() {
        provider?.use { provider ->
            // Create calendar
            val values = contentValuesOf(
                // ACCOUNT_NAME and ACCOUNT_TYPE are required (see docs)! If it's missing, other apps will crash.
                Calendars.ACCOUNT_NAME to account.name,
                Calendars.ACCOUNT_TYPE to account.type,

                // Email address for scheduling. Used by the calendar provider to determine whether the
                // user is ORGANIZER/ATTENDEE for a certain event.
                Calendars.OWNER_ACCOUNT to account.name,

                // flag as visible & syncable at creation, might be changed by user at any time
                Calendars.VISIBLE to 1,
                Calendars.SYNC_EVENTS to 1,

                Calendars._SYNC_ID to 999, // db collection id
                Calendars.CALENDAR_DISPLAY_NAME to "displayName",
            )
            val uri = provider.insert(
                Calendars.CONTENT_URI.asSyncAdapter(account),
                values
            )!!.asSyncAdapter(account)

            try {
                // Verify existence
                provider.query(
                    uri,
                    arrayOf(Calendars.OWNER_ACCOUNT),
                    "${Calendars.ACCOUNT_NAME}=?",
                    arrayOf(account.name),
                    null
                )!!.use { cursor ->
                    cursor.moveToNext()
                    assertEquals("Test Account", cursor.getString(0))
                }

                // Update
                val localCalendar = 
                val collection = mockk<Collection> {
                    every { id } returns 1
                    every { url } returns "https://example.com/calenddar/timeout".toHttpUrl()
                    every { displayName } returns "time out"
                    every { serviceId } returns 0
                }
                localCalendarStore.update(provider, )

                // Verify update

            } finally {
                provider.delete(uri, null, null)
            }
        }
    }


    // helpers

    private fun createLocalCalendar() {

    }


}