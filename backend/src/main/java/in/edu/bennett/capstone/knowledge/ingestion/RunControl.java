package in.edu.bennett.capstone.knowledge.ingestion;

/**
 * Tells a running run to stop at its next checkpoint: because it lost its lease (the heartbeat
 * found it recovered, deleted or replaced), or because the application is stopping.
 */
class RunControl {

  /** Why a run stops. */
  enum Stop {
    LOST,
    SHUTDOWN
  }

  private volatile Stop stop;

  static RunControl running() {
    return new RunControl();
  }

  void lose() {
    stop = Stop.LOST;
  }

  void shutDown() {
    if (stop == null) {
      stop = Stop.SHUTDOWN;
    }
  }

  /** Whether the run was told to stop, or its thread interrupted. */
  boolean stopRequested() {
    return stop != null || Thread.currentThread().isInterrupted();
  }

  /**
   * @throws Stopped when the run must stop
   */
  void checkpoint() {
    Stop current = stop;
    if (current != null || Thread.currentThread().isInterrupted()) {
      throw new Stopped(current == null ? Stop.SHUTDOWN : current);
    }
  }

  /** Thrown at a checkpoint of a run that must stop. */
  static final class Stopped extends RuntimeException {

    private final Stop reason;

    Stopped(Stop reason) {
      super(reason.name(), null, false, false);
      this.reason = reason;
    }

    Stop reason() {
      return reason;
    }
  }
}
