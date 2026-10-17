package pl.srm.biuroapi.auth.management;

import org.junit.jupiter.api.Test;
import pl.srm.biuroapi.auth.account.AccountRole;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class AccountAdministrationConcurrencyTest extends AccountManagementHttpSupport {
    @Test void concurrentSelfDemotionsAlwaysPreserveOneActiveAdministrator() throws Exception {
        String secondId = account("second.admin", Set.of(AccountRole.SYS_ADMIN), Set.of());
        String secondToken = login("second.admin", PASSWORD);
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { gate.await(); return update(adminToken, adminId, 0, true, Set.of(AccountRole.OPERATOR), Set.of()).statusCode(); });
            var b = pool.submit(() -> { gate.await(); return update(secondToken, secondId, 0, true, Set.of(AccountRole.OPERATOR), Set.of()).statusCode(); });
            gate.countDown();
            assertEquals(Set.of(200, 409), Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
            assertEquals(1, management.activeAdministrators());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM biuro_account_audit WHERE event_type='ACCOUNT_UPDATED'", Integer.class));
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }

    @Test void concurrentEditsCannotSilentlyOverwriteAccountRoles() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { gate.await(); return update(adminToken, viewerId, 0, true, Set.of(AccountRole.OPERATOR), Set.of()).statusCode(); });
            var b = pool.submit(() -> { gate.await(); return update(adminToken, viewerId, 0, true, Set.of(AccountRole.VIEWER), Set.of("ZAGLE26T2")).statusCode(); });
            gate.countDown();
            assertEquals(Set.of(200, 409), Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
            assertEquals(1, management.get(viewerId, false).version());
            assertEquals(1, events("ACCOUNT_UPDATED", viewerId));
        } finally { pool.shutdownNow(); pool.awaitTermination(10, TimeUnit.SECONDS); }
    }
}
