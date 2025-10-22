package edu.stanford.protege.github.cloneservice.message;

/**
 * Sealed interface representing the completion of a GitHub repository clone operation.
 *
 * <p>This sealed interface extends {@link CreateProjectHistoryEvent} and restricts
 * the possible implementations to only {@link CreateProjectHistorySucceededEvent}
 * and {@link CreateProjectHistoryFailedEvent}, providing type safety and ensuring
 * that completion events can only represent success or failure outcomes.</p>
 */
sealed interface CreateProjectHistoryCompletionEvent extends CreateProjectHistoryEvent
        permits CreateProjectHistorySucceededEvent, CreateProjectHistoryFailedEvent {}
