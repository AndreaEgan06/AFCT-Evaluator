package afctevaluator.mosel.predicates.counts;

import afctevaluator.mosel.core.FactTrackingSystem;

/** isEmpty: the string is the empty string. The fact is whether nothing has been read yet. */
public final class IsEmptyFactSystem implements FactTrackingSystem<Boolean> {

    @Override
    public Boolean initialFact() {
        return true;
    }

    @Override
    public Boolean transfer(Boolean fact, char symbol) {
        return false;
    }

    @Override
    public boolean isAccepting(Boolean fact) {
        return fact;
    }
}
