package com.opsconsole.activity;

import com.opsconsole.activity.domain.ActivityType;
import com.opsconsole.activity.repository.SystemActivityLogRepository;
import com.opsconsole.activity.service.ActivityFeedService;
import com.opsconsole.auth.domain.AppUser;
import com.opsconsole.health.domain.HealthStatus;
import com.opsconsole.health.domain.SystemHealthView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ActivityFeedServiceTest {

    @Autowired
    private ActivityFeedService feed;

    @Autowired
    private SystemActivityLogRepository repository;

    @Test
    void recordLogin_persistsEvent() {
        AppUser user = new AppUser("activity-test", "activity-test@opsconsole.local", "Activity Tester", null);

        feed.recordLogin(user);

        assertThat(feed.recent(5)).hasSize(1);
        assertThat(feed.recent(1).get(0).type()).isEqualTo(ActivityType.USER_LOGIN);
        assertThat(feed.recent(1).get(0).messageHighlight()).isEqualTo("Activity Tester");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void recordHealthStatusChange_onlyOnTransition() {
        SystemHealthView system = new SystemHealthView(
                1L, "API", "localhost", 8080, "UAT", "US-East",
                HealthStatus.DOWN, "DOWN", Instant.now(), 0, "timeout"
        );

        feed.recordHealthStatusChange(system, HealthStatus.UP, HealthStatus.DOWN);

        assertThat(feed.recent(5)).hasSize(1);
        assertThat(feed.recent(1).get(0).type()).isEqualTo(ActivityType.HEALTH_DOWN);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void recordUserRoleChanged_persistsEvent() {
        AppUser actor = new AppUser("dev-admin", "admin@opsconsole.local", "Administrator", null);
        AppUser target = new AppUser("dev-tester", "tester@opsconsole.local", "Tester", null);

        feed.recordUserRoleChanged(actor, target, "Monitoring", "Tester");

        assertThat(feed.recent(1).get(0).type()).isEqualTo(ActivityType.USER_ROLE_CHANGED);
        assertThat(feed.recent(1).get(0).messageSuffix()).contains("Tester");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void recentHealthIncidents_returnsOnlyDownEvents() {
        SystemHealthView system = new SystemHealthView(
                1L, "Billing API", "localhost", 8080, "UAT", "US-East",
                HealthStatus.DOWN, "DOWN", Instant.now(), 0, "timeout"
        );
        feed.recordHealthStatusChange(system, HealthStatus.UP, HealthStatus.DOWN);
        feed.recordLogin(new AppUser("dev-admin", "admin@opsconsole.local", "Administrator", null));

        assertThat(feed.recentHealthIncidents(5)).hasSize(1);
        assertThat(feed.recentHealthIncidents(1).get(0).type()).isEqualTo(ActivityType.HEALTH_DOWN);
        assertThat(feed.recentHealthIncidents(1).get(0).messageHighlight()).isEqualTo("Billing API");
    }
}
