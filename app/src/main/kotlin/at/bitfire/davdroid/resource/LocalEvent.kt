/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

package at.bitfire.davdroid.resource

import android.provider.CalendarContract.Events
import androidx.core.content.contentValuesOf
import at.bitfire.ical4android.Event
import at.bitfire.synctools.icalendar.AssociatedEvents
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventBuilder2
import at.bitfire.synctools.mapping.calendar.LegacyAndroidEventProcessor
import at.bitfire.synctools.storage.LocalStorageException
import at.bitfire.synctools.storage.calendar.AndroidEvent2
import at.bitfire.synctools.storage.calendar.AndroidRecurringCalendar
import java.util.Optional
import java.util.UUID

class LocalEvent(
    val recurringCalendar: AndroidRecurringCalendar,
    val androidEvent: AndroidEvent2
) : LocalResource<Event> {

    override val id: Long
        get() = androidEvent.id

    override val fileName: String?
        get() = androidEvent.syncId

    override val eTag: String?
        get() = androidEvent.eTag

    override val scheduleTag: String?
        get() = androidEvent.scheduleTag

    override val flags: Int
        get() = androidEvent.flags


    override fun update(data: Event, fileName: String?, eTag: String?, scheduleTag: String?, flags: Int) {
        val eventAndExceptions = LegacyAndroidEventBuilder2(
            calendar = androidEvent.calendar,
            event = data,
            syncId = fileName,
            eTag = eTag,
            scheduleTag = scheduleTag,
            flags = flags
        ).build()
        recurringCalendar.updateEventAndExceptions(id, eventAndExceptions)
    }

    /**
     * Generates the [Event] that should actually be uploaded:
     *
     * 1. Takes the event from the calendar provider.
     * 2. Calculates the new SEQUENCE.
     *
     * @return data object that should be used for uploading
     *
     * @throws LocalStorageException    if there is no local event with the given [id]
     */
    fun eventToUpload(): AssociatedEvents {
        val eventAndExceptions = recurringCalendar.getById(id) ?: throw LocalStorageException("Event $id not found")

        // map local event to iCalendar
        val processor = LegacyAndroidEventProcessor(recurringCalendar.calendar.account.name)
        val associatedEvents = processor.process(eventAndExceptions)

        // Increase sequence (event.sequence null/non-null behavior is defined by the Event, see KDoc of event.sequence):
        // - If it's null, the event has just been created in the database, so we can start with SEQUENCE:0 (default).
        // - If it's non-null, the event already exists on the server, so increase by one.
        /*val sequence = event.sequence
        if (sequence != null && (nonGroupScheduled || weAreOrganizer))
            event.sequence = sequence + 1*/
        // TODO

        return associatedEvents
    }

    /**
     * Updates the SEQUENCE of the event in the content provider.
     *
     * @param sequence  new sequence value
     */
    fun updateSequence(sequence: Int?) {
        androidEvent.update(contentValuesOf(
            AndroidEvent2.COLUMN_SEQUENCE to sequence
        ))
    }


    /**
     * Creates and sets a new UID in the calendar provider, if no UID is already set.
     * It also returns the desired file name for the event for further processing in the sync algorithm.
     *
     * @return file name to use at upload
     */
    override fun prepareForUpload(): String {
        // make sure that UID is set

        // TODO iCalendar UID

        /*val uid: String = getCachedEvent().uid ?: run {
            // generate new UID
            val newUid = UUID.randomUUID().toString()

            // persist to calendar provider
            val values = contentValuesOf(Events.UID_2445 to newUid)
            androidEvent.update(values)

            // update in cached event data object
            getCachedEvent().uid = newUid

            newUid
        }*/

        val event = recurringCalendar.calendar.getEventRow(id) ?: throw LocalStorageException("Event $id not found")
        val uid = event.getAsString(Events.UID_2445) ?: run {
            val newUid = UUID.randomUUID().toString()

            // persist to calendar provider
            val values = contentValuesOf(Events.UID_2445 to newUid)
            androidEvent.update(values)

            newUid
        }

        val uidIsGoodFilename = uid.all { char ->
            // see RFC 2396 2.2
            char.isLetterOrDigit() || arrayOf(                  // allow letters and digits
                ';', ':', '@', '&', '=', '+', '$', ',',         // allow reserved characters except '/' and '?'
                '-', '_', '.', '!', '~', '*', '\'', '(', ')'    // allow unreserved characters
            ).contains(char)
        }
        return if (uidIsGoodFilename)
            "$uid.ics"                      // use UID as file name
        else
            "${UUID.randomUUID()}.ics"      // UID would be dangerous as file name, use random UUID instead
    }

    override fun clearDirty(fileName: Optional<String>, eTag: String?, scheduleTag: String?) {
        val values = contentValuesOf(
            Events.DIRTY to 0,
            AndroidEvent2.COLUMN_ETAG to eTag,
            AndroidEvent2.COLUMN_SCHEDULE_TAG to scheduleTag
        )
        if (fileName.isPresent)
            values.put(Events._SYNC_ID, fileName.get())
        androidEvent.update(values)
    }

    override fun updateFlags(flags: Int) {
        androidEvent.update(contentValuesOf(
            AndroidEvent2.COLUMN_FLAGS to flags
        ))
    }

    override fun deleteLocal() {
        recurringCalendar.deleteEventAndExceptions(id)
    }

    override fun resetDeleted() {
        androidEvent.update(contentValuesOf(
            Events.DELETED to 0
        ))
    }

}