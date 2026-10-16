package net.kdt.pojavlaunch.runtime;

/** 준비 단계별 처리량. 전체 작업을 합친 백분율이 아니다. */
public final class GamePreparationProgress {
    public enum Stage {
        COMPONENTS,
        VERSION_LIST,
        METADATA,
        JAVA_RUNTIME,
        GAME_FILES,
        NATIVE_LIBRARIES
    }

    public enum Unit {
        ITEMS,
        BYTES,
        UNKNOWN
    }

    public final Stage stage;
    public final Unit unit;
    public final long completed;
    /** 전체 크기를 알 수 없는 단계는 -1을 사용한다. */
    public final long total;

    public GamePreparationProgress(Stage stage, Unit unit, long completed, long total) {
        this.stage = stage;
        this.unit = unit;
        this.completed = completed;
        this.total = total;
    }
}
