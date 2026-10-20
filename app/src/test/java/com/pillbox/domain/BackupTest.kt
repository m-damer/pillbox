package com.pillbox.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val at = LocalDateTime.of(2026, 10, 17, 9, 41, 30)

    private fun sample() = BackupData(
        persons = listOf(mapOf("id" to 1L, "name" to "Mahmoud \"Abu\" عمر\nline", "isMe" to true, "sortOrder" to 0L, "birthYear" to null)),
        medicines = listOf(mapOf("id" to 7L, "personId" to 1L, "name" to "Amoxicillin", "doseAmount" to 1.5, "stock" to 20.0, "endedOn" to null)),
        schedules = listOf(mapOf("id" to 3L, "medicineId" to 7L, "kind" to "FIXED", "timesCsv" to "480,1200", "shifts" to "2026-10-17T08:00>2026-10-17T09:00")),
        pauses = listOf(mapOf("id" to 2L, "medicineId" to 7L, "fromAt" to "2026-10-01T08:00:00", "untilAt" to null)),
        doseEvents = listOf(mapOf("id" to 9L, "medicineId" to 7L, "status" to "TAKEN", "early" to false, "lateMinutes" to 12L)),
    )

    @Test fun aBackupReadsBackExactly() {
        val result = BackupCodec.decode(BackupCodec.encode(sample(), at)) as BackupResult.Ok
        assertEquals(sample(), result.data)
        assertEquals(at.withNano(0), result.savedAt)
    }

    @Test fun theSummaryCountsPeopleMedicinesAndDoses() = assertEquals(BackupSummary(1, 1, 1), sample().summary)

    @Test fun decimalsKeepTheirValue() {
        val back = (BackupCodec.decode(BackupCodec.encode(sample(), at)) as BackupResult.Ok).data
        assertEquals(1.5, (back.medicines[0]["doseAmount"] as Number).toDouble(), 0.0)
        assertEquals(20.0, (back.medicines[0]["stock"] as Number).toDouble(), 0.0)
    }

    @Test fun aNewerFormatIsRefusedNotGuessed() {
        val text = BackupCodec.encode(sample(), at).replace("\"format\":1", "\"format\":2")
        assertEquals(BackupResult.Newer(2), BackupCodec.decode(text))
    }

    @Test fun otherFilesAreNotBackups() {
        listOf("", "hello", "{}", "[]", "{\"app\":\"other\",\"format\":1}", "{\"app\":\"pillbox\"}", "{\"app\":\"pillbox\",\"format\":1,\"persons\":[]}")
            .forEach { assertEquals(it, BackupResult.Invalid, BackupCodec.decode(it)) }
    }

    @Test fun aCutOffFileIsInvalid() {
        val text = BackupCodec.encode(sample(), at)
        assertEquals(BackupResult.Invalid, BackupCodec.decode(text.substring(0, text.length / 2)))
    }

    @Test fun rowsThatPointNowhereAreInvalid() {
        val broken = sample().copy(doseEvents = listOf(mapOf("id" to 9L, "medicineId" to 99L)))
        assertEquals(BackupResult.Invalid, BackupCodec.decode(BackupCodec.encode(broken, at)))
    }

    @Test fun duplicateIdsAreInvalid() {
        val broken = sample().copy(persons = sample().persons + sample().persons)
        assertEquals(BackupResult.Invalid, BackupCodec.decode(BackupCodec.encode(broken, at)))
    }

    @Test fun anEmptyBackupIsValid() {
        val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(BackupSummary(0, 0, 0), (BackupCodec.decode(BackupCodec.encode(empty, at)) as BackupResult.Ok).data.summary)
    }

    @Test fun unknownFieldsAreKeptAndMissingOnesAreFine() {
        val text = """{"app":"pillbox","format":1,"extra":5,"persons":[{"id":1,"name":"A","future":true}],"medicines":[],"schedules":[],"pauses":[],"doseEvents":[]}"""
        val ok = BackupCodec.decode(text) as BackupResult.Ok
        assertNull(ok.savedAt)
        assertEquals("A", ok.data.persons[0]["name"])
    }

    @Test fun fileNameHasTheDateAndExtension() = assertEquals("pillbox-2026-10-17.pillbox", BackupCodec.fileName(LocalDate.of(2026, 10, 17)))

    @Test fun onlyOldBackupsOfOursAreRemoved() {
        val names = listOf("pillbox-2026-10-03.pillbox", "pillbox-2026-10-17.pillbox", "pillbox-2026-10-10.pillbox", "pillbox-2026-09-26.pillbox", "pillbox-2026-09-19.pillbox", "notes.txt", "pillbox-old.pillbox")
        assertEquals(listOf("pillbox-2026-09-26.pillbox", "pillbox-2026-09-19.pillbox"), BackupCodec.toRemove(names, 3))
        assertTrue(BackupCodec.toRemove(names, 10).isEmpty())
    }

    @Test fun failingMeansTheLastTryFailedAfterTheLastSuccess() {
        val ok = at
        assertTrue(BackupSettings(lastSuccessAt = ok, lastProblem = BackupProblem.NO_SPACE, lastProblemAt = ok.plusDays(1)).failing)
        assertEquals(false, BackupSettings(lastSuccessAt = ok.plusDays(2), lastProblem = BackupProblem.NO_SPACE, lastProblemAt = ok.plusDays(1)).failing)
        assertTrue(BackupSettings(lastProblem = BackupProblem.OTHER, lastProblemAt = ok).failing)
        assertEquals(false, BackupSettings().failing)
    }
}

class BackupFoldersTest {
    @Test fun aFolderPathReadsWithSpacedSlashes() = assertEquals("Documents / Pill Box", BackupFolders.path("primary:Documents/Pill Box"))
    @Test fun theTopOfAVolumeHasNoPath() { assertEquals("", BackupFolders.path("primary:")); assertEquals("", BackupFolders.path("1234-5678:")) }
    @Test fun aCardFolderKeepsItsName() = assertEquals("Backups", BackupFolders.path("1234-5678:Backups"))
    @Test fun anIdWithoutAColonHasNoPath() = assertEquals("", BackupFolders.path("weird"))
}

class BackupFrequencyTest {
    @Test fun dailyIsOneDayAndWeeklyIsSeven() { assertEquals(1L, BackupFrequency.DAILY.days); assertEquals(7L, BackupFrequency.WEEKLY.days) }
    @Test fun aNewSetupIsWeeklyAndTellsThePerson() {
        val s = BackupSettings()
        assertEquals(BackupFrequency.WEEKLY, s.frequency)
        assertTrue(s.notify)
    }
}

class DriveApiTest {
    @Test fun uploadBodyHasTheNameThePrivateFolderAndTheFile() {
        val body = DriveApi.multipartBody("pillbox-2026-10-17.pillbox", "{\"a\":1}", "B")
        assertTrue(body.startsWith("--B\r\n"))
        assertTrue(body.contains("\"parents\":[\"appDataFolder\"]"))
        assertTrue(body.contains("\"name\":\"pillbox-2026-10-17.pillbox\""))
        assertTrue(body.contains("\r\n\r\n{\"a\":1}\r\n--B--"))
    }

    @Test fun backupsComeNewestFirstAndOtherFilesAreIgnored() {
        val json = """{"files":[
            {"id":"1","name":"pillbox-2026-10-10.pillbox","createdTime":"2026-10-10T09:41:00.000Z"},
            {"id":"2","name":"pillbox-2026-10-17.pillbox","createdTime":"2026-10-17T09:41:00.000Z"},
            {"id":"3","name":"notes.txt","createdTime":"2026-10-18T09:41:00.000Z"},
            {"name":"pillbox-x.pillbox"}]}"""
        assertEquals(listOf("2", "1"), DriveApi.parseFiles(json).map { it.id })
    }

    @Test fun anAnswerThatIsNotJsonGivesNoFiles() {
        assertEquals(emptyList<DriveFile>(), DriveApi.parseFiles("<html>"))
        assertEquals(emptyList<DriveFile>(), DriveApi.parseFiles("{}"))
    }

    @Test fun onlyTheOldestAreRemoved() {
        val files = (1..9).map { DriveFile("$it", "pillbox-$it.pillbox", "") }
        assertEquals(listOf("8", "9"), DriveApi.toRemove(files, 7).map { it.id })
    }

    @Test fun problemsAreToldInPlainTerms() {
        assertEquals(DriveProblem.NO_INTERNET, DriveApi.problemFor(null, offline = true))
        assertEquals(DriveProblem.SIGN_IN, DriveApi.problemFor(401, false))
        assertEquals(DriveProblem.OTHER, DriveApi.problemFor(500, false))
    }
}
