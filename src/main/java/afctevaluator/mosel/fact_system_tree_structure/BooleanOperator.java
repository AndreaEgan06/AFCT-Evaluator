package afctevaluator.mosel.fact_system_tree_structure;

public enum BooleanOperator {
    AND {
        @Override
        public boolean apply(boolean left, boolean right) {
            return left && right;
        }
    },
    OR {
        @Override
        public boolean apply(boolean left, boolean right) {
            return left || right;
        }
    };

    public abstract boolean apply(boolean left, boolean right);
}
