package com.booking.engine.platform.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.booking.engine.entity.*;
import com.booking.engine.entity.Service;
import com.booking.engine.platform.kafka.*;
import com.booking.engine.platform.repository.*;
import com.booking.engine.platform.security.JwtTokenService;
import com.booking.engine.platform.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in real-infrastructure measurements. No production code or live Stripe calls are changed. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:55432/booking_evaluation",
    "spring.datasource.username=evaluation", "spring.datasource.password=evaluation_local_only",
    "spring.data.redis.host=localhost", "spring.data.redis.port=56379",
    "spring.kafka.bootstrap-servers=localhost:59092",
    "app.jwt.secret=local-evaluation-only-secret-at-least-32-characters",
    "app.stripe.secret-key=sk_test_evaluation_not_a_real_key",
    "app.stripe.webhook-secret=whsec_evaluation_not_a_real_key",
    "app.redis.booking-hold-rate-limit.max-attempts=100000",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=OFF", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
    "logging.level.org.apache.kafka=WARN", "spring.jpa.show-sql=false"
})
@EnabledIfSystemProperty(named = "booking.evaluate", matches = "true")
class PlatformEvaluationTest {
    @LocalServerPort int port;
    @Autowired OrganizationRepository organizations;
    @Autowired UserRepository users;
    @Autowired MembershipRepository memberships;
    @Autowired ServiceRepository services;
    @Autowired StaffRepository staff;
    @Autowired CustomerRepository customers;
    @Autowired BookingRepository bookings;
    @Autowired JwtTokenService tokens;
    @Autowired OrganizationServiceCatalog catalog;
    @Autowired RedisBookingHoldService holds;
    @Autowired RedisRateLimiter limiter;
    @Autowired StringRedisTemplate redis;
    @Autowired EntityManagerFactory emf;
    @Autowired BookingAnalyticsService analytics;
    @Autowired KafkaTemplate<String, BookingConfirmedEvent> kafka;
    @MockitoBean StripePaymentService stripePayments;
    @MockitoSpyBean NotificationConsumer notifications;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final List<Tenant> tenants = new ArrayList<>();
    record Tenant(Organization org, Staff staff, Service service, Map<MembershipRole, String> tokens) {}
    record Reply(int status, String body, double millis) {}

    @Test
    void measurePlatformAndWriteEvidence() throws Exception {
        Path output = Path.of("target/evaluation"); Files.createDirectories(output);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("revision", System.getProperty("evaluation.revision", "unspecified"));
        report.put("java", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("jvmMaxHeapMb", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        report.put("measuredAt", Instant.now().toString());
        report.put("infrastructure", List.of("PostgreSQL 16", "Redis 7", "Kafka 3.8.0"));
        seed();
        report.put("fixture", Map.of("organizations", 20, "membershipUsers", 60, "initialServices", 1000,
                "staff", 20, "holdRateLimitForEvaluation", 100000, "paymentBoundary", "mock; no Stripe network calls"));
        report.put("tenantRbac", rbac()); write(output, report);
        report.put("cache", cache()); write(output, report);
        report.put("httpReads", reads()); write(output, report);
        report.put("sameSlotHolds", contention()); write(output, report);
        report.put("fixedWindowLimiter", rateLimit()); write(output, report);
        report.put("knownConcurrencyDiagnostics", diagnostics()); write(output, report);
        report.put("kafka", events()); write(output, report);
    }

    private void seed() {
        assertThat(organizations.count()).as("Use fresh disposable evaluation infrastructure").isZero();
        String run = UUID.randomUUID().toString();
        for (int i = 0; i < 20; i++) {
            Organization org = new Organization(); org.setName("Evaluation " + run + " " + i); org.setTimezone("Europe/Zurich");
            org = organizations.save(org);
            Map<MembershipRole, String> auth = new EnumMap<>(MembershipRole.class);
            for (MembershipRole role : MembershipRole.values()) {
                User user = new User(); user.setEmail(run + "-" + i + "-" + role + "@example.test");
                user.setPasswordHash("not-used-evaluation-authenticates-with-issued-jwt"); user.setRole(UserRole.PROVIDER); user = users.save(user);
                Membership m = new Membership(); m.setOrganization(org); m.setUser(user); m.setRole(role); memberships.save(m);
                auth.put(role, tokens.create(user));
            }
            List<Service> batch = new ArrayList<>();
            for (int j = 0; j < 50; j++) {
                Service s = new Service(); s.setOrganization(org); s.setName("tenant-" + i + "-service-" + j);
                s.setDurationMinutes(30); s.setPrice(BigDecimal.TEN); batch.add(s);
            }
            List<Service> saved = services.saveAll(batch);
            Staff member = new Staff(); member.setOrganization(org); member.setName("staff-" + i); member = staff.save(member);
            tenants.add(new Tenant(org, member, saved.getFirst(), auth));
        }
    }

    private Map<String, Object> rbac() throws Exception {
        List<Map<String, Object>> cases = new ArrayList<>();
        List<String> endpoints = List.of("services", "services-write", "staff", "bookings", "analytics", "audit-logs");
        for (int i = 0; i < tenants.size(); i++) {
            Tenant t = tenants.get(i);
            for (String role : List.of("OWNER", "ADMIN", "STAFF", "FOREIGN_OWNER")) {
                String token = role.equals("FOREIGN_OWNER") ? tenants.get((i + 1) % tenants.size()).tokens.get(MembershipRole.OWNER)
                        : t.tokens.get(MembershipRole.valueOf(role));
                for (String ep : endpoints) {
                    boolean allowed = !role.equals("FOREIGN_OWNER") && (!ep.equals("services-write") || !role.equals("STAFF"))
                            && (!(ep.equals("analytics") || ep.equals("audit-logs")) || role.equals("OWNER"));
                    boolean write = ep.equals("services-write");
                    Reply reply = request(write ? "POST" : "GET", orgPath(t) + (write ? "services" : ep), token,
                            write ? Map.of("name", "matrix-" + role, "durationMinutes", 15, "price", 5) : null);
                    assertThat(reply.status).as("tenant %s role %s endpoint %s", i, role, ep).isEqualTo(allowed ? 200 : 403);
                    if (allowed && ep.equals("services")) {
                        Set<String> ownIds = new HashSet<>(services.findAllByOrganizationId(t.org.getId()).stream().map(s -> s.getId().toString()).toList());
                        for (var item : json.readTree(reply.body)) assertThat(ownIds).contains(item.get("id").asText());
                    }
                    cases.add(Map.of("tenantIndex", i, "role", role, "endpoint", ep, "expected", allowed ? 200 : 403, "actual", reply.status));
                }
            }
        }
        Reply unauthenticated = request("GET", orgPath(tenants.getFirst()) + "services", null, null);
        assertThat(unauthenticated.status).isIn(401, 403);
        return Map.of("matrixRequests", cases.size(), "passed", cases.size(), "cases", cases,
                "unauthenticatedRequestStatus", unauthenticated.status);
    }

    private Map<String, Object> cache() {
        var stats = emf.unwrap(SessionFactory.class).getStatistics();
        Tenant t = tenants.getFirst();
        // Warm both paths; eviction is outside the measured warm-cache window.
        services.findAllByOrganizationId(t.org.getId()); catalog.servicesFor(t.org.getId());
        stats.clear();
        List<Double> direct = new ArrayList<>();
        for (int i = 0; i < 100; i++) { long at = System.nanoTime(); services.findAllByOrganizationId(t.org.getId()); direct.add(ms(at)); }
        long directSql = stats.getPrepareStatementCount();
        catalog.invalidate(t.org.getId()); stats.clear();
        List<Double> cached = new ArrayList<>();
        long first = System.nanoTime(); catalog.servicesFor(t.org.getId()); cached.add(ms(first));
        long coldSql = stats.getPrepareStatementCount(); stats.clear();
        for (int i = 0; i < 99; i++) { long at = System.nanoTime(); catalog.servicesFor(t.org.getId()); cached.add(ms(at)); }
        long warmSql = stats.getPrepareStatementCount(), cachedSql = coldSql + warmSql;
        assertThat(directSql).isEqualTo(100); assertThat(warmSql).isZero(); assertThat(cachedSql).isLessThan(directSql);
        return Map.of("readsPerPath", 100, "directSqlStatements", directSql, "cachedSqlStatements", cachedSql,
                "coldReadSqlStatements", coldSql, "warmReadSqlStatements", warmSql,
                "directInProcessMs", percentiles(direct), "cachedInProcessMs", percentiles(cached),
                "scope", "direct service-catalogue reads; excludes HTTP/authentication; one cold read then 99 warm hits");
    }

    private List<Map<String, Object>> reads() throws Exception {
        for (int i = 0; i < 40; i++) {
            Tenant t = tenants.get(i % tenants.size()); request("GET", orgPath(t) + "services", t.tokens.get(MembershipRole.OWNER), null);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (int concurrency : List.of(1, 8, 32)) {
            long start = System.nanoTime();
            List<Reply> replies = parallel(200, concurrency, i -> {
                Tenant t = tenants.get(i % tenants.size());
                return uncheckedRequest("GET", orgPath(t) + "services", t.tokens.get(MembershipRole.OWNER), null);
            });
            double duration = ms(start);
            assertThat(replies).allMatch(r -> r.status == 200);
            result.add(Map.of("workers", concurrency, "requests", replies.size(), "successes", replies.size(),
                    "elapsedMs", duration, "observedRequestsPerSecond", replies.size() * 1000 / duration,
                    "latencyMs", percentiles(replies.stream().map(Reply::millis).toList())));
        }
        return result;
    }

    private Map<String, Object> contention() throws Exception {
        Tenant t = tenants.getFirst(); List<Map<String, Object>> rounds = new ArrayList<>();
        for (int round = 0; round < 10; round++) {
            Instant start = Instant.parse("2030-01-01T09:00:00Z").plusSeconds(round * 3600L);
            List<Reply> replies = parallel(32, 32, i -> uncheckedRequest("POST", "/api/v1/booking-holds", t.tokens.get(MembershipRole.OWNER),
                    Map.of("organizationId", t.org.getId(), "staffId", t.staff.getId(), "serviceId", t.service.getId(), "startTime", start.toString())));
            long winners = replies.stream().filter(r -> r.status == 200).count(), conflicts = replies.stream().filter(r -> r.status == 409).count();
            assertThat(winners).isEqualTo(1); assertThat(conflicts).isEqualTo(31);
            for (Reply r : replies) if (r.status == 200) holds.release(json.readTree(r.body).get("holdToken").asText());
            rounds.add(Map.of("round", round, "requests", 32, "acquired", winners, "conflicts", conflicts,
                    "latencyMs", percentiles(replies.stream().map(Reply::millis).toList())));
        }
        return Map.of("rounds", rounds, "totalRequests", 320, "acquired", 10, "conflicts", 310,
                "scope", "identical start AND end time hold creation only; not full booking oversell safety");
    }

    private Map<String, Object> rateLimit() throws Exception {
        String key = "evaluation:limiter:" + UUID.randomUUID();
        List<Integer> outcomes = parallel(100, 20, i -> {
            try { limiter.enforce(key, 20, Duration.ofSeconds(60), "evaluation"); return 200; }
            catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
        });
        assertThat(outcomes.stream().filter(v -> v == 200).count()).isEqualTo(20);
        assertThat(outcomes.stream().filter(v -> v == 429).count()).isEqualTo(80);
        redis.delete(key);
        return Map.of("attempts", 100, "workers", 20, "windowSeconds", 60, "limit", 20, "allowed", 20, "rejected", 80,
                "scope", "real Redis fixed-window primitive; not HTTP endpoint rate-limit thresholds");
    }

    private Map<String, Object> diagnostics() throws Exception {
        Tenant t = tenants.getFirst(); Instant at = Instant.parse("2031-01-01T10:00:00Z");
        List<Integer> accepted = new ArrayList<>();
        for (int round = 0; round < 10; round++) {
            Instant start = at.plusSeconds(round * 3600L), end = start.plusSeconds(1800);
            String token = holds.create(t.org.getId(), t.staff.getId(), start, end).holdToken();
            List<Boolean> outcomes = parallel(32, 32, i -> holds.consume(token, t.org.getId(), t.staff.getId(), start, end));
            accepted.add((int) outcomes.stream().filter(Boolean::booleanValue).count()); holds.release(token);
        }
        var first = holds.create(t.org.getId(), t.staff.getId(), at.plusSeconds(86400), at.plusSeconds(88200));
        boolean overlappingAccepted = false; String other = null;
        try { other = holds.create(t.org.getId(), t.staff.getId(), at.plusSeconds(87300), at.plusSeconds(89100)).holdToken(); overlappingAccepted = true; }
        catch (ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); }
        finally { holds.release(first.holdToken()); if (other != null) holds.release(other); }
        // Diagnostics deliberately report current behaviour; they do not turn a known race into a claimed guarantee.
        return Map.of("consumeReplayCallersPerRound", 32, "consumeReplaySuccessfulCallsPerRound", accepted,
                "consumeReplayRoundsWithMultipleSuccesses", accepted.stream().filter(n -> n > 1).count(),
                "overlappingDifferentStartHoldAccepted", overlappingAccepted,
                "scope", "real Redis service calls, not persisted booking writes; failures are reported, not hidden");
    }

    private Map<String, Object> events() throws Exception {
        UUID org = UUID.randomUUID(); clearInvocations(notifications);
        List<CompletableFuture<?>> sends = new ArrayList<>(); long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            UUID booking = UUID.randomUUID();
            BookingConfirmedEvent event = new BookingConfirmedEvent(booking, org, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    Instant.parse("2030-01-01T10:00:00Z"), BigDecimal.TEN, "eur");
            sends.add(kafka.send("booking.confirmed", booking.toString(), event));
        }
        CompletableFuture.allOf(sends.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        verify(notifications, timeout(30000).times(100)).onBookingConfirmed(any());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (analytics.confirmedBookingCount(org) < 100 && System.nanoTime() < deadline) Thread.sleep(50);
        assertThat(analytics.confirmedBookingCount(org)).isEqualTo(100);
        double elapsed = ms(start);
        redis.delete("analytics:confirmed-bookings:" + org);
        return Map.of("published", 100, "notificationListenerInvocations", 100, "analyticsRedisCount", 100,
                "elapsedUntilBothObservedMs", elapsed, "groups", List.of("booking-notifications", "booking-analytics"),
                "scope", "healthy single-broker synthetic event fan-out; notification logs only, not delivered emails or crash/exactly-once testing");
    }

    private Reply request(String method, String path, String token, Object body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        long start = System.nanoTime(); HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(r.statusCode(), r.body(), ms(start));
    }
    private Reply uncheckedRequest(String method, String path, String token, Object body) {
        try { return request(method, path, token, body); } catch (Exception ex) { throw new RuntimeException(ex); }
    }
    private String orgPath(Tenant t) { return "/api/v1/organizations/" + t.org.getId() + "/"; }
    private static double ms(long start) { return (System.nanoTime() - start) / 1e6; }
    private static Map<String, Double> percentiles(List<Double> values) {
        var sorted = values.stream().sorted().toList();
        return Map.of("p50", sorted.get((int) Math.ceil(sorted.size() * .5) - 1), "p95", sorted.get((int) Math.ceil(sorted.size() * .95) - 1));
    }
    private static <T> List<T> parallel(int count, int workers, IntFunction<T> action) throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(workers)) {
            CountDownLatch ready = new CountDownLatch(Math.min(count, workers)), start = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) { final int index = i; futures.add(pool.submit(() -> { ready.countDown(); start.await(); return action.apply(index); })); }
            if (!ready.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("Workers did not start");
            start.countDown(); List<T> results = new ArrayList<>();
            for (Future<T> f : futures) results.add(f.get(60, TimeUnit.SECONDS)); return results;
        }
    }
    private void write(Path output, Map<String, Object> report) throws Exception {
        json.writerWithDefaultPrettyPrinter().writeValue(output.resolve("platform.json").toFile(), report);
    }
}
