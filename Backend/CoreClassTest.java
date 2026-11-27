package smart_event;
import smart_event.model.*;
import java.time.*;
import java.util.List;

/** Task 2.05: explicit behavior coverage for every core model class. */
public class CoreClassTest {
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

    private static Administrator admin(boolean allowed) {
        return new Administrator("A1", "admin", "a@mail.com", "Admin", "hash", allowed);
    }
    public static void main(String[] args) {
        test("UserAccount: login and logout", () -> { UserAccount u = guest("G1"); u.login("hash"); assertTrue(u.isLoggedIn(), "logged in"); u.logout(); assertTrue(!u.isLoggedIn(), "logged out"); });
        test("Administrator: publishing permission", () -> { Administrator a = admin(false); a.setCanPublishAlbum(true); assertTrue(a.canPublishAlbum(), "permission updated"); });
        test("EventStaff: role update", () -> { EventStaff s = new EventStaff("S1","staff","s@mail.com","Staff","hash","Gate"); s.changeEventRole("Exit"); assertEquals("Exit", s.getEventRole()); });
        test("RegisteredGuest: eligibility", () -> { RegisteredGuest g = guest("G1"); assertTrue(g.isEligible(), "eligible"); g.updateEligibilityStatus("INELIGIBLE"); assertTrue(!g.isEligible(), "not eligible"); });
        test("Venue: capacity update", () -> { Venue v = venue(5); v.setMaximumCapacity(8); assertEquals(8,v.getMaximumCapacity()); });
        test("Event: start", () -> assertEquals(EventStatus.OPEN, openEvent(venue(5),true).getStatus()));
        test("EventRegistration: cancellation", () -> { EventRegistration r = reg("R1",openEvent(venue(5),true),guest("G1")); assertTrue(r.isValid(),"valid"); r.cancel(); assertTrue(!r.isValid(),"cancelled"); });
        test("QRPass: revoke", () -> { Event e = openEvent(venue(5),true); QRPass p = reg("R1",e,guest("G1")).getQrPass(); assertTrue(p.validate(e),"valid"); p.revoke(); assertTrue(!p.validate(e),"revoked"); });
        test("AttendanceRecord: check-in and checkout", () -> { Venue v=venue(5); Event e=openEvent(v,true); OccupancyMonitor m=monitor(e,v); EventRegistration r=reg("R1",e,guest("G1")); AttendanceRecord a=m.checkIn(r,T10); assertEquals(T10,a.getCheckInTime()); m.checkOut(r,T11); assertEquals(T11,a.getCheckOutTime()); assertTrue(!a.isInside(),"checked out"); });
        test("OccupancyMonitor: capacity accounting", () -> { Venue v=venue(5); Event e=openEvent(v,true); OccupancyMonitor m=monitor(e,v); m.checkIn(reg("R1",e,guest("G1")),T10); assertEquals(1,m.getCurrentOccupancy()); });
        test("Photo: publish and hide", () -> { Photo p=new Photo("P1",event("E1",venue(5),true),guest("G1"),T10,true); p.publish(); assertTrue(p.isDownloadable(),"published"); p.hide(); assertTrue(!p.isDownloadable(),"hidden"); });
        test("PhotoGallery: published filtering", () -> { Event e=event("E1",venue(5),true); Photo p=new Photo("P1",e,guest("G1"),T10,true); PhotoGallery g=new PhotoGallery(e,List.of(p),true); assertEquals(0,g.listPublishedPhotos().size()); p.publish(); assertEquals(List.of(p),g.listPublishedPhotos()); });
        test("PhotoModeration: approval", () -> { RegisteredGuest g=guest("G1"); Photo p=new Photo("P1",event("E1",venue(5),true),g,T10,true); PhotoModeration m=new PhotoModeration("M1",p,g,"Review",T11); m.approve(admin(true)); assertEquals(ModerationDecision.APPROVED,m.getDecision()); assertEquals(PhotoStatus.PUBLISHED,p.getStatus()); });
        test("FinalAlbum: authorized publication", () -> { FinalAlbum a=new FinalAlbum("A1",event("E1",venue(5),true),List.of()); a.publish(admin(true)); assertEquals(PublishStatus.PUBLISHED,a.getPublishStatus()); });
        test("Notification: failed delivery and retry", () -> { Notification n=new Notification("N1",guest("G1"),"Hello",NotificationChannel.IN_APP); n.recordDeliveryAttempt(false); assertEquals(DeliveryStatus.FAILED,n.getDeliveryStatus()); n.retry(); n.recordDeliveryAttempt(true); assertEquals(DeliveryStatus.SENT,n.getDeliveryStatus()); assertEquals(2,n.getDeliveryAttempts()); });
        test("EventReport: update metrics", () -> { EventReport r=new EventReport("R1",event("E1",venue(5),true),T12,1,1,1,0); r.updateMetrics(3,2,2,1); assertEquals(3,r.getRegisteredCount()); assertTrue(r.export().contains("Check-ins: 2"),"export"); });
        test("AuditLog: record and search", () -> { AuditLog a=new AuditLog(); a.recordAction(admin(true),"UPDATE","Changed event","Correction"); assertEquals(1,a.search("changed").size()); assertEquals(0,a.search("missing").size()); });
        test("Invalid credentials", () -> expectThrows(IllegalArgumentException.class,"credentials",()->guest("G1").login("wrong")));
        test("Invalid staff role", () -> expectThrows(IllegalArgumentException.class,"eventRole",()->new EventStaff("S1","staff","s@mail.com","Staff","hash","")));
        test("Invalid venue capacity", () -> expectThrows(IllegalArgumentException.class,"positive",()->venue(-1)));
        test("Invalid photo ID", () -> expectThrows(IllegalArgumentException.class,"photoId",()->new Photo("",event("E1",venue(5),true),guest("G1"),T10,true)));
        test("Invalid album permission", () -> expectThrows(SecurityException.class,"authorized",()->new FinalAlbum("A1",event("E1",venue(5),true),List.of()).publish(admin(false))));
        test("Invalid notification message", () -> expectThrows(IllegalArgumentException.class,"message",()->new Notification("N1",guest("G1"),"",NotificationChannel.EMAIL)));
        test("Invalid report metrics", () -> expectThrows(IllegalArgumentException.class,"negative",()->new EventReport("R1",event("E1",venue(5),true),T12,-1,0,0,0)));
        test("Invalid audit action", () -> expectThrows(IllegalArgumentException.class,"actionType",()->new AuditLog().recordAction(admin(true),"","Description","Reason")));
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed != 0 || passed == 0) System.exit(1);
    }
}
