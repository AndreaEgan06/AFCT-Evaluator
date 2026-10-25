package afctevaluator.mosel.logic;

public enum BooleanOperator {
    AND {
        @Override
        public boolean apply(boolean... inputs) {
            // Checks if ALL inputs are true
            return java.util.stream.IntStream.range(0, inputs.length)
                    .mapToObj(i -> inputs[i])
                    .allMatch(val -> val);
        }
    },
    OR {
        @Override
        public boolean apply(boolean... inputs) {
            // Checks if ANY input is true
            return java.util.stream.IntStream.range(0, inputs.length)
                    .mapToObj(i -> inputs[i])
                    .anyMatch(val -> val);
        }
    },
    NOT {
        @Override
        public boolean apply(boolean... inputs) {
            return !inputs[0];
        }
    },
    IMPLIES {
        @Override
        public boolean apply(boolean... inputs) {
            return !inputs[0] || inputs[1];
        }
    },
    IFF {
        @Override
        public boolean apply(boolean... inputs) {
            return inputs[0] == inputs[1];
        }
    };

    public abstract boolean apply(boolean... inputs);
}
