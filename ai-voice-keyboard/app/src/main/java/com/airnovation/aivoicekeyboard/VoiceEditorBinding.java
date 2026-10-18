package com.airnovation.aivoicekeyboard;

/** Immutable-value plus connection-identity binding for one IME editor lifecycle. */
final class VoiceEditorBinding {
    private long generation;
    private String pkg;
    private int fieldId, inputType;
    private Object connection;
    void bind(long generation, String pkg, int fieldId, int inputType, Object connection, boolean allowed) {
        this.generation=generation; this.pkg=allowed ? pkg : null;
        this.fieldId=fieldId; this.inputType=inputType; this.connection=allowed ? connection : null;
    }
    boolean matches(long generation, String pkg, int fieldId, int inputType, Object connection) {
        return this.pkg!=null && !this.pkg.isEmpty() && this.pkg.equals(pkg)
                && this.generation==generation && this.fieldId==fieldId && this.inputType==inputType
                && this.connection!=null && this.connection==connection;
    }
}
