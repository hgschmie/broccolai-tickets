package co.uk.magmo.puretickets.storage

import co.aikar.idb.*
import co.uk.magmo.puretickets.interactions.PendingNotification
import co.uk.magmo.puretickets.ticket.Message
import co.uk.magmo.puretickets.ticket.Ticket
import co.uk.magmo.puretickets.ticket.TicketStatus
import co.uk.magmo.puretickets.user.UserSettings
import co.uk.magmo.puretickets.utils.asName
import com.google.common.collect.ArrayListMultimap
import org.bukkit.Location
import org.bukkit.plugin.Plugin
import org.intellij.lang.annotations.Language
import java.io.File
import java.sql.Time
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.logging.Level
import kotlin.collections.HashMap

class SQLiteManager : SQLManager {
    override fun setup(plugin: Plugin) {
        val file = File(plugin.dataFolder, "tickets.db")
        file.createNewFile()

        val options = DatabaseOptions.builder().sqlite(file.toString()).build()
        val database = PooledDatabaseOptions.builder().options(options).createHikariDatabase()

        DB.setGlobalDatabase(database)
        DB.executeUpdate("CREATE TABLE IF NOT EXISTS ticket(id INTEGER, uuid TEXT, status TEXT, picker TEXT)")
        DB.executeUpdate("CREATE TABLE IF NOT EXISTS message(ticket INTEGER, reason TEXT, data TEXT, sender TEXT, date TEXT)")
        DB.executeUpdate("CREATE TABLE IF NOT EXISTS notification(uuid TEXT, message TEXT, replacements TEXT)")
        DB.executeUpdate("CREATE TABLE IF NOT EXISTS settings(uuid TEXT, announcements TEXT)")

        var version = DB.getFirstColumn<Int>("PRAGMA user_version")

        if (version == 0) {
            plugin.logger.log(Level.INFO, "Updated PureTickets database to have location column")
            DB.executeUpdate("ALTER TABLE ticket ADD location TEXT")
            version++
        }

        if (version <= 1) {
            plugin.logger.log(Level.INFO, "Updated PureTickets database to remove tickets with empty locations and remove all pending notifications")
            DB.executeUpdate("DELETE FROM ticket WHERE location IS NULL OR trim(location) = ?", "")
            DB.executeUpdate("DELETE FROM notification")
            version++
        }

        DB.executeUpdate("PRAGMA user_version = $version")
    }

    override fun highscores(span: TimeAmount): HashMap<UUID, Int> {
        val data = HashMap<UUID, Int>()

        val length = if (span.length != null) {
            LocalDateTime.now().atZone(ZoneId.systemDefault()).toEpochSecond() - span.length
        } else {
            0
        }

        DB.getResults("""
            SELECT picker, COUNT(*) AS `num`
            FROM ticket
            WHERE status = ?
            AND picker IS NOT NULL
            and id in (SELECT DISTINCT ticket FROM message WHERE date > ?)
            GROUP BY picker
        """.trimIndent(), TicketStatus.CLOSED.name, length)
                .forEach { data[it.getUUID("picker")!!] = it.getInt("num") }

        return data
    }

    override val ticket = object : SQLManager.TicketFunctions {
        override fun select(id: Int): Ticket {
            val row = DB.getFirstRow("SELECT id, uuid, status, picker, location FROM ticket WHERE id = ?", id)

            return row.buildTicket()
        }

        override fun selectAll(status: TicketStatus?): List<Ticket> {
            val results = if (status == null) {
                DB.getResults("SELECT id, uuid, status, picker, location FROM ticket")
            } else {
                DB.getResults("SELECT id, uuid, status, picker, location FROM ticket WHERE status = ?", status.name)
            }

            return results.map { it.buildTicket() }
        }

        override fun selectAll(uuid: UUID, status: TicketStatus?): List<Ticket> {
            @Language("SQL")
            val sql = "SELECT id, uuid, status, picker, location FROM ticket WHERE uuid = ?"

            val results = if (status == null) {
                DB.getResults(sql, uuid)
            } else {
                DB.getResults(sql + " AND status = ?", uuid, status.name)
            }

            return results.map { it.buildTicket() }
        }

        override fun selectIds(uuid: UUID, status: TicketStatus?): List<Int> {
            @Language("SQL")
            val sql = "SELECT id, uuid, status, picker, location FROM ticket WHERE uuid = ?"

            return if (status == null) {
                DB.getFirstColumnResults(sql, uuid)
            } else {
                DB.getFirstColumnResults(sql + " AND status = ?", uuid, status.name)
            }
        }

        override fun selectHighestId(uuid: UUID, vararg status: TicketStatus?): Int? {
            @Language("SQL")
            var sql = "SELECT max(id) FROM ticket WHERE uuid = ?"
            val replacements = mutableListOf<String>()

            for (i in status.indices) {
                sql += if (i == 0) {
                    " AND status = ?"
                } else {
                    " OR status = ?"
                }

                replacements.add(status[i]!!.name)
            }

            return DB.getFirstColumn(sql, uuid, *replacements.toTypedArray())
        }

        override fun selectNames(status: TicketStatus?): List<String> {
            val row = if (status == null) {
                DB.getFirstColumnResults<String>("SELECT DISTINCT uuid FROM ticket")
            } else {
                DB.getFirstColumnResults("SELECT DISTINCT uuid FROM ticket WHERE status = ?", status.name)
            }

            return row.mapNotNull { UUID.fromString(it).asName() }
        }

        override fun selectTicketStats(uuid: UUID?): HashMap<TicketStatus, Int> {
            @Language("SQL")
            val sql = """
                SELECT
                    SUM(Status LIKE 'OPEN') AS open,
                    SUM(Status LIKE 'PICKED') AS picked,
                    SUM(status LIKE 'CLOSED') AS closed
                FROM ticket
            """.trimIndent()

            val row = if (uuid != null) {
                DB.getFirstRow(sql + " WHERE uuid = ?", uuid)
            } else {
                DB.getFirstRow(sql)
            }

            val results = HashMap<TicketStatus, Int>()

            results[TicketStatus.OPEN] = row.getInt("open")
            results[TicketStatus.PICKED] = row.getInt("picked")
            results[TicketStatus.CLOSED] = row.getInt("closed")

            return results
        }

        override fun exists(id: Int): Boolean {
            return DB.getFirstColumn<Int>("SELECT EXISTS(SELECT 1 FROM ticket WHERE id = ?)", id) == 1
        }

        override fun count(status: TicketStatus?): Int {
            return if (status == null) {
                DB.getFirstColumn("SELECT COUNT(id) FROM ticket")
            } else {
                DB.getFirstColumn("SELECT COUNT(id) FROM ticket WHERE status = ?", status.name)
            }
        }

        override fun insert(uuid: UUID, status: TicketStatus, picker: UUID?, location: Location): Int {
            var index = DB.getFirstColumn<Int>("SELECT max(id) FROM ticket") ?: 0
            index++

            DB.executeInsert("INSERT INTO ticket(id, uuid, status, picker, location) VALUES(?, ?, ?, ?, ?)",
                    index, uuid, status.name, picker, location.serialized())

            return index
        }

        override fun update(ticket: Ticket) {
            DB.executeUpdateAsync("UPDATE ticket SET status = ?, picker = ? WHERE id = ?",
                    ticket.status.name, ticket.pickerUUID, ticket.id)
        }
    }

    override val message = object : SQLManager.MessageFunctions {
        override fun selectAll(id: Int): ArrayList<Message> {
            val results = DB.getResults("SELECT reason, data, sender, date FROM message WHERE ticket = ?", id)
                    .map { it.buildMessage() }

            return results as ArrayList<Message>
        }

        override fun insert(ticket: Ticket, message: Message) {
            DB.executeInsert("INSERT INTO message(ticket, reason, data, sender, date) VALUES(?, ?, ?, ?, ?)",
                    ticket.id, message.reason.name, message.data, message.sender, message.date.serialized())
        }
    }

    override val notification = object : SQLManager.NotificationFunctions {
        override fun selectAllAndClear(): ArrayListMultimap<UUID, PendingNotification> {
            val multimap = ArrayListMultimap.create<UUID, PendingNotification>()

            DB.getResults("SELECT uuid, message, replacements FROM notification")
                    .map { it.getUUID("uuid") to it.buildNotification() }
                    .forEach { (uuid, notification) -> multimap.put(uuid, notification) }

            DB.executeUpdate("DELETE FROM notification")

            return multimap
        }

        override fun insertAll(notifications: ArrayListMultimap<UUID, PendingNotification>) {
            notifications.forEach { uuid, notification ->
                val message = notification.messageKey.name
                val replacements = notification.replacements.joinToString("|")

                DB.executeInsert("INSERT INTO notification(uuid, message, replacements) VALUES(?, ?, ?)", uuid, message, replacements)
            }
        }
    }

    override val settings = object : SQLManager.SettingsFunctions {
        override fun select(uuid: UUID): UserSettings {
            val data = DB.getFirstRow("SELECT announcements FROM settings WHERE uuid = ?", uuid)
            val announcements = data.getString("announcements") == "1"

            return UserSettings(announcements)
        }

        override fun exists(uuid: UUID): Boolean {
            return DB.getFirstColumn<Int>("SELECT EXISTS(SELECT 1 FROM settings WHERE uuid = ?)", uuid) == 1
        }

        override fun insert(uuid: UUID, settings: UserSettings) {
            DB.executeInsert("INSERT INTO settings(uuid, announcements) VALUES(?, ?)", uuid, settings)
        }

        override fun update(uuid: UUID, settings: UserSettings) {
            DB.executeUpdate("UPDATE settings SET announcements = ? WHERE uuid = ?", settings.announcements, uuid)
        }
    }

    override fun DbRow.getPureLong(column: String): Long {
        return getString(column).toLong()
    }
}