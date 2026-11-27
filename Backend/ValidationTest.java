package smart_event;

import smart_event.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Plain-Java validation tests (no JUnit needed). Every test is registered and called from main(),
 * so the summary always shows the real Passed/Failed counts. Exit code is 1 if anything fails.
 */
public class ValidationTest {
    private static int passed = 0, failed = 0;

    interface Body { void run() throws Exception; }

    // ------------------------------------------------------------- tiny harness
    private static void test(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("  PASS  " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  " + name + "  ->  " + t);
        }
    }

    private static void expectThrows(Class<? extends Throwable> type, String messagePart, Body body) {
        try {
            body.run();
        } catch (Throwable t) {
            if (!type.isInstance(t))
                throw new AssertionError("Expected " + type.getSimpleName() + " but got " + t);
            if (messagePart != null && (t.getMessage() == null || !t.getMessage().contains(messagePart)))
                throw new AssertionError("Expected message containing \"" + messagePart + "\" but got \"" + t.getMessage() + "\"");
            return;
        }
        throw new AssertionError("Expected " + type.getSimpleName() + " but nothing was thrown");
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual))
            throw new AssertionError("Expected <" + expected + "> but was <" + actual + ">");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    // ----------------------------------------------------------------- fixtures
    private static final LocalDate DAY = LocalDate.of(2026, 12, 1);
    private static final LocalDateTime T10 = LocalDateTime.of(DAY, LocalTime.of(10, 0));
    private static final LocalDateTime T11 = LocalDateTime.of(DAY, LocalTime.of(11, 0));
    private static final LocalDateTime T12 = LocalDateTime.of(DAY, LocalTime.of(12, 0));

    private static Venue venue(int capacity) { return new Venue("V1", "Gymnasium", "Bulan", capacity); }

    private static Event event(String id, Venue v, boolean reEntry) {
        return new Event(id, "Tech Fest", DAY, LocalTime.of(9, 0), LocalTime.of(17, 0), v, reEntry, true, true);
    }

    private static Event openEvent(Venue v, boolean reEntry) {
        Event e = event("E1", v, reEntry);
        e.startEvent();
        return e;
    }

    private static RegisteredGuest guest(String id) {
        return new RegisteredGuest(id, "user" + id, id + "@mail.com", "Guest " + id, "hash", "ELIGIBLE");
    }

    /** Registered guest with an active QR pass already assigned. */
    private static EventRegistration reg(String regId, Event e, RegisteredGuest g) {
        EventRegistration r = new EventRegistration(regId, e, g, RegistrationStatus.REGISTERED);
        r.assignPass(new QRPass("P-" + regId, "CODE-" + regId, r));
        return r;
    }

    private static OccupancyMonitor monitor(Event e, Venue v) { return new OccupancyMonitor(e, v, null, 0.8); }

    // -------------------------------------------------- basic input validations
    private static void basicValidations() {
        System.out.println("Basic input validations");
        Venue v = venue(5);
        test("empty event name is rejected", () ->
                expectThrows(IllegalArgumentException.class, "name",
                        () -> new Event("E1", "  ", DAY, LocalTime.of(9, 0), LocalTime.of(17, 0), v, true, true, true)));
        test("zero venue capacity is rejected", () ->
                expectThrows(IllegalArgumentException.class, "positive", () -> new Venue("V9", "A", "B", 0)));
        test("negative venue capacity is rejected", () ->
                expectThrows(IllegalArgumentException.class, "positive", () -> new Venue("V9", "A", "B", -3)));
        test("missing event date is rejected", () ->
                expectThrows(NullPointerException.class, "date",
                        () -> new Event("E1", "Fest", null, LocalTime.of(9, 0), LocalTime.of(17, 0), v, true, true, true)));
        test("end time before start time is rejected", () ->
                expectThrows(IllegalArgumentException.class, "End time",
                        () -> new Event("E1", "Fest", DAY, LocalTime.of(17, 0), LocalTime.of(9, 0), v, true, true, true)));
        test("invalid occupancy warning threshold is rejected", () -> {
            Event e = openEvent(v, true);
            expectThrows(IllegalArgumentException.class, "warningThreshold", () -> new OccupancyMonitor(e, v, null, 0));
            expectThrows(IllegalArgumentException.class, "warningThreshold", () -> new OccupancyMonitor(e, v, null, 1.5));
        });
        test("invalid (pending/cancelled) registration status cannot check in", () -> {
            Venue v2 = venue(5);
            Event e = openEvent(v2, true);
            OccupancyMonitor m = monitor(e, v2);
            EventRegistration pending = new EventRegistration("R1", e, guest("G1"), RegistrationStatus.PENDING);
            expectThrows(IllegalStateException.class, "not valid", () -> m.checkIn(pending, T10));
            EventRegistration cancelled = reg("R2", e, guest("G2"));
            cancelled.cancel();
            expectThrows(IllegalStateException.class, "not valid", () -> m.checkIn(cancelled, T10));
        });
    }

    // ---------------------------------------------- check-in / check-out basics
    private static void checkInOutValidations() {
        System.out.println("Check-in / check-out validations");
        test("valid check-in increases occupancy", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            m.checkIn(reg("R1", e, guest("G1")), T10);
            assertEquals(1, m.getCurrentOccupancy());
        });
        test("duplicate active check-in for the same registration is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            m.checkIn(r, T10);
            expectThrows(IllegalStateException.class, "active check-in", () -> m.checkIn(r, T11));
            assertEquals(1, m.getCurrentOccupancy());
        });
        test("check-in when venue is full is rejected", () -> {
            Venue v = venue(1); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            m.checkIn(reg("R1", e, guest("G1")), T10);
            expectThrows(IllegalStateException.class, "full", () -> m.checkIn(reg("R2", e, guest("G2")), T10));
        });
        test("check-out before check-in is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            expectThrows(IllegalStateException.class, "before check-in",
                    () -> m.checkOut(reg("R1", e, guest("G1")), T10));
        });
        test("check-out time earlier than check-in time is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            m.checkIn(r, T11);
            expectThrows(IllegalArgumentException.class, "precede", () -> m.checkOut(r, T10));
        });
        test("duplicate check-out is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            m.checkIn(r, T10);
            m.checkOut(r, T11);
            expectThrows(IllegalStateException.class, "Duplicate check-out", () -> m.checkOut(r, T12));
            assertEquals(0, m.getCurrentOccupancy());
        });
    }

    // ------------------------------------------------------------ Issue #2
    private static void reEntryPolicy() {
        System.out.println("Issue #2 - re-entry policy");
        test("re-entry is rejected when reEntryAllowed = false", () -> {
            Venue v = venue(5); Event e = openEvent(v, false); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            m.checkIn(r, T10);
            m.checkOut(r, T11);
            expectThrows(IllegalStateException.class, "Re-entry", () -> m.checkIn(r, T12));
            assertEquals(0, m.getCurrentOccupancy());
        });
        test("re-entry is allowed when reEntryAllowed = true (new record per visit)", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            m.checkIn(r, T10);
            m.checkOut(r, T11);
            m.checkIn(r, T12);
            assertEquals(1, m.getCurrentOccupancy());
            assertEquals(2, m.getAttendanceRecords().size());
        });
        test("re-entry policy cannot be dodged with a second registration of the same guest", () -> {
            Venue v = venue(5); Event e = openEvent(v, false); OccupancyMonitor m = monitor(e, v);
            RegisteredGuest g = guest("G1");
            EventRegistration r1 = reg("R1", e, g), r2 = reg("R2", e, g);
            m.checkIn(r1, T10);
            m.checkOut(r1, T11);
            expectThrows(IllegalStateException.class, "Re-entry", () -> m.checkIn(r2, T12));
        });
    }

    // ------------------------------------------------------------ Issue #3
    private static void qrValidation() {
        System.out.println("Issue #3 - QR pass validation");
        test("QR entry with missing pass is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            expectThrows(IllegalStateException.class, "QR pass is required",
                    () -> m.checkIn(r, EntryMethod.QR_CODE, null, T10));
        });
        test("QR entry with revoked pass is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            r.getQrPass().revoke();
            expectThrows(IllegalStateException.class, "revoked",
                    () -> m.checkIn(r, EntryMethod.QR_CODE, r.getQrPass(), T10));
            assertEquals(0, m.getCurrentOccupancy());
        });
        test("QR entry with expired pass is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            r.getQrPass().expire();
            expectThrows(IllegalStateException.class, "expired",
                    () -> m.checkIn(r, EntryMethod.QR_CODE, r.getQrPass(), T10));
        });
        test("QR entry with another guest's pass is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r1 = reg("R1", e, guest("G1")), r2 = reg("R2", e, guest("G2"));
            expectThrows(IllegalStateException.class, "does not belong",
                    () -> m.checkIn(r1, EntryMethod.QR_CODE, r2.getQrPass(), T10));
        });
        test("QR entry with a pass for a different event is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            Event other = event("E2", v, true);
            EventRegistration foreign = reg("R9", other, guest("G9"));
            expectThrows(IllegalArgumentException.class, "different event",
                    () -> m.checkIn(foreign, EntryMethod.QR_CODE, foreign.getQrPass(), T10));
        });
        test("QR entry with a valid pass succeeds", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            m.checkIn(r, EntryMethod.QR_CODE, r.getQrPass(), T10);
            assertEquals(1, m.getCurrentOccupancy());
        });
        test("QRPass.validate compares events by id, not by object reference", () -> {
            Venue v = venue(5);
            Event e1 = openEvent(v, true);
            Event sameIdCopy = openEvent(v, true);
            EventRegistration r = reg("R1", e1, guest("G1"));
            assertTrue(r.getQrPass().validate(sameIdCopy), "same event id should validate");
            assertTrue(!r.getQrPass().validate(event("E2", v, true)), "different event id must not validate");
        });
    }

    // ------------------------------------------------------------ Issue #4
    private static void eventStatus() {
        System.out.println("Issue #4 - event status");
        test("check-in before the event has started is rejected", () -> {
            Venue v = venue(5); Event e = event("E1", v, true); // still PLANNED
            OccupancyMonitor m = monitor(e, v);
            expectThrows(IllegalStateException.class, "not started", () -> m.checkIn(reg("R1", e, guest("G1")), T10));
        });
        test("check-in after the event was closed is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            e.closeEvent();
            expectThrows(IllegalStateException.class, "ended", () -> m.checkIn(reg("R1", e, guest("G1")), T10));
        });
        test("check-in after the scheduled end time is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            LocalDateTime late = LocalDateTime.of(DAY, LocalTime.of(18, 0));
            expectThrows(IllegalStateException.class, "ended", () -> m.checkIn(reg("R1", e, guest("G1")), late));
        });
        test("check-in to a cancelled event is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            e.cancelEvent();
            expectThrows(IllegalStateException.class, "cancelled", () -> m.checkIn(reg("R1", e, guest("G1")), T10));
        });
        test("guests can still check out after the event has ended", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            m.checkIn(r, T10);
            e.closeEvent();
            m.checkOut(r, T12);
            assertEquals(0, m.getCurrentOccupancy());
        });
    }

    // ------------------------------------------------------------ Issue #5
    private static void capacityEnforcement() {
        System.out.println("Issue #5 - capacity enforcement");
        test("lowering capacity below current occupancy is rejected (via monitor)", () -> {
            Venue v = venue(3); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            m.checkIn(reg("R1", e, guest("G1")), T10);
            m.checkIn(reg("R2", e, guest("G2")), T10);
            expectThrows(IllegalStateException.class, "inside", () -> m.changeMaximumCapacity(1));
            assertEquals(3, v.getMaximumCapacity());
            m.changeMaximumCapacity(2); // equal to occupancy is fine
            assertEquals(2, v.getMaximumCapacity());
        });
        test("lowering capacity below current occupancy is rejected (directly on Venue)", () -> {
            Venue v = venue(3); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            m.checkIn(reg("R1", e, guest("G1")), T10);
            m.checkIn(reg("R2", e, guest("G2")), T10);
            expectThrows(IllegalStateException.class, "inside", () -> v.setMaximumCapacity(1));
        });
        test("capacity can be lowered again after guests check out", () -> {
            Venue v = venue(3); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r1 = reg("R1", e, guest("G1"));
            m.checkIn(r1, T10);
            m.checkIn(reg("R2", e, guest("G2")), T10);
            m.checkOut(r1, T11);
            m.changeMaximumCapacity(1);
            assertEquals(1, v.getMaximumCapacity());
        });
        test("attendance correction that would exceed capacity is rejected and changes nothing", () -> {
            Venue v = venue(1); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r1 = reg("R1", e, guest("G1")), r2 = reg("R2", e, guest("G2"));
            AttendanceRecord a1 = m.checkIn(r1, T10);
            m.checkOut(r1, T11);
            AttendanceRecord a2 = m.checkIn(r2, T11);
            // try to "correct" guest 1 back inside while guest 2 occupies the only place
            expectThrows(IllegalStateException.class, "capacity",
                    () -> m.correctAttendance(a1.getRecordId(), T10, null, EntryMethod.MANUAL, "scanner error"));
            assertEquals(T11, a1.getCheckOutTime());
            assertEquals(1, m.getCurrentOccupancy());
            assertTrue(a2.isInside(), "guest 2 must still be inside");
        });
        test("attendance correction within capacity is accepted", () -> {
            Venue v = venue(2); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r1 = reg("R1", e, guest("G1"));
            AttendanceRecord a1 = m.checkIn(r1, T10);
            m.checkOut(r1, T11);
            m.correctAttendance(a1.getRecordId(), T10, null, EntryMethod.MANUAL, "left by mistake");
            assertEquals(1, m.getCurrentOccupancy());
            assertEquals(RecordStatus.CORRECTED, a1.getRecordStatus());
        });
        test("correction cannot create a second active record for the same guest", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            AttendanceRecord first = m.checkIn(r, T10);
            m.checkOut(r, T11);
            m.checkIn(r, T12);
            expectThrows(IllegalStateException.class, "second active",
                    () -> m.correctAttendance(first.getRecordId(), T10, null, EntryMethod.MANUAL, "fix"));
        });
        test("correction with check-out before check-in is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            AttendanceRecord a = m.checkIn(reg("R1", e, guest("G1")), T10);
            expectThrows(IllegalArgumentException.class, "precede",
                    () -> m.correctAttendance(a.getRecordId(), T11, T10, EntryMethod.MANUAL, "fix"));
        });
        test("a corrected record that is still inside keeps counting toward occupancy", () -> {
            Venue v = venue(2); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            AttendanceRecord a = m.checkIn(reg("R1", e, guest("G1")), T10);
            m.correctAttendance(a.getRecordId(), T10.minusMinutes(5), null, EntryMethod.MANUAL, "early arrival");
            assertEquals(1, m.getCurrentOccupancy());
        });
    }

    // ------------------------------------------------------------ Issue #6
    private static void duplicatesAndIsolation() {
        System.out.println("Issue #6 - duplicate registration and event isolation");
        test("same guest with two different registration objects cannot check in twice", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            RegisteredGuest g = guest("G1");
            EventRegistration r1 = reg("R1", e, g), r2 = reg("R2", e, g);
            m.checkIn(r1, T10);
            expectThrows(IllegalStateException.class, "active check-in", () -> m.checkIn(r2, T11));
            assertEquals(1, m.getCurrentOccupancy());
        });
        test("same guest identity from a different Java object is still a duplicate", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r1 = reg("R1", e, guest("G1")), r2 = reg("R2", e, guest("G1")); // two objects, same userId
            m.checkIn(r1, T10);
            expectThrows(IllegalStateException.class, "active check-in", () -> m.checkIn(r2, T11));
        });
        test("registration for another event cannot be checked in here", () -> {
            Venue v = venue(5); Event e1 = openEvent(v, true); Event e2 = event("E2", v, true);
            e2.startEvent();
            OccupancyMonitor m = monitor(e1, v);
            expectThrows(IllegalArgumentException.class, "different event",
                    () -> m.checkIn(reg("R1", e2, guest("G1")), T10));
        });
        test("same guest may attend two different events (isolation)", () -> {
            Venue v1 = venue(5), v2 = new Venue("V2", "Hall", "Bulan", 5);
            Event e1 = openEvent(v1, false);
            Event e2 = event("E2", v2, false); e2.startEvent();
            OccupancyMonitor m1 = monitor(e1, v1), m2 = monitor(e2, v2);
            RegisteredGuest g = guest("G1");
            m1.checkIn(reg("R1", e1, g), T10);
            m2.checkIn(reg("R2", e2, g), T10);
            assertEquals(1, m1.getCurrentOccupancy());
            assertEquals(1, m2.getCurrentOccupancy());
        });
        test("attendance record of another event cannot be added to this monitor", () -> {
            Venue v = venue(5); Event e1 = openEvent(v, true); Event e2 = event("E2", v, true);
            OccupancyMonitor m = monitor(e1, v);
            AttendanceRecord foreign = new AttendanceRecord("AR-X", reg("R1", e2, guest("G1")), EntryMethod.MANUAL);
            expectThrows(IllegalArgumentException.class, "different event", () -> m.addAttendanceRecord(foreign));
        });
        test("adding two active records for the same guest is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true);
            RegisteredGuest g = guest("G1");
            // Records can only be checked in through a monitor, so create them with two helper monitors.
            OccupancyMonitor m = monitor(e,v);
            AttendanceRecord a = m.checkIn(reg("R1", e, g), T10);
            Venue helperVenue = venue(5); Event helperEvent = openEvent(helperVenue,true);
            OccupancyMonitor helper = monitor(helperEvent, helperVenue);
            helper.checkIn(reg("R9", e, guest("G9")), T10); // makes the next record id differ from a's
            AttendanceRecord b = helper.checkIn(reg("R2", e, g), T11);
            m.addAttendanceRecord(a);
            expectThrows(IllegalStateException.class, "active check-in", () -> m.addAttendanceRecord(b));
            assertEquals(1, m.getAttendanceRecords().size());
        });
        test("adding a different record that reuses an existing record id is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true);
            OccupancyMonitor m = monitor(e,v);
            AttendanceRecord a = m.checkIn(reg("R1", e, guest("G1")), T10);
            AttendanceRecord sameId = new AttendanceRecord(a.getRecordId(), reg("R2", e, guest("G2")), EntryMethod.MANUAL);
            m.addAttendanceRecord(a);
            m.addAttendanceRecord(a); // adding the very same record again is a harmless no-op
            assertEquals(1, m.getAttendanceRecords().size());
            expectThrows(IllegalArgumentException.class, "already uses id", () -> m.addAttendanceRecord(sameId));
            assertEquals(1, m.getAttendanceRecords().size());
        });
        test("monitor rejects a venue that is not the event's venue", () -> {
            Venue v = venue(5); Event e = openEvent(v, true);
            Venue other = new Venue("V2", "Other", "Bulan", 5);
            expectThrows(IllegalArgumentException.class, "Venue", () -> new OccupancyMonitor(e, other, List.of(), 0.8));
        });
    }


    // ------------------------------------------------- Remaining issue #1
    private static void attendanceRecordIsReadOnlyOutsideMonitor() {
        System.out.println("Remaining #1 - AttendanceRecord cannot be changed behind OccupancyMonitor's back");
        for (String name : new String[]{"checkIn", "checkOut"})
            test("AttendanceRecord." + name + " is not public", () ->
                    expectThrows(NoSuchMethodException.class, null,
                            () -> AttendanceRecord.class.getMethod(name, LocalDateTime.class)));
        test("AttendanceRecord.correctAttendance is not public", () ->
                expectThrows(NoSuchMethodException.class, null,
                        () -> AttendanceRecord.class.getMethod("correctAttendance",
                                LocalDateTime.class, LocalDateTime.class, EntryMethod.class, String.class)));
        test("AttendanceRecord exposes no public method that changes state", () -> {
            for (java.lang.reflect.Method m : AttendanceRecord.class.getMethods()) {
                if (m.getDeclaringClass() != AttendanceRecord.class) continue;
                String n = m.getName();
                assertTrue(n.startsWith("get") || n.startsWith("is"),
                        "unexpected public method on AttendanceRecord: " + n);
            }
        });
        test("record state changes only through the monitor and keeps occupancy consistent", () -> {
            Venue v = venue(1); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            AttendanceRecord a = m.checkIn(r, T10);
            assertTrue(a.isInside(), "record should be inside after monitor check-in");
            expectThrows(IllegalStateException.class, "full", () -> m.checkIn(reg("R2", e, guest("G2")), T10));
            m.checkOut(r, T11);
            assertEquals(0, m.getCurrentOccupancy());
            assertEquals(RecordStatus.COMPLETED, a.getRecordStatus());
        });
    }

    // ------------------------------------------------- Remaining issue #2
    private static void eventUpdateDetailsIsAtomic() {
        System.out.println("Remaining #2 - Event.updateDetails validates before assigning");
        final LocalTime S = LocalTime.of(9, 0), E = LocalTime.of(17, 0);
        final LocalDate NEW_DAY = DAY.plusDays(3);
        final LocalTime NS = LocalTime.of(13, 0), NE = LocalTime.of(15, 0);

        test("valid update changes every field", () -> {
            Event e = event("E1", venue(5), true);
            e.updateDetails("New Name", NEW_DAY, NS, NE);
            assertEquals("New Name", e.getName());
            assertEquals(NEW_DAY, e.getDate());
            assertEquals(NS, e.getStartTime());
            assertEquals(NE, e.getEndTime());
        });
        // Each case supplies exactly one bad input next to otherwise valid new values.
        // Valid new values must NOT be applied when another input is rejected.
        Object[][] cases = {
                {"blank name",        IllegalArgumentException.class, "name",      "   ", NEW_DAY, NS, NE},
                {"null name",         IllegalArgumentException.class, "name",      null,  NEW_DAY, NS, NE},
                {"null date",         NullPointerException.class,     "date",      "New", null,    NS, NE},
                {"null start time",   NullPointerException.class,     "startTime", "New", NEW_DAY, null, NE},
                {"null end time",     NullPointerException.class,     "endTime",   "New", NEW_DAY, NS, null},
                {"end before start",  IllegalArgumentException.class, "End time",  "New", NEW_DAY, NE, NS},
                {"end equal to start", IllegalArgumentException.class, "End time", "New", NEW_DAY, NS, NS},
        };
        for (Object[] c : cases) {
            test("rejected updateDetails (" + c[0] + ") leaves the event unchanged", () -> {
                Event e = event("E1", venue(5), true);
                @SuppressWarnings("unchecked") Class<? extends Throwable> type = (Class<? extends Throwable>) c[1];
                expectThrows(type, (String) c[2],
                        () -> e.updateDetails((String) c[3], (LocalDate) c[4], (LocalTime) c[5], (LocalTime) c[6]));
                assertEquals("Tech Fest", e.getName());
                assertEquals(DAY, e.getDate());
                assertEquals(S, e.getStartTime());
                assertEquals(E, e.getEndTime());
            });
        }
        test("event stays usable after a rejected update (end time still after start time)", () -> {
            Event e = event("E1", venue(5), true);
            expectThrows(IllegalArgumentException.class, "End time", () -> e.updateDetails("X", NEW_DAY, NE, NS));
            assertTrue(e.getEndTime().isAfter(e.getStartTime()), "event must keep a valid time range");
        });
    }

    // ------------------------------------------------- Remaining issue #3
    private static void checkInBeforeScheduledStart() {
        System.out.println("Remaining #3 - no check-in before the scheduled start time");
        final LocalDateTime BEFORE = LocalDateTime.of(DAY, LocalTime.of(8, 59));
        final LocalDateTime AT_START = LocalDateTime.of(DAY, LocalTime.of(9, 0));
        test("check-in one minute before the start time is rejected (event already OPEN)", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            expectThrows(IllegalStateException.class, "before the scheduled", () -> m.checkIn(reg("R1", e, guest("G1")), BEFORE));
            assertEquals(0, m.getCurrentOccupancy());
            assertEquals(0, m.getAttendanceRecords().size());
        });
        test("QR check-in before the start time is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            expectThrows(IllegalStateException.class, "before the scheduled",
                    () -> m.checkIn(r, EntryMethod.QR_CODE, r.getQrPass(), BEFORE));
            assertEquals(0, m.getCurrentOccupancy());
        });
        test("check-in on the day before the event is rejected", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            LocalDateTime dayBefore = LocalDateTime.of(DAY.minusDays(1), LocalTime.of(12, 0));
            expectThrows(IllegalStateException.class, "before the scheduled", () -> m.checkIn(reg("R1", e, guest("G1")), dayBefore));
        });
        test("check-in exactly at the start time is accepted", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            m.checkIn(reg("R1", e, guest("G1")), AT_START);
            assertEquals(1, m.getCurrentOccupancy());
        });
        test("a rejected early check-in does not block a valid check-in afterwards", () -> {
            Venue v = venue(5); Event e = openEvent(v, false); OccupancyMonitor m = monitor(e, v);
            EventRegistration r = reg("R1", e, guest("G1"));
            expectThrows(IllegalStateException.class, "before the scheduled", () -> m.checkIn(r, BEFORE));
            m.checkIn(r, T10); // re-entry is disabled, but the rejected attempt must not count as a visit
            assertEquals(1, m.getCurrentOccupancy());
        });
        test("correction cannot move a check-in before the start time", () -> {
            Venue v = venue(5); Event e = openEvent(v, true); OccupancyMonitor m = monitor(e, v);
            AttendanceRecord a = m.checkIn(reg("R1", e, guest("G1")), T10);
            expectThrows(IllegalStateException.class, "before the scheduled",
                    () -> m.correctAttendance(a.getRecordId(), BEFORE, null, EntryMethod.MANUAL, "fix"));
            assertEquals(T10, a.getCheckInTime());
            assertEquals(RecordStatus.ACTIVE, a.getRecordStatus());
        });
        test("a record checked in before the start time cannot be added to a monitor", () -> {
            Venue v = venue(5); Event early = event("E1", v, true);
            early.updateDetails("Early", DAY, LocalTime.of(6, 0), LocalTime.of(17, 0));
            early.startEvent();
            AttendanceRecord a = monitor(early, v).checkIn(reg("R1", early, guest("G1")), LocalDateTime.of(DAY, LocalTime.of(7, 0)));
            v = venue(5); Event later = event("E1", v, true); later.startEvent(); // same id, scheduled start 09:00
            OccupancyMonitor m = monitor(later, v);
            expectThrows(IllegalStateException.class, "before the scheduled", () -> m.addAttendanceRecord(a));
            assertEquals(0, m.getAttendanceRecords().size());
        });
    }

    // ------------------------------------------------- Remaining issue #4
    private static void nonFiniteWarningThreshold() {
        System.out.println("Remaining #4 - non-finite warning thresholds are rejected");
        double[] bad = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0, -0.0, -0.5, 1.0000001};
        for (double t : bad)
            test("warningThreshold " + t + " is rejected", () -> {
                Venue v = venue(5); Event e = openEvent(v, true);
                expectThrows(IllegalArgumentException.class, "warningThreshold", () -> new OccupancyMonitor(e, v, null, t));
            });
        test("a rejected threshold leaves the venue free to change capacity", () -> {
            Venue v = venue(5); Event e = openEvent(v, true);
            expectThrows(IllegalArgumentException.class, "warningThreshold", () -> new OccupancyMonitor(e, v, null, Double.NaN));
            v.setMaximumCapacity(2);
            assertEquals(2, v.getMaximumCapacity());
        });
        test("boundary thresholds 1.0 and 0.5 are accepted and drive isNearCapacity", () -> {
            Venue v = venue(4); Event e = openEvent(v, true);
            OccupancyMonitor half = new OccupancyMonitor(e, v, null, 0.5);
            Venue v2 = venue(4); Event e2 = openEvent(v2, true);
            OccupancyMonitor full = new OccupancyMonitor(e2, v2, null, 1.0);
            assertTrue(!half.isNearCapacity(), "empty venue is not near capacity");
            half.checkIn(reg("R1", e, guest("G1")), T10);
            half.checkIn(reg("R2", e, guest("G2")), T10);
            assertTrue(half.isNearCapacity(), "2 of 4 reaches a 0.5 threshold");
            assertTrue(!full.isNearCapacity(), "no one in this monitor, threshold 1.0 not reached");
        });
    }

    // -------------------------------------------------------------------- main
    public static void main(String[] args) {
        basicValidations();
        checkInOutValidations();
        reEntryPolicy();
        qrValidation();
        eventStatus();
        capacityEnforcement();
        duplicatesAndIsolation();
        attendanceRecordIsReadOnlyOutsideMonitor();
        eventUpdateDetailsIsAtomic();
        checkInBeforeScheduledStart();
        nonFiniteWarningThreshold();
        test("duplicate monitor rejected", () -> {
            Venue v = venue(1); Event e = openEvent(v, true); monitor(e,v);
            expectThrows(IllegalStateException.class, "already", () -> monitor(e,v));
            expectThrows(IllegalStateException.class, "already", () -> monitor(openEvent(v,true),v));
        });
        test("venue update is atomic", () -> {
            Venue v = venue(5);
            expectThrows(IllegalArgumentException.class, "location", () -> v.updateDetails("New", ""));
            assertEquals("Gymnasium", v.getPlaceName()); assertEquals("Bulan", v.getLocation());
        });
        test("profile update is atomic", () -> {
            RegisteredGuest g = guest("G1");
            expectThrows(IllegalArgumentException.class, "fullName", () -> g.updateProfile("new", "new@mail.com", ""));
            assertEquals("userG1", g.getUsername()); assertEquals("G1@mail.com", g.getEmail());
        });
        test("monitored event cannot change venue", () -> {
            Venue v = venue(5); Event e = openEvent(v,true); monitor(e,v);
            expectThrows(IllegalStateException.class, "venue", () -> e.assignVenue(venue(1)));
            assertTrue(e.getVenue() == v, "venue unchanged");
        });

        System.out.println();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (passed + failed == 0) {
            System.out.println("ERROR: no tests were executed.");
            System.exit(2);
        }
        if (failed > 0) System.exit(1);
    }
}
