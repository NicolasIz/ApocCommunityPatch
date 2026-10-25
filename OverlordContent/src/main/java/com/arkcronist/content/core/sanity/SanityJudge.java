package com.arkcronist.content.core.sanity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Decides when something the sanity checker found wrong is acted on: when two audits in a row found
 * it wrong in exactly the same way.
 *
 * <p>One audit is a moment. A block replaced by a rollback still in progress, a world edit halfway
 * through its chunks, a piece being placed: each looks wrong once and right a little later. So a
 * finding becomes a suspect first, and is confirmed only if the next audit finds the same thing -
 * equal by {@code equals}, which for the checker's records means the same entity or row and the
 * same block found where it stands. Found differently, it becomes a new suspect; not found, or not
 * looked at (its chunk unloaded), it is forgotten. A confirmed finding is not kept: if acting on it
 * is refused - the world changed since - it starts over as a suspect.</p>
 *
 * <p>Not thread-safe: the checker keeps each judge on the database's thread.</p>
 *
 * @param <T> a finding; its identity is what {@code identity} returns, its details the rest
 */
public final class SanityJudge<T> {

    private final Function<T, Object> identity;
    private Map<Object, T> suspects = new HashMap<>();

    public SanityJudge(Function<T, Object> identity) {
        this.identity = identity;
    }

    /**
     * Takes one audit's findings.
     *
     * @return those confirmed now, to act on
     */
    public List<T> judge(Collection<T> found) {
        Map<Object, T> next = new HashMap<>();
        List<T> confirmed = new ArrayList<>();
        for (T finding : found) {
            Object id = identity.apply(finding);
            if (finding.equals(suspects.get(id))) {
                confirmed.add(finding);
            } else {
                next.put(id, finding);
            }
        }
        suspects = next;
        return confirmed;
    }

    /** How many findings wait for the next audit. */
    public int suspects() {
        return suspects.size();
    }
}
