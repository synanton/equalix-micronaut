package org.synanton.equalix.adapter.out.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.action.spi.AfterTransactionCompletionProcess;
import org.hibernate.engine.spi.ActionQueue;
import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.synanton.equalix.domain.port.out.CMSProviderPort;

/**
 * Micronaut port of the oracle's {@code TransactionAwareCmsProviderTest}. The provider under
 * test hooks the Hibernate session's action queue instead of Spring's
 * {@code TransactionSynchronizationManager}, so the test drives the
 * {@code AfterTransactionCompletionProcess} callbacks the way a transaction manager would.
 * Same behaviors: immediate outside transactions, buffered net deltas on commit, discard on
 * rollback, per-session (REQUIRES_NEW) isolation, failure tolerance.
 */
@ExtendWith(MockitoExtension.class)
class TransactionAwareCmsProviderTest {

    @Mock
    private CMSProviderPort delegate;

    @Mock
    private SessionFactory sessionFactory;

    private TransactionAwareCmsProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TransactionAwareCmsProvider(delegate, sessionFactory);
    }

    private SessionImplementor activeSession() {
        SessionImplementor session = mock(SessionImplementor.class);
        Transaction transaction = mock(Transaction.class);
        when(session.getTransaction()).thenReturn(transaction);
        when(transaction.isActive()).thenReturn(true);
        when(sessionFactory.getCurrentSession()).thenReturn(session);
        return session;
    }

    private ActionQueue actionQueueOf(SessionImplementor session) {
        ActionQueue queue = mock(ActionQueue.class);
        when(((SessionImplementor) session).getActionQueue()).thenReturn(queue);
        return queue;
    }

    @Test
    void shouldApplyImmediatelyOutsideTransaction() {
        when(sessionFactory.getCurrentSession()).thenThrow(new HibernateException("no session"));

        provider.add("tenantA", 1);

        verify(delegate).add("tenantA", 1);
    }

    @Test
    void shouldApplyImmediatelyWhenTransactionInactive() {
        SessionImplementor session = mock(SessionImplementor.class);
        Transaction transaction = mock(Transaction.class);
        when(session.getTransaction()).thenReturn(transaction);
        when(transaction.isActive()).thenReturn(false);
        when(sessionFactory.getCurrentSession()).thenReturn(session);

        provider.add("tenantA", 1);

        verify(delegate).add("tenantA", 1);
    }

    @Test
    void shouldBufferInsideTransactionAndApplyNetDeltasAfterCommit() {
        SessionImplementor session = activeSession();
        ActionQueue queue = actionQueueOf(session);
        ArgumentCaptor<AfterTransactionCompletionProcess> captor =
            ArgumentCaptor.forClass(AfterTransactionCompletionProcess.class);

        provider.add("tenantA", 1);
        provider.add("tenantA", 1);
        provider.add("tenantB", 1);
        provider.add("tenantB", -1);
        verifyNoInteractions(delegate);

        verify(queue).registerProcess(captor.capture());
        captor.getValue().doAfterTransactionCompletion(true, session);

        verify(delegate).add("tenantA", 2);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void shouldDiscardBufferedDeltasOnRollback() {
        SessionImplementor session = activeSession();
        ActionQueue queue = actionQueueOf(session);
        ArgumentCaptor<AfterTransactionCompletionProcess> captor =
            ArgumentCaptor.forClass(AfterTransactionCompletionProcess.class);

        provider.add("tenantA", 1);

        verify(queue).registerProcess(captor.capture());
        captor.getValue().doAfterTransactionCompletion(false, session);

        verifyNoInteractions(delegate);
    }

    @Test
    void shouldRegisterOneProcessPerSession() {
        SessionImplementor session = activeSession();
        ActionQueue queue = actionQueueOf(session);

        provider.add("tenantA", 1);
        provider.add("tenantB", 1);

        verify(queue).registerProcess(any(AfterTransactionCompletionProcess.class));
    }

    @Test
    void shouldKeepSeparateBuffersPerSession() {
        // A REQUIRES_NEW inner transaction runs on a separate session with its own buffer.
        SessionImplementor outer = activeSession();
        ActionQueue outerQueue = actionQueueOf(outer);
        ArgumentCaptor<AfterTransactionCompletionProcess> outerProcess =
            ArgumentCaptor.forClass(AfterTransactionCompletionProcess.class);

        provider.add("outer", 1);

        SessionImplementor inner = mock(SessionImplementor.class);
        Transaction innerTx = mock(Transaction.class);
        when(inner.getTransaction()).thenReturn(innerTx);
        when(innerTx.isActive()).thenReturn(true);
        ActionQueue innerQueue = mock(ActionQueue.class);
        when(inner.getActionQueue()).thenReturn(innerQueue);
        ArgumentCaptor<AfterTransactionCompletionProcess> innerProcess =
            ArgumentCaptor.forClass(AfterTransactionCompletionProcess.class);
        when(sessionFactory.getCurrentSession()).thenReturn(inner);

        provider.add("inner", 1);

        verify(innerQueue).registerProcess(innerProcess.capture());
        innerProcess.getValue().doAfterTransactionCompletion(true, inner);
        verify(delegate).add("inner", 1);

        verify(outerQueue).registerProcess(outerProcess.capture());
        outerProcess.getValue().doAfterTransactionCompletion(true, outer);
        verify(delegate).add("outer", 1);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void shouldNotFailCommittedTransactionWhenSketchUpdateFails() {
        SessionImplementor session = activeSession();
        ActionQueue queue = actionQueueOf(session);
        ArgumentCaptor<AfterTransactionCompletionProcess> captor =
            ArgumentCaptor.forClass(AfterTransactionCompletionProcess.class);
        doThrow(new IllegalStateException("redis down")).when(delegate).add("tenantA", 1);

        provider.add("tenantA", 1);
        provider.add("tenantB", 1);

        verify(queue).registerProcess(captor.capture());
        captor.getValue().doAfterTransactionCompletion(true, session);

        verify(delegate).add("tenantB", 1);
    }

    @Test
    void shouldDelegateReadsAndRebuild() {
        when(delegate.estimateCount("tenantA")).thenReturn(4L);
        when(delegate.totalInFlight()).thenReturn(9L);

        provider.rebuild(Map.of("tenantA", 4));

        assertThat(List.of(provider.estimateCount("tenantA"), provider.totalInFlight()))
            .containsExactly(4L, 9L);
        verify(delegate).rebuild(Map.of("tenantA", 4));
    }
}
