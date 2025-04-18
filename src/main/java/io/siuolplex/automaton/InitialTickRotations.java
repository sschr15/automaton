package io.siuolplex.automaton;

public interface InitialTickRotations {
    public default float initialXRotation() {
        return 0.0f;
    }

    public default float initialYRotation() {
        return 0.0f;
    }
}
