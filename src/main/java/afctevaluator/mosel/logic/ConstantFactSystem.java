package afctevaluator.mosel.logic;

import afctevaluator.mosel.core.FactTrackingSystem;

/** true or false: every string, or no string. There is a single fact, the value itself. */
public final class ConstantFactSystem implements FactTrackingSystem<Boolean> {
    private final boolean value;

    public ConstantFactSystem(boolean value) {
        this.value = value;
    }

    @Override
    public Boolean initialFact() {
        return value;
    }

    @Override
    public Boolean transfer(Boolean fact, char symbol) {
        return fact;
    }

    @Override
    public boolean isAccepting(Boolean fact) {
        return fact;
    }
}
