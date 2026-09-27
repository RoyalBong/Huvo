package com.huvo.worklife.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.huvo.audit.AuditClient;
import com.huvo.security.HuvoPrincipal;
import com.huvo.worklife.exception.BusinessRuleException;
import com.huvo.worklife.messaging.WorklifeEventPublisher;
import com.huvo.worklife.task.entity.Task;
import com.huvo.worklife.task.entity.TaskSubmission;
import com.huvo.worklife.task.repository.TaskRepository;
import com.huvo.worklife.task.repository.TaskSubmissionRepository;
import com.huvo.worklife.task.service.TaskService;

/**
 * The task path end to end through a real database.
 *
 * <p>This is the counterpart to {@code TaskTransitionsTest}, and it exists for the same reason
 * {@code AttendanceEvaluationIntegrationTest} did in Phase D. The state machine is proven in
 * isolation, but every bug found in that service lived in the seam between the rule and the
 * database - a history query that excluded today, a walk that skipped gaps - and none were
 * reachable from a test that never runs a query. Same shape of risk here: a transition that is
 * correct in isolation but written, read back, and filtered wrongly by the repository underneath
 * it.
 */
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class TaskDataPathIntegrationTest {

  private static final Long EMPLOYEE_ID = 42L;
  private static final String DEPARTMENT = "3";

  @Autowired private TaskService taskService;
  @Autowired private TaskRepository tasks;
  @Autowired private TaskSubmissionRepository submissions;

  @MockBean private WorklifeEventPublisher publisher;
  @MockBean private AuditClient audit;

  private HuvoPrincipal manager;
  private HuvoPrincipal assignee;
  private HuvoPrincipal colleague;
  private HuvoPrincipal otherDeptManager;

  @BeforeEach
  void setUp() {
    manager = new HuvoPrincipal("mgr-1", "MANAGER", List.of(3L), EMPLOYEE_ID);
    assignee = new HuvoPrincipal("u-42", "EMPLOYEE", List.of(3L), EMPLOYEE_ID);
    colleague = new HuvoPrincipal("u-77", "EMPLOYEE", List.of(3L), 77L);
    // A second manager, scoped to a different department, for the scoping tests.
    otherDeptManager = new HuvoPrincipal("mgr-2", "MANAGER", List.of(9L), EMPLOYEE_ID);
  }

  @Test
  void anAssignedTaskIsPersistedWithEveryField() {
    OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);

    Task saved =
        taskService.assign(
            manager, "Write the report", "Q3 numbers", EMPLOYEE_ID, DEPARTMENT, deadline);

    Task reloaded = tasks.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getTitle()).isEqualTo("Write the report");
    assertThat(reloaded.getDescription()).isEqualTo("Q3 numbers");
    assertThat(reloaded.getEmployeeId()).isEqualTo(EMPLOYEE_ID);
    assertThat(reloaded.getAssignedByUser()).isEqualTo("mgr-1");
    assertThat(reloaded.getDepartmentId()).isEqualTo(DEPARTMENT);
    assertThat(reloaded.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
    assertThat(reloaded.getCreatedAt()).isNotNull();
  }

  @Test
  void anOpenEndedTaskStoresANullDeadline() {
    // Not defaulted to "now plus something": a default would make every open-ended task inventably
    // overdue. TaskTransitionsTest pins that at the rule level, this pins it at the storage level.
    Task saved = taskService.assign(manager, "Whenever", null, EMPLOYEE_ID, DEPARTMENT, null);

    assertThat(tasks.findById(saved.getId()).orElseThrow().getDeadline()).isNull();
  }

  @Test
  void aDeadlineIsStoredInUtcNotInTheCallersOffset() {
    // 17:00 in Mumbai is 11:30 UTC. Storing the wall clock "17:00" and comparing it to a UTC now
    // would mark this overdue 5h30m early - the same class of bug as measuring attendance against
    // UTC in Section 5.1, and the reason Task.STORAGE_ZONE exists.
    OffsetDateTime mumbaiEvening =
        OffsetDateTime.of(2026, 9, 28, 17, 0, 0, 0, ZoneOffset.ofHoursMinutes(5, 30));

    Task saved =
        taskService.assign(manager, "IST deadline", null, EMPLOYEE_ID, DEPARTMENT, mumbaiEvening);

    assertThat(tasks.findById(saved.getId()).orElseThrow().getDeadline().toString())
        .startsWith("2026-09-28T11:30");
  }

  @Test
  void startingATaskPersistsInProgress() {
    Task task =
        taskService.assign(manager, "Write the report", null, EMPLOYEE_ID, DEPARTMENT, null);

    Task started = taskService.start(assignee, task.getId());

    assertThat(started.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    assertThat(tasks.findById(task.getId()).orElseThrow().getStatus())
        .isEqualTo(TaskStatus.IN_PROGRESS);
  }

  @Test
  void someoneElseCannotStartATask() {
    // The rule lives in the service, not the controller, precisely so that it cannot be skipped by
    // calling the endpoint directly.
    Task task =
        taskService.assign(manager, "Write the report", null, EMPLOYEE_ID, DEPARTMENT, null);

    assertThatThrownBy(() -> taskService.start(colleague, task.getId()))
        .isInstanceOf(BusinessRuleException.class);
    assertThat(tasks.findById(task.getId()).orElseThrow().getStatus())
        .isEqualTo(TaskStatus.ASSIGNED);
  }

  @Test
  void submittingRecordsTheTaskAndTheUploadedObject() {
    Task task =
        taskService.assign(manager, "Write the report", null, EMPLOYEE_ID, DEPARTMENT, null);
    taskService.start(assignee, task.getId());

    Task submitted =
        taskService.submit(
            assignee, task.getId(), "tasks/1/abc-report.pdf", "report.pdf", "application/pdf");

    assertThat(submitted.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
    assertThat(submitted.getSubmittedAt()).isNotNull();

    // The S3 key is the whole record of the upload - the bytes never passed through this service,
    // which is the entire point of the pre-signed design in Section 6.2.
    List<TaskSubmission> rows = submissions.findByTaskIdOrderBySubmittedAtAsc(task.getId());
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).getObjectKey()).isEqualTo("tasks/1/abc-report.pdf");
    assertThat(rows.get(0).getOriginalName()).isEqualTo("report.pdf");
    assertThat(rows.get(0).getContentType()).isEqualTo("application/pdf");
    assertThat(rows.get(0).getEmployeeId()).isEqualTo(EMPLOYEE_ID);
  }

  @Test
  void submittingBeforeTheDeadlineIsNotLate() {
    Task task =
        taskService.assign(
            manager,
            "Write the report",
            null,
            EMPLOYEE_ID,
            DEPARTMENT,
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
    taskService.start(assignee, task.getId());

    Task submitted = taskService.submit(assignee, task.getId(), "k", "n", "t");

    assertThat(submitted.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
    verify(publisher, never()).publishTaskSubmittedLate(any());
  }

  @Test
  void submittingAfterTheDeadlineIsLateAndPublishesIt() {
    Task task =
        taskService.assign(
            manager,
            "Late report",
            null,
            EMPLOYEE_ID,
            DEPARTMENT,
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    taskService.start(assignee, task.getId());

    Task submitted = taskService.submit(assignee, task.getId(), "k", "n", "t");

    // Still a successful submission - the work was handed in - and the manager is told it was late.
    assertThat(submitted.getStatus()).isEqualTo(TaskStatus.LATE_SUBMITTED);
    verify(publisher, times(1)).publishTaskSubmittedLate(any());
  }

  @Test
  void aTaskWithNoDeadlineIsNeverSubmittedLate() {
    Task task = taskService.assign(manager, "Whenever", null, EMPLOYEE_ID, DEPARTMENT, null);

    Task submitted = taskService.submit(assignee, task.getId(), "k", "n", "t");

    assertThat(submitted.getStatus()).isEqualTo(TaskStatus.SUBMITTED);
    verify(publisher, never()).publishTaskSubmittedLate(any());
  }

  @Test
  void submittingTwiceIsRefused() {
    Task task =
        taskService.assign(manager, "Write the report", null, EMPLOYEE_ID, DEPARTMENT, null);
    taskService.submit(assignee, task.getId(), "k1", "n", "t");

    assertThatThrownBy(() -> taskService.submit(assignee, task.getId(), "k2", "n", "t"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlyASubmittedTaskCanBeCompleted() {
    Task task =
        taskService.assign(manager, "Write the report", null, EMPLOYEE_ID, DEPARTMENT, null);

    assertThatThrownBy(() -> taskService.complete(manager, task.getId()))
        .isInstanceOf(IllegalStateException.class);

    taskService.submit(assignee, task.getId(), "k", "n", "t");
    Task completed = taskService.complete(manager, task.getId());

    assertThat(completed.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    assertThat(completed.getCompletedAt()).isNotNull();
  }

  // --- the Section 6.2 sweep ---

  @Test
  void theSweepMarksOpenTasksPastTheirDeadline() {
    Task stale =
        taskService.assign(
            manager,
            "Overdue",
            null,
            EMPLOYEE_ID,
            DEPARTMENT,
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

    int marked = taskService.markOverdue();

    assertThat(marked).isEqualTo(1);
    assertThat(tasks.findById(stale.getId()).orElseThrow().getStatus())
        .isEqualTo(TaskStatus.OVERDUE);
  }

  @Test
  void theSweepMarksATaskThatIsInProgressAndLate() {
    // Open means ASSIGNED *and* IN_PROGRESS. Filtering on ASSIGNED alone would quietly stop telling
    // managers about work that was started and then stalled - the case they most need to hear
    // about.
    Task task =
        taskService.assign(
            manager,
            "Stalled",
            null,
            EMPLOYEE_ID,
            DEPARTMENT,
            OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
    taskService.start(assignee, task.getId());

    int marked = taskService.markOverdue();

    assertThat(marked).isEqualTo(1);
    assertThat(tasks.findById(task.getId()).orElseThrow().getStatus())
        .isEqualTo(TaskStatus.OVERDUE);
  }

  @Test
  void theSweepLeavesTasksThatAreNotYetDueAlone() {
    taskService.assign(
        manager,
        "Not due",
        null,
        EMPLOYEE_ID,
        DEPARTMENT,
        OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));

    assertThat(taskService.markOverdue()).isZero();
  }

  @Test
  void theSweepNeverMarksAnOpenEndedTask() {
    // There is nothing to be late against, so an open-ended task can never be overdue.
    Task open = taskService.assign(manager, "Whenever", null, EMPLOYEE_ID, DEPARTMENT, null);

    assertThat(taskService.markOverdue()).isZero();
    assertThat(tasks.findById(open.getId()).orElseThrow().getStatus())
        .isEqualTo(TaskStatus.ASSIGNED);
  }

  @Test
  void theSweepDoesNotOverwriteASubmittedTaskThatWasNeverSwept() {
    // Submitted late but never swept, because it was handed in before the sweep ran. The sweep must
    // not walk backwards over that and replace the record of the submission.
    Task task =
        taskService.assign(
            manager,
            "Handed in",
            null,
            EMPLOYEE_ID,
            DEPARTMENT,
            OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    taskService.submit(assignee, task.getId(), "k", "n", "t");

    assertThat(taskService.markOverdue()).isZero();
    assertThat(tasks.findById(task.getId()).orElseThrow().getStatus())
        .isEqualTo(TaskStatus.LATE_SUBMITTED);
  }

  @Test
  void theSweepIsIdempotent() {
    // This is the property the whole "no distributed lock needed yet" comment rests on. A second
    // run
    // must find nothing, or every 15 minutes would send the same employee another overdue
    // notification for a task they were told about an hour ago.
    taskService.assign(
        manager,
        "Overdue",
        null,
        EMPLOYEE_ID,
        DEPARTMENT,
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

    assertThat(taskService.markOverdue()).isEqualTo(1);
    verify(publisher, times(1)).publishTaskOverdue(any());

    assertThat(taskService.markOverdue()).isZero();
    verify(publisher, times(1)).publishTaskOverdue(any());
  }

  @Test
  void theSweepNotifiesTheAssigneeOfEveryTaskItMarks() {
    taskService.assign(
        manager,
        "Mine",
        null,
        EMPLOYEE_ID,
        DEPARTMENT,
        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
    taskService.assign(
        manager, "Theirs", null, 77L, DEPARTMENT, OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

    assertThat(taskService.markOverdue()).isEqualTo(2);
    verify(publisher, times(2)).publishTaskOverdue(any());
  }

  // --- the Section 6.2 dashboard ---

  @Test
  void theDashboardIsScopedToTheManagersDepartments() {
    taskService.assign(manager, "Mine", null, EMPLOYEE_ID, "3", null);
    taskService.assign(otherDeptManager, "Theirs", null, 77L, "9", null);

    List<Task> dashboard = taskService.dashboard(manager);

    // Section 6.2 is the whole point of the scoping rule: a manager sees their team, not the
    // company.
    assertThat(dashboard).hasSize(1);
    assertThat(dashboard.get(0).getTitle()).isEqualTo("Mine");
  }

  @Test
  void aManagerWithNoDepartmentsSeesAnEmptyDashboard() {
    taskService.assign(manager, "Mine", null, EMPLOYEE_ID, "3", null);
    HuvoPrincipal unscoped = new HuvoPrincipal("mgr-9", "MANAGER", List.of(), EMPLOYEE_ID);

    // Empty rather than everyone's: the alternative default is how a department boundary leaks.
    assertThat(taskService.dashboard(unscoped)).isEmpty();
  }

  @Test
  void anAdminSeesEveryDepartment() {
    taskService.assign(manager, "Dept 3", null, EMPLOYEE_ID, "3", null);
    taskService.assign(otherDeptManager, "Dept 9", null, 77L, "9", null);
    HuvoPrincipal admin = new HuvoPrincipal("admin-1", "ADMIN", List.of(), 1L);

    assertThat(taskService.dashboard(admin)).hasSize(2);
  }

  @Test
  void anEmployeesOwnTasksAreNewestFirst() {
    taskService.assign(manager, "Older", null, EMPLOYEE_ID, DEPARTMENT, null);
    taskService.assign(manager, "Newer", null, EMPLOYEE_ID, DEPARTMENT, null);

    List<Task> mine = taskService.forEmployee(EMPLOYEE_ID);

    assertThat(mine).hasSize(2);
    assertThat(mine.get(0).getTitle()).isEqualTo("Newer");
  }

  @Test
  void assigningOutsideYourDepartmentIsRefusedAndWritesNothing() {
    assertThatThrownBy(() -> taskService.assign(manager, "Not mine to give", null, 77L, "9", null))
        .isInstanceOf(BusinessRuleException.class);

    assertThat(tasks.findAll()).isEmpty();
    // And nothing was announced, so no notification goes out for a task that does not exist.
    verify(publisher, never()).publishTaskAssigned(any());
  }
}
