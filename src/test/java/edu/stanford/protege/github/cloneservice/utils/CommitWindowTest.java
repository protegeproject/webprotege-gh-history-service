package edu.stanford.protege.github.cloneservice.utils;

import edu.stanford.protege.commitnavigator.model.CommitMetadata;
import edu.stanford.protege.commitnavigator.utils.CommitNavigator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CommitWindow} using AssertJ assertions.
 */
class CommitWindowTest {

    private UncheckedCommitNavigator nav;
    private CommitMetadata m0, m1, m2;

    @BeforeEach
    void setUp() throws Exception {
        nav = mock(UncheckedCommitNavigator.class);
        m0 = mock(CommitMetadata.class);
        m1 = mock(CommitMetadata.class);
        m2 = mock(CommitMetadata.class);

        when(m0.commitHash()).thenReturn("H0");
        when(m1.commitHash()).thenReturn("H1");
        when(m2.commitHash()).thenReturn("H2");

        when(nav.getCommitCount()).thenReturn(3);
        when(nav.getCommitAt(0)).thenReturn(m0);
        when(nav.getCommitAt(1)).thenReturn(m1);
        when(nav.getCommitAt(2)).thenReturn(m2);

        when(nav.checkoutCommitAt(anyInt())).thenAnswer(inv -> switch (inv.getArgument(0, Integer.class)) {
            case 0 -> m0;
            case 1 -> m1;
            case 2 -> m2;
            default -> throw new IllegalArgumentException("Unexpected index " + inv.getArgument(0));
        });
    }

    private CommitWindow newWindow() {
        return new CommitWindow(nav);
    }

    @Test
    void shouldInitializeWithExpectedIndicesAndHashes() {
        var cw = newWindow();

        assertThat(cw.hasBaselineCommit()).isTrue();
        assertThat(cw.hasAncestorCommit()).isTrue();
        assertThat(cw.getBaselineIndex()).isZero();
        assertThat(cw.getAncestorIndex()).isOne();
        assertThat(cw.getBaselineCommitHash()).isEqualTo("H0");
        assertThat(cw.getAncestorCommitHash()).isEqualTo("H1");
        assertThat(cw.checkoutBaseline()).isSameAs(m0);
        assertThat(cw.checkoutAncestor()).isSameAs(m1);
    }

    @Test
    void shouldIncrementAncestorOffsetAndInvalidateParentRelationship() {
        var cw = newWindow();
        cw.incrementAncestorOffset();

        assertThat(cw.getAncestorIndex()).isEqualTo(2);
        assertThat(cw.hasAncestorCommit()).isTrue();
        assertThatThrownBy(cw::validateAncestorIsParent)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Expected ancestor commit to be the parent");
    }

    @Test
    void shouldResetBaselineAndOffsetWhenSettingToLastLoadedCommit() {
        var cw = newWindow();

        // lastLoadedIndex is -1 initially → baseline should reset to 0 and offset to 1
        cw.setBaselineToLastLoadedCommit();

        assertThat(cw.getBaselineIndex()).isZero();
        assertThat(cw.getAncestorIndex()).isOne();
        assertThat(cw.hasAncestorCommit()).isTrue();
    }

    @Test
    void shouldAdvanceBaselineToAncestorAndUpdateLastLoadedIndex() {
        var cw = newWindow();

        cw.advanceBaselineToAncestor();
        assertThat(cw.getBaselineIndex()).isEqualTo(1);
        assertThat(cw.getLastLoadedIndex()).isEqualTo(1);
        assertThat(cw.getAncestorIndex()).isEqualTo(2);
        assertThat(cw.hasAncestorCommit()).isTrue();

        cw.advanceBaselineToAncestor();
        assertThat(cw.getBaselineIndex()).isEqualTo(2);
        assertThat(cw.getLastLoadedIndex()).isEqualTo(2);
        assertThat(cw.hasAncestorCommit()).isFalse();
        assertThat(cw.getBaselineCommitHash()).isEqualTo("H2");
        assertThat(cw.getAncestorCommitHash()).isEmpty();
    }

    @Test
    void shouldCheckoutExpectedCommitIndices() throws Exception {
        var cw = newWindow();

        assertThat(cw.checkoutBaseline()).isSameAs(m0);
        assertThat(cw.checkoutAncestor()).isSameAs(m1);

        cw.advanceBaselineToAncestor();
        assertThat(cw.checkoutBaseline()).isSameAs(m1);
        assertThat(cw.checkoutAncestor()).isSameAs(m2);

        verify(nav, atLeastOnce()).checkoutCommitAt(0);
        verify(nav, atLeastOnce()).checkoutCommitAt(1);
        verify(nav, atLeastOnce()).checkoutCommitAt(2);
    }
}
