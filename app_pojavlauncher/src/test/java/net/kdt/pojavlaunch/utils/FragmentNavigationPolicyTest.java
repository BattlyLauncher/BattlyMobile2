package net.kdt.pojavlaunch.utils;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FragmentNavigationPolicyTest {
    @Test
    public void allowsNavigationOnlyWhileActivityCanCommitTransactions() {
        assertTrue(FragmentNavigationPolicy.canNavigate(false, false, false, true));
        assertFalse(FragmentNavigationPolicy.canNavigate(true, false, false, true));
        assertFalse(FragmentNavigationPolicy.canNavigate(false, true, false, true));
        assertFalse(FragmentNavigationPolicy.canNavigate(false, false, true, true));
        assertFalse(FragmentNavigationPolicy.canNavigate(false, false, false, false));
    }
}
