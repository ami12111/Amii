package ami.ami.client.velocity;

import java.util.Arrays;

/**
 * Статистика работы модуля.
 * Точность = JUMPED / (JUMPED + MISSED + FAILED). Пропуски по шансу (SKIPPED) не считаются ошибкой.
 */
public final class VelocityStats {
    public enum Result {
        /** Прыжок выполнен и подтверждён (игрок оторвался от земли). */
        JUMPED,
        /** В нужный тик игрок уже был в воздухе. */
        MISSED,
        /** Прыжок запрошен, но игрок не оторвался от земли. */
        FAILED,
        /** Не сработало из-за шанса. */
        SKIPPED
    }

    public static final int WINDOW = 20;

    private static final Result[] ring = new Result[WINDOW];
    private static final int[] totals = new int[Result.values().length];
    private static int next = 0;
    private static int size = 0;

    private VelocityStats() {
    }

    public static synchronized void record(Result result) {
        ring[next] = result;
        next = (next + 1) % WINDOW;
        if (size < WINDOW) {
            size++;
        }
        totals[result.ordinal()]++;
    }

    public static synchronized int count(Result result) {
        return totals[result.ordinal()];
    }

    public static int attempts() {
        return count(Result.JUMPED) + count(Result.MISSED) + count(Result.FAILED);
    }

    /** Точность в процентах или -1, если данных ещё нет. */
    public static float accuracy() {
        int attempts = attempts();
        return attempts == 0 ? -1f : count(Result.JUMPED) * 100f / attempts;
    }

    /** i-й по порядку (0 = самый старый) из последних событий или null. */
    public static synchronized Result recent(int i) {
        if (i < 0 || i >= size) {
            return null;
        }
        int start = (next - size + WINDOW) % WINDOW;
        return ring[(start + i) % WINDOW];
    }

    public static synchronized void reset() {
        Arrays.fill(ring, null);
        Arrays.fill(totals, 0);
        next = 0;
        size = 0;
    }
}
