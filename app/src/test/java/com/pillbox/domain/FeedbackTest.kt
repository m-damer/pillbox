package com.pillbox.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    @Test fun emailChecks() {
        assertEquals(EmailCheck.OK, FeedbackMail.checkEmail("name@example.com"))
        assertEquals(EmailCheck.OK, FeedbackMail.checkEmail("  a.b+c@mail.example.co  "))
        assertEquals(EmailCheck.EMPTY, FeedbackMail.checkEmail("   "))
        for (bad in listOf("name", "name@", "@example.com", "name@example", "name@example.", "a b@example.com", "a@@example.com", "a@b@example.com", "name@.com")) {
            assertEquals(bad, EmailCheck.INVALID, FeedbackMail.checkEmail(bad))
        }
    }

    @Test fun feedbackMailHasMessageContactAndInfo() {
        val m = FeedbackMail.feedback("A problem", " It crashes ", " me@x.com ", "", FeedbackMail.appInfo("1.0", "Pixel 8", "15"))
        assertEquals(Contact.DEVELOPER_EMAIL, m.to)
        assertEquals("Pill Box feedback: A problem", m.subject)
        assertTrue(m.body.startsWith("It crashes\n"))
        assertTrue("Email: me@x.com" in m.body)
        assertFalse("Phone: \n" in m.body)
        assertTrue("Pixel 8" in m.body)
    }

    @Test fun phoneAndInfoAreOptional() {
        val m = FeedbackMail.feedback("An idea", "Hi", "me@x.com", "0999", null)
        assertTrue("Phone: 0999" in m.body)
        assertFalse("Pill Box 1.0" in m.body)
    }

    @Test fun ratingIsKeptBetweenOneAndFive() {
        assertTrue("rating: 5/5" in FeedbackMail.rating(9, "i").subject)
        assertTrue("rating: 1/5" in FeedbackMail.rating(0, "i").subject)
    }

    @Test fun crashTextKeepsTypesAndFramesButNoMessages() {
        val t = FeedbackMail.crashText("main", listOf("java.lang.IllegalStateException" to listOf("com.pillbox.A.b(A.kt:1)"), "java.io.IOException" to listOf("x.y(Z.java:2)")))
        assertTrue("Error: java.lang.IllegalStateException" in t)
        assertTrue("Caused by: java.io.IOException" in t)
        assertTrue("  at com.pillbox.A.b(A.kt:1)" in t)
    }

    @Test fun longCrashTextIsCut() {
        val frames = List(30) { "com.pillbox.Very.Long.Class.Name.method$it(File.kt:$it)" }
        val t = FeedbackMail.crashText("main", List(10) { "E$it" to frames }, maxChars = 500)
        assertTrue(t.length <= 504)
        assertTrue(t.endsWith("..."))
    }
}
